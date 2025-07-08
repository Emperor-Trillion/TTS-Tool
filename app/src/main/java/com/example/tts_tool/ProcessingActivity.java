package com.example.tts_tool;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences; // Import SharedPreferences
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson; // Import Gson
import com.google.gson.reflect.TypeToken; // Import TypeToken for List deserialization

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

// Implement the new interface for the dialog fragment
public class ProcessingActivity extends AppCompatActivity implements SentenceAdapter.OnItemClickListener,
        ExitConfirmationDialogFragment.ExitConfirmationListener {

    private static final String TAG = "ProcessingActivity";
    private static final int AMPLITUDE_UPDATE_INTERVAL = 100; // Milliseconds
    private static final String PREFS_NAME = "TTSRecorderPrefs"; // SharedPreferences file name
    private static final String SESSION_KEY = "currentSession"; // Key for storing session JSON

    private TextView usernameTextView;
    private TextView fileUriTextView;
    private TextView loadedFileNameTextView;
    private TextView recordingProgressTextView;
    private ProgressBar recordingProgressBar;
    private TextView currentSelectedSentenceTextView;
    private TextView audioLevelIndicatorTextView;
    private RecyclerView sentencesRecyclerView;
    private SentenceAdapter sentenceAdapter;

    private Button btnStartProcessing;
    private Button btnDeleteFile;
    private Button btnPlayAudio;
    private Button btnNextItem;
    private Button btnSaveSession;
    private Button btnExitActivity;

    private List<SentenceItem> sentenceItems;
    private int currentSentenceIndex = -1;
    private DocumentFile workingFolderDocument;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private DocumentFile currentRecordingDocumentFile;

    private Handler audioLevelHandler;
    private Runnable audioLevelRunnable;

    private SharedPreferences sharedPreferences; // SharedPreferences instance
    private Gson gson; // Gson instance

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "RECORD_AUDIO permission granted.");
                    startRecordingInternal();
                } else {
                    Log.w(TAG, "RECORD_AUDIO permission denied.");
                    Toast.makeText(this, "Audio recording permission denied. Cannot record.", Toast.LENGTH_LONG).show();
                    isRecording = false;
                    updateButtonStates();
                }
            });

    // Inner class to hold the session state for saving/loading
    // Note: SentenceItem must be structured to allow Gson serialization (e.g., store Uri as String)
    private static class SessionState {
        String username;
        String originalInputFileUriString;
        String rootFolderUriString;
        String workingFolderUriString; // Added this field
        int currentSentenceIndex;
        List<SentenceItem> sentenceItems; // This list will contain SentenceItem objects

        public SessionState(String username, String originalInputFileUriString, String rootFolderUriString, String workingFolderUriString, int currentSentenceIndex, List<SentenceItem> sentenceItems) {
            this.username = username;
            this.originalInputFileUriString = originalInputFileUriString;
            this.rootFolderUriString = rootFolderUriString;
            this.workingFolderUriString = workingFolderUriString; // Initialize new field
            this.currentSentenceIndex = currentSentenceIndex;
            this.sentenceItems = sentenceItems;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Initialize SharedPreferences and Gson
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();

        // Initialize UI components
        usernameTextView = findViewById(R.id.username_display_text_view);
        fileUriTextView = findViewById(R.id.file_uri_display_text_view);
        loadedFileNameTextView = findViewById(R.id.loaded_file_name_text_view);
        recordingProgressTextView = findViewById(R.id.recording_progress_text_view);
        recordingProgressBar = findViewById(R.id.recording_progress_bar);
        currentSelectedSentenceTextView = findViewById(R.id.current_selected_sentence_text_view);
        audioLevelIndicatorTextView = findViewById(R.id.audio_level_indicator_text_view);
        sentencesRecyclerView = findViewById(R.id.sentences_recycler_view);
        btnStartProcessing = findViewById(R.id.btn_start_processing);
        btnDeleteFile = findViewById(R.id.btn_delete_file);
        btnPlayAudio = findViewById(R.id.btn_play_audio);
        btnNextItem = findViewById(R.id.btn_next_item);
        btnSaveSession = findViewById(R.id.btn_save_session);
        btnExitActivity = findViewById(R.id.btn_exit_activity);

        sentencesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Handler and Runnable for audio level updates
        audioLevelHandler = new Handler(Looper.getMainLooper());
        audioLevelRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording && mediaRecorder != null) {
                    int amplitude = mediaRecorder.getMaxAmplitude();
                    updateAudioLevelIndicator(amplitude);
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                } else if (isPlaying) {
                    audioLevelIndicatorTextView.setText("Playing audio...");
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                }
            }
        };

        // Check if this is a new session initiated from a previous activity
        String usernameFromIntent = getIntent().getStringExtra("username");
        Uri originalInputFileUriFromIntent = getIntent().getData();
        String rootFolderUriStringFromIntent = getIntent().getStringExtra("root_folder_uri");

        if (usernameFromIntent != null && originalInputFileUriFromIntent != null && rootFolderUriStringFromIntent != null) {
            // This is a NEW session
            setupNewSession(usernameFromIntent, originalInputFileUriFromIntent, rootFolderUriStringFromIntent);
        } else {
            // This is NOT a new session, attempt to load a saved one
            if (!loadSessionState()) { // loadSessionState returns true if successful
                Toast.makeText(this, "No saved session found. Please start a new one from the previous screen.", Toast.LENGTH_LONG).show();
                handleInitializationError(); // Disable buttons if no session to load
            }
        }

        // Set up button click listeners
        btnStartProcessing.setOnClickListener(v -> toggleRecording());
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnPlayAudio.setOnClickListener(v -> handlePlayAudio());
        btnNextItem.setOnClickListener(v -> handleNextSentence());
        btnSaveSession.setOnClickListener(v -> handleSaveSession());
        btnExitActivity.setOnClickListener(v -> showExitConfirmationDialog());

        // Handle Android Back button press using OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        });

        updateProgressBar();
        updateButtonStates();
        audioLevelIndicatorTextView.setText("Ready to record.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioLevelHandler != null) {
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
        }
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    mediaRecorder.stop();
                }
                mediaRecorder.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "RuntimeException on MediaRecorder release in onDestroy: " + e.getMessage());
            } finally {
                mediaRecorder = null;
            }
        }
        if (mediaPlayer != null) {
            stopPlayingAudio();
        }
    }

    /**
     * Sets up a new recording session.
     * @param username The username for the session.
     * @param originalInputFileUri The URI of the original text file.
     * @param rootFolderUriString The string representation of the root folder URI.
     */
    private void setupNewSession(String username, Uri originalInputFileUri, String rootFolderUriString) {
        usernameTextView.setText("Speaker: " + username);
        Uri rootFolderUri = Uri.parse(rootFolderUriString);
        DocumentFile rootDocument = DocumentFile.fromTreeUri(this, rootFolderUri);

        if (rootDocument != null && rootDocument.isDirectory()) {
            String originalFileName = (DocumentFile.fromSingleUri(this, originalInputFileUri) != null && DocumentFile.fromSingleUri(this, originalInputFileUri).getName() != null)
                    ? DocumentFile.fromSingleUri(this, originalInputFileUri).getName()
                    : "unknown_file";
            String sanitizedFileName = originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_");

            String folderName = "TTS_" + sanitizedFileName + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            Log.d(TAG, "Attempting to create working folder: " + folderName);

            workingFolderDocument = rootDocument.createDirectory(folderName);

            if (workingFolderDocument != null) {
                Toast.makeText(this, "Working folder created: " + folderName, Toast.LENGTH_SHORT).show();
                fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());

                copyInputFileToWorkingFolder(originalInputFileUri, workingFolderDocument);
                loadedFileNameTextView.setText("Loaded File: " + (DocumentFile.fromSingleUri(this, originalInputFileUri) != null ? DocumentFile.fromSingleUri(this, originalInputFileUri).getName() : "N/A"));
                readFileContentAndPopulateList(originalInputFileUri);

                // Save the initial state of this new session
                saveSessionState();

            } else {
                Log.e(TAG, "Failed to create working folder in " + rootFolderUri.toString());
                Toast.makeText(this, "Failed to create working folder. Check permissions.", Toast.LENGTH_LONG).show();
                handleInitializationError();
            }
        } else {
            Log.e(TAG, "Root folder document is invalid or not a directory: " + rootFolderUriString);
            Toast.makeText(this, "Invalid root folder. Please re-select a folder in previous screen.", Toast.LENGTH_LONG).show();
            handleInitializationError();
        }
    }

    /**
     * Saves the current session state to SharedPreferences.
     */
    private void saveSessionState() {
        if (usernameTextView.getText().toString().equals("Speaker: N/A") || workingFolderDocument == null || sentenceItems == null || sentenceItems.isEmpty()) {
            Log.w(TAG, "Cannot save session: essential data is missing or empty.");
            // Toast.makeText(this, "Cannot save session: No active session data.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert Uris to Strings for serialization
        String currentUsername = usernameTextView.getText().toString().replace("Speaker: ", "");
        String currentOriginalInputFileUriString = getIntent().getData() != null ? getIntent().getData().toString() : null;
        String currentRootFolderUriString = getIntent().getStringExtra("root_folder_uri");
        String currentWorkingFolderUriString = workingFolderDocument.getUri().toString(); // Get working folder URI

        // Create a new list of SentenceItem that has Uri converted to String for serialization
        List<SentenceItem> serializableSentenceItems = new ArrayList<>();
        for (SentenceItem item : sentenceItems) {
            // Create a new SentenceItem or a copy that stores Uri as String
            SentenceItem serializableItem = new SentenceItem(item.getIndex(), item.getText());
            if (item.getRecordedFileName() != null && item.getRecordedFileUri() != null) {
                serializableItem.setRecordedFile(item.getRecordedFileName(), item.getRecordedFileUri());
            }
            serializableSentenceItems.add(serializableItem);
        }

        SessionState sessionState = new SessionState(
                currentUsername,
                currentOriginalInputFileUriString,
                currentRootFolderUriString,
                currentWorkingFolderUriString, // Pass working folder URI
                currentSentenceIndex,
                serializableSentenceItems
        );

        String json = gson.toJson(sessionState);
        sharedPreferences.edit().putString(SESSION_KEY, json).apply();
        Log.d(TAG, "Session state saved successfully.");
        // Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show(); // Only show toast on explicit save button click
    }

    /**
     * Loads the saved session state from SharedPreferences.
     * @return true if a session was successfully loaded, false otherwise.
     */
    private boolean loadSessionState() {
        String json = sharedPreferences.getString(SESSION_KEY, null);
        if (json == null) {
            Log.d(TAG, "No saved session found in SharedPreferences.");
            return false;
        }

        try {
            Type type = new TypeToken<SessionState>() {}.getType();
            SessionState loadedState = gson.fromJson(json, type);

            if (loadedState != null) {
                usernameTextView.setText("Speaker: " + loadedState.username);
                fileUriTextView.setText("Working Folder: " + (loadedState.workingFolderUriString != null ? DocumentFile.fromTreeUri(this, Uri.parse(loadedState.workingFolderUriString)).getName() : "N/A"));
                loadedFileNameTextView.setText("Loaded File: " + (loadedState.originalInputFileUriString != null ? DocumentFile.fromSingleUri(this, Uri.parse(loadedState.originalInputFileUriString)).getName() : "N/A"));

                // Reconstruct workingFolderDocument using the saved workingFolderUriString
                if (loadedState.workingFolderUriString != null) {
                    workingFolderDocument = DocumentFile.fromTreeUri(this, Uri.parse(loadedState.workingFolderUriString));
                    if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
                        Log.e(TAG, "Loaded working folder does not exist or is not a directory: " + loadedState.workingFolderUriString);
                        Toast.makeText(this, "Saved session's working folder not found or accessible.", Toast.LENGTH_LONG).show();
                        return false; // Cannot load session without a valid working folder
                    }
                } else {
                    Log.e(TAG, "Loaded session state missing working folder URI.");
                    return false;
                }

                sentenceItems = loadedState.sentenceItems;
                // Ensure Uri objects are reconstructed for SentenceItems if needed by other parts of the app
                // The SentenceItem class's getRecordedFileUri() method should handle parsing from string.
                // So, no need to explicitly convert here, as long as SentenceItem is properly designed.
                sentenceAdapter = new SentenceAdapter(sentenceItems, this);
                sentencesRecyclerView.setAdapter(sentenceAdapter);

                currentSentenceIndex = loadedState.currentSentenceIndex;
                if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                    selectSentence(currentSentenceIndex); // Re-select the last active sentence
                } else if (!sentenceItems.isEmpty()) {
                    selectSentence(0); // Select first if index is invalid
                }

                Toast.makeText(this, "Session loaded successfully!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Session state loaded successfully.");
                updateProgressBar();
                updateButtonStates();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading session state from JSON: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to load saved session.", Toast.LENGTH_LONG).show();
        }
        return false;
    }


    /**
     * Displays the exit confirmation dialog.
     */
    private void showExitConfirmationDialog() {
        if (isRecording || isPlaying) {
            Toast.makeText(this, "Cannot exit while recording or playing is in progress. Please stop first.", Toast.LENGTH_LONG).show();
            return;
        }
        ExitConfirmationDialogFragment dialog = new ExitConfirmationDialogFragment();
        dialog.show(getSupportFragmentManager(), "ExitConfirmationDialog");
    }

    // --- ExitConfirmationListener Interface Implementations ---
    @Override
    public void onSaveAndExit() {
        // This button now explicitly saves the session state before exiting
        saveSessionState();
        Toast.makeText(this, "Session saved and exiting!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User chose Save & Exit.");
        finish(); // Close the activity
    }

    @Override
    public void onContinueRecording() {
        Toast.makeText(this, "Continuing recording session.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User chose Continue Recording.");
        // Dialog is dismissed automatically by the fragment
    }

    @Override
    public void onExitWithoutSaving() {
        // This option means just exit, don't perform an explicit save operation here.
        // The recordings are already persistent.
        Toast.makeText(this, "Exiting without saving session.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User chose Exit Without Saving.");
        // Optionally, clear the saved session state if "Exit Without Saving" means discarding progress.
        // For now, we'll leave the last saved state as is, as individual files are saved.
        // sharedPreferences.edit().remove(SESSION_KEY).apply(); // Uncomment to clear saved session on "Exit Without Saving"
        finish(); // Close the activity
    }
    // --- End of ExitConfirmationListener Interface Implementations ---


    /**
     * Handles errors during activity initialization by disabling buttons and showing default text.
     */
    private void handleInitializationError() {
        fileUriTextView.setText("Working Folder: Error");
        loadedFileNameTextView.setText("Loaded File: Error");
        currentSelectedSentenceTextView.setText("Initialization Error. Please restart.");
        audioLevelIndicatorTextView.setText("Error.");
        btnStartProcessing.setEnabled(false);
        btnDeleteFile.setEnabled(false);
        btnPlayAudio.setEnabled(false);
        btnNextItem.setEnabled(false);
        btnSaveSession.setEnabled(false); // Disable Save/Exit on initialization error
        btnExitActivity.setEnabled(false); // Disable Save/Exit on initialization error
        if (sentencesRecyclerView.getAdapter() == null) {
            sentencesRecyclerView.setAdapter(new SentenceAdapter(new ArrayList<>(), this));
        }
        updateProgressBar();
    }

    /**
     * Copies the selected input text file into the newly generated working folder.
     * @param sourceUri The URI of the original text file.
     * @param targetFolder The DocumentFile representing the newly created working folder.
     */
    private void copyInputFileToWorkingFolder(Uri sourceUri, DocumentFile targetFolder) {
        DocumentFile sourceFile = DocumentFile.fromSingleUri(this, sourceUri);
        if (sourceFile == null || !sourceFile.isFile()) {
            Toast.makeText(this, "Source file not found or is not a file.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Source file invalid: " + sourceUri);
            return;
        }

        try {
            DocumentFile newFileInWorkingFolder = targetFolder.createFile(sourceFile.getType(), sourceFile.getName());

            if (newFileInWorkingFolder != null) {
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = getContentResolver().openOutputStream(newFileInWorkingFolder.getUri())) {

                    if (in == null || out == null) {
                        Log.e(TAG, "Failed to open streams for file copy.");
                        Toast.makeText(this, "Failed to open streams for file copy.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Toast.makeText(this, "Input file copied to working folder!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Input file copied to: " + newFileInWorkingFolder.getUri().toString());
                }
            } else {
                Toast.makeText(this, "Failed to create copy of input file.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to create copy of input file: " + sourceFile.getName());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying input file: " + e.getMessage(), e);
            Toast.makeText(this, "Error copying input file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during file copy: " + e.getMessage(), e);
            Toast.makeText(this, "Permission denied to copy input file.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Reads the content of the selected text file and populates the sentences list.
     * @param uri The URI of the text file.
     */
    private void readFileContentAndPopulateList(Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append(" ");
            }
            String fullText = stringBuilder.toString().trim();
            parseTextIntoSentences(fullText);
            Toast.makeText(this, "File loaded and sentences parsed!", Toast.LENGTH_SHORT).show();

            if (!sentenceItems.isEmpty()) {
                selectSentence(0);
            }
            updateProgressBar();
        } catch (Exception e) {
            Log.e(TAG, "Error reading file content: " + e.getMessage(), e);
            currentSelectedSentenceTextView.setText("Error reading file: " + e.getMessage());
            Toast.makeText(this, "Failed to read file content.", Toast.LENGTH_LONG).show();
            updateProgressBar();
        }
    }

    /**
     * Parses the given text into individual sentences using a simple regex.
     * @param fullText The entire text content from the file.
     */
    private void parseTextIntoSentences(String fullText) {
        sentenceItems = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9])|\\n");
        String[] sentences = pattern.split(fullText);

        int index = 0;
        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (!trimmedSentence.isEmpty()) {
                sentenceItems.add(new SentenceItem(index++, trimmedSentence));
            }
        }
        sentenceAdapter = new SentenceAdapter(sentenceItems, this);
        sentencesRecyclerView.setAdapter(sentenceAdapter);
    }

    /**
     * Handles item clicks in the RecyclerView (sentence selection).
     * @param position The position of the clicked sentence.
     */
    @Override
    public void onItemClick(int position) {
        if (!isRecording && !isPlaying) {
            selectSentence(position);
        } else if (isRecording) {
            Toast.makeText(this, "Cannot select sentence while recording is in progress.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot select sentence while audio is playing.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Selects a sentence by its index, updates UI, and scrolls to it.
     * @param index The index of the sentence to select.
     */
    private void selectSentence(int index) {
        if (index >= 0 && index < sentenceItems.size()) {
            if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                sentenceItems.get(currentSentenceIndex).setSelected(false);
            }

            currentSentenceIndex = index;
            sentenceItems.get(currentSentenceIndex).setSelected(true);
            sentenceAdapter.setSelectedPosition(currentSentenceIndex);

            currentSelectedSentenceTextView.setText(sentenceItems.get(currentSentenceIndex).getText());

            sentencesRecyclerView.scrollToPosition(currentSentenceIndex);

            updateButtonStates();
        } else {
            currentSelectedSentenceTextView.setText("No sentence selected.");
            currentSentenceIndex = -1;
            updateButtonStates();
        }
    }

    /**
     * Updates the enabled/disabled state of action buttons and the start button text/color.
     */
    private void updateButtonStates() {
        boolean isSentenceSelected = currentSentenceIndex != -1;
        SentenceItem selectedItem = isSentenceSelected ? sentenceItems.get(currentSentenceIndex) : null;
        boolean hasRecordedAudio = selectedItem != null && selectedItem.getRecordedFileName() != null;

        if (isRecording) {
            btnStartProcessing.setText("Stop Recording");
            btnStartProcessing.setBackgroundTintList(getResources().getColorStateList(R.color.custom_red, getTheme()));
            btnStartProcessing.setEnabled(true);
            btnDeleteFile.setEnabled(false);
            btnPlayAudio.setEnabled(false);
            btnNextItem.setEnabled(false);
            btnSaveSession.setEnabled(false);
            btnExitActivity.setEnabled(false);
        } else if (isPlaying) {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(getResources().getColorStateList(R.color.custom_green, getTheme()));
            btnStartProcessing.setEnabled(false);
            btnDeleteFile.setEnabled(false);
            btnPlayAudio.setEnabled(true);
            btnNextItem.setEnabled(false);
            btnSaveSession.setEnabled(false);
            btnExitActivity.setEnabled(false);
        }
        else {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(getResources().getColorStateList(R.color.custom_green, getTheme()));
            btnStartProcessing.setEnabled(isSentenceSelected);
            btnDeleteFile.setEnabled(hasRecordedAudio);
            btnPlayAudio.setEnabled(hasRecordedAudio);
            btnNextItem.setEnabled(isSentenceSelected && currentSentenceIndex < sentenceItems.size() - 1);
            btnSaveSession.setEnabled(true);
            btnExitActivity.setEnabled(true);
        }
    }

    /**
     * Toggles the recording state (Start -> Stop, Stop -> Start).
     */
    private void toggleRecording() {
        if (isPlaying) {
            Toast.makeText(this, "Please stop audio playback before recording.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startRecordingInternal();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        } else {
            stopRecordingInternal();
        }
    }

    /**
     * Internal method to handle starting the recording.
     * This method assumes RECORD_AUDIO permission has been granted.
     */
    private void startRecordingInternal() {
        if (currentSentenceIndex == -1) {
            Toast.makeText(this, "Please select a sentence to record.", Toast.LENGTH_SHORT).show();
            return;
        }

        SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);

        if (selectedItem.getRecordedFileName() != null && selectedItem.getRecordedFileName().isEmpty()) {
            Toast.makeText(this, "This sentence has already been recorded. Delete it first to re-record.", Toast.LENGTH_LONG).show();
            return;
        }

        if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
            Toast.makeText(this, "Working folder not accessible. Cannot record.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Working folder is null, does not exist, or is not a directory.");
            return;
        }

        try {
            String recordedFileName = "sentence_" + (selectedItem.getIndex() + 1) + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp3";
            currentRecordingDocumentFile = workingFolderDocument.createFile("audio/mpeg", recordedFileName);

            if (currentRecordingDocumentFile == null) {
                Toast.makeText(this, "Failed to create audio file for recording.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to create audio file in working folder.");
                return;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(getContentResolver().openFileDescriptor(currentRecordingDocumentFile.getUri(), "w").getFileDescriptor());

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            updateButtonStates();
            Toast.makeText(this, "Recording started for: \"" + selectedItem.getText() + "\"", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording to: " + currentRecordingDocumentFile.getUri().toString());

            audioLevelHandler.post(audioLevelRunnable);

        } catch (IOException e) {
            Log.e(TAG, "IOException during MediaRecorder setup/start: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isRecording = false;
            updateButtonStates();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Recording Error.");
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException during MediaRecorder setup/start: " + e.getMessage(), e);
            Toast.makeText(this, "Runtime error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isRecording = false;
            updateButtonStates();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Recording Error.");
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        }
    }

    /**
     * Internal method to handle stopping the recording.
     */
    private void stopRecordingInternal() {
        if (mediaRecorder == null) {
            Log.w(TAG, "MediaRecorder is null, cannot stop recording.");
            Toast.makeText(this, "Recording not active.", Toast.LENGTH_SHORT).show();
            isRecording = false;
            updateButtonStates();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Ready to record.");
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            isRecording = false;
            updateButtonStates();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Ready to record.");

            if (currentSentenceIndex != -1 && currentRecordingDocumentFile != null) {
                SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
                selectedItem.setRecordedFile(currentRecordingDocumentFile.getName(), currentRecordingDocumentFile.getUri());
                sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                Toast.makeText(this, "Recording stopped and saved.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Recording saved to: " + currentRecordingDocumentFile.getUri().toString());
                updateProgressBar();
                handleNextSentence();
            } else {
                Toast.makeText(this, "Recording stopped, but no sentence selected or file created.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Recording stopped but currentSentenceIndex or currentRecordingDocumentFile was null.");
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException during MediaRecorder stop/release: " + e.getMessage(), e);
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Recording Error.");
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        } finally {
            currentRecordingDocumentFile = null;
        }
    }

    /**
     * Updates the audio level indicator TextView during recording.
     * @param amplitude The current max amplitude from MediaRecorder.
     */
    private void updateAudioLevelIndicator(int amplitude) {
        if (amplitude > 1000) {
            audioLevelIndicatorTextView.setText("● Recording (High Sound)");
            audioLevelIndicatorTextView.setTextColor(getResources().getColor(R.color.custom_red, getTheme()));
        } else if (amplitude > 100) {
            audioLevelIndicatorTextView.setText("● Recording (Medium Sound)");
            audioLevelIndicatorTextView.setTextColor(getResources().getColor(R.color.custom_orange, getTheme()));
        } else {
            audioLevelIndicatorTextView.setText("○ Recording (Low/No Sound)");
            audioLevelIndicatorTextView.setTextColor(getResources().getColor(R.color.black, getTheme()));
        }
    }

    /**
     * Handles the "Delete" button click.
     * Clears the recorded audio path for the currently selected sentence and deletes the file.
     */
    private void handleDeleteRecording() {
        if (isRecording || isPlaying) {
            Toast.makeText(this, "Cannot delete while recording or playing is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex != -1) {
            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
            if (selectedItem.getRecordedFileUri() != null) {
                try {
                    DocumentFile fileToDelete = DocumentFile.fromSingleUri(this, selectedItem.getRecordedFileUri());
                    if (fileToDelete != null && fileToDelete.exists()) {
                        if (fileToDelete.delete()) {
                            selectedItem.clearRecordedFile();
                            sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                            Toast.makeText(this, "Recording deleted for sentence " + (currentSentenceIndex + 1), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Deleted file: " + fileToDelete.getName());
                            updateProgressBar();
                        } else {
                            Toast.makeText(this, "Failed to delete recording.", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "DocumentFile.delete() returned false for " + fileToDelete.getName());
                        }
                    } else {
                        Toast.makeText(this, "Recorded file not found.", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Attempted to delete non-existent file: " + selectedItem.getRecordedFileUri());
                        selectedItem.clearRecordedFile();
                        sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                        updateProgressBar();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied to delete file: " + e.getMessage(), e);
                    Toast.makeText(this, "Permission denied to delete recording.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting recording: " + e.getMessage(), e);
                    Toast.makeText(this, "An error occurred deleting recording.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "No recording to delete for this sentence.", Toast.LENGTH_SHORT).show();
            }
        }
        updateButtonStates();
    }

    /**
     * Handles the "Play" button click.
     * Starts or stops playing the audio associated with the currently selected sentence.
     */
    private void handlePlayAudio() {
        if (isRecording) {
            Toast.makeText(this, "Cannot play while recording is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPlaying) {
            stopPlayingAudio();
            Toast.makeText(this, "Playback stopped.", Toast.LENGTH_SHORT).show();
        } else {
            if (currentSentenceIndex != -1) {
                SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
                if (selectedItem.getRecordedFileUri() != null) {
                    try {
                        mediaPlayer = new MediaPlayer();
                        mediaPlayer.setDataSource(this, selectedItem.getRecordedFileUri());
                        mediaPlayer.prepareAsync();

                        mediaPlayer.setOnPreparedListener(mp -> {
                            mp.start();
                            isPlaying = true;
                            updateButtonStates();
                            audioLevelHandler.post(audioLevelRunnable);
                            Toast.makeText(this, "Playing: " + selectedItem.getRecordedFileName(), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Playing audio from: " + selectedItem.getRecordedFileUri().toString());
                        });

                        mediaPlayer.setOnCompletionListener(mp -> {
                            stopPlayingAudio();
                            Toast.makeText(this, "Playback finished.", Toast.LENGTH_SHORT).show();
                        });

                        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                            Toast.makeText(this, "Error playing audio.", Toast.LENGTH_SHORT).show();
                            stopPlayingAudio();
                            return true;
                        });

                    } catch (IOException e) {
                        Log.e(TAG, "IOException during MediaPlayer setup: " + e.getMessage(), e);
                        Toast.makeText(this, "Error setting up playback: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        stopPlayingAudio();
                    } catch (RuntimeException e) {
                        Log.e(TAG, "RuntimeException during MediaPlayer setup: " + e.getMessage(), e);
                        Toast.makeText(this, "Runtime error during playback setup: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        stopPlayingAudio();
                    }
                } else {
                    Toast.makeText(this, "No audio recorded for this sentence.", Toast.LENGTH_SHORT).show();
                }
            }
        }
        updateButtonStates();
    }

    /**
     * Stops audio playback and releases MediaPlayer resources.
     */
    private void stopPlayingAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException during MediaPlayer stop/release: " + e.getMessage());
            } finally {
                mediaPlayer = null;
                isPlaying = false;
                updateButtonStates();
                audioLevelHandler.removeCallbacks(audioLevelRunnable);
                audioLevelIndicatorTextView.setText("Ready to record.");
            }
        }
    }

    /**
     * Handles the "Next" button click.
     * Moves to and selects the next sentence in the list.
     */
    private void handleNextSentence() {
        if (isRecording || isPlaying) {
            Toast.makeText(this, "Cannot move to next sentence while recording or playing is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex < sentenceItems.size() - 1) {
            selectSentence(currentSentenceIndex + 1);
        } else {
            Toast.makeText(this, "End of sentences.", Toast.LENGTH_SHORT).show();
        }
        updateButtonStates();
    }

    /**
     * Handles the "Save" button click.
     * This method now explicitly saves the current session state.
     */
    private void handleSaveSession() {
        if (isRecording || isPlaying) {
            Toast.makeText(this, "Cannot save while recording or playing is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveSessionState();
        Toast.makeText(this, "Session state saved!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Handles the "Exit" button click.
     * This method now shows the confirmation dialog instead of directly exiting.
     */
    private void handleExitActivity() {
        showExitConfirmationDialog();
    }

    /**
     * Updates the progress bar and text to show recorded sentences vs total sentences.
     */
    private void updateProgressBar() {
        if (sentenceItems == null || sentenceItems.isEmpty()) {
            recordingProgressBar.setProgress(0);
            recordingProgressTextView.setText("Progress: 0/0 Recorded");
            return;
        }

        int totalSentences = sentenceItems.size();
        int recordedSentences = 0;
        for (SentenceItem item : sentenceItems) {
            if (item.getRecordedFileName() != null) {
                recordedSentences++;
            }
        }

        recordingProgressTextView.setText(String.format(Locale.getDefault(), "Progress: %d/%d Recorded", recordedSentences, totalSentences));

        int progress = (totalSentences > 0) ? (recordedSentences * 100 / totalSentences) : 0;
        recordingProgressBar.setProgress(progress);
    }
}
