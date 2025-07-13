// app/src/main/java/com/example/tts_tool/ProcessingActivity.java
package com.example.tts_tool;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog; // For AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Firebase imports
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions; // For merging data
import com.google.firebase.firestore.Query; // For ordering results

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections; // For sorting
import java.util.Comparator; // For sorting
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID; // For generating unique session IDs
import java.util.regex.Pattern;

// Import the AudioRecorderManager
import com.example.tts_tool.AudioRecorderManager; // Explicit import

public class ProcessingActivity extends AppCompatActivity implements SentenceAdapter.OnItemClickListener,
        ExitConfirmationDialogFragment.ExitConfirmationListener, // Ensure this interface is correctly implemented
        LoadSessionDialogFragment.OnSessionSelectedListener,
        AudioRecorderManager.RecordingCallback { // Implement the new callback interface

    private static final String TAG = "ProcessingActivity";
    // AMPLITUDE_UPDATE_INTERVAL is no longer directly used for audio level, but can be kept for other UI updates if needed
    private static final int AMPLITUDE_UPDATE_INTERVAL = 100;
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri"; // Added for consistency
    private static final String FIRESTORE_COLLECTION_SESSIONS = "sessions";

    // Keys for saving/restoring instance state
    private static final String STATE_CURRENT_SESSION_ID = "currentSessionId";
    private static final String STATE_USERNAME = "username";
    private static final String STATE_WORKING_FOLDER_URI = "workingFolderUri";
    private static final String STATE_COPIED_INPUT_FILE_URI = "copiedInputFileUri";
    private static final String STATE_CURRENT_SENTENCE_INDEX = "currentSentenceIndex";
    private static final String STATE_SENTENCE_ITEMS_JSON = "sentenceItemsJson";
    private static final String STATE_IS_RECORDING = "isRecording";
    private static final String STATE_IS_PLAYING = "isPlaying";


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
    private Button btnDeleteFile; // For deleting individual audio files
    private Button btnPlayAudio;
    private Button btnNextItem;
    private Button btnSaveSession;
    private Button btnLoadSession;
    private Button btnExitActivity;

    private List<SentenceItem> sentenceItems;
    private int currentSentenceIndex = -1;
    private DocumentFile workingFolderDocument;

    // Replaced MediaRecorder with AudioRecorderManager
    private AudioRecorderManager audioRecorderManager;
    private MediaPlayer mediaPlayer;
    private boolean isRecording = false; // This will be updated by AudioRecorderManager callbacks
    private boolean isPlaying = false;
    // currentRecordingDocumentFile is now managed internally by AudioRecorderManager,
    // but we might need a temporary reference for file creation before passing to manager.
    private DocumentFile tempRecordingDocumentFile;


    private Handler audioLevelHandler;
    private Runnable audioLevelRunnable; // Simplified for playback/status, not amplitude

    private SharedPreferences sharedPreferences;
    private Gson gson;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;
    private String currentSessionId; // This will hold the ID of the currently active session
    private Uri copiedInputFileUri; // Stores the URI of the input text file copied into the working folder

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "RECORD_AUDIO permission granted.");
                    toggleRecording(); // Re-attempt recording after permission
                } else {
                    Log.w(TAG, "RECORD_AUDIO permission denied.");
                    Toast.makeText(this, "Audio recording permission denied. Cannot record.", Toast.LENGTH_LONG).show();
                    isRecording = false; // Ensure state is correct
                    updateButtonStates();
                }
            });

    // --- START: SentenceItem and SessionState Definitions for Firestore Compatibility ---
    public static class SentenceItem {
        private int index;
        private String text;
        private String recordedFileName;
        private String recordedFileUriString;
        private transient Uri recordedFileUri; // Marked transient to avoid Gson serialization issues

        public SentenceItem() {}

        public SentenceItem(int index, String text) {
            this.index = index;
            this.text = text;
            this.recordedFileName = null;
            this.recordedFileUriString = null;
            this.recordedFileUri = null;
        }

        public int getIndex() { return index; }
        public String getText() { return text; }
        public String getRecordedFileName() { return recordedFileName; }
        public String getRecordedFileUriString() { return recordedFileUriString; }

        public void setIndex(int index) { this.index = index; }
        public void setText(String text) { this.text = text; }
        public void setRecordedFileName(String recordedFileName) { this.recordedFileName = recordedFileName; }
        public void setRecordedFileUriString(String recordedFileUriString) {
            this.recordedFileUriString = recordedFileUriString;
            this.recordedFileUri = (recordedFileUriString != null) ? Uri.parse(recordedFileUriString) : null;
        }

        public void setRecordedFile(String fileName, Uri fileUri) {
            this.recordedFileName = fileName;
            this.recordedFileUri = fileUri;
            this.recordedFileUriString = (fileUri != null) ? fileUri.toString() : null;
        }

        public Uri getRecordedFileUri() {
            if (this.recordedFileUri == null && this.recordedFileUriString != null) {
                this.recordedFileUri = Uri.parse(this.recordedFileUriString);
            }
            return recordedFileUri;
        }

        public void clearRecordedFile() {
            this.recordedFileName = null;
            this.recordedFileUriString = null;
            this.recordedFileUri = null;
        }

        private boolean selected;
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }

    public static class SessionState {
        String sessionId;
        String username;
        String originalInputFileUriString; // This will now store the URI of the *copied* file in the working folder
        String rootFolderUriString;
        String workingFolderUriString;
        int currentSentenceIndex;
        List<SentenceItem> sentenceItems;
        long lastModified;

        public SessionState() {}

        public SessionState(String sessionId, String username, String originalInputFileUriString, String rootFolderUriString, String workingFolderUriString, int currentSentenceIndex, List<SentenceItem> sentenceItems, long lastModified) {
            this.sessionId = sessionId;
            this.username = username;
            this.originalInputFileUriString = originalInputFileUriString;
            this.rootFolderUriString = rootFolderUriString;
            this.workingFolderUriString = workingFolderUriString;
            this.currentSentenceIndex = currentSentenceIndex;
            this.sentenceItems = sentenceItems;
            this.lastModified = lastModified;
        }

        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
        public String getOriginalInputFileUriString() { return originalInputFileUriString; }
        public String getRootFolderUriString() { return rootFolderUriString; }
        public String getWorkingFolderUriString() { return workingFolderUriString; }
        public int getCurrentSentenceIndex() { return currentSentenceIndex; }
        public List<SentenceItem> getSentenceItems() { return sentenceItems; }
        public long getLastModified() { return lastModified; }

        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public void setUsername(String username) { this.username = username; }
        public void setOriginalInputFileUriString(String originalInputFileUriString) { this.originalInputFileUriString = originalInputFileUriString; }
        public void setRootFolderUriString(String rootFolderUriString) { this.rootFolderUriString = rootFolderUriString; }
        public void setWorkingFolderUriString(String workingFolderUriString) { this.workingFolderUriString = workingFolderUriString; }
        public void setCurrentSentenceIndex(int currentSentenceIndex) { this.currentSentenceIndex = currentSentenceIndex; }
        public void setSentenceItems(List<SentenceItem> sentenceItems) { this.sentenceItems = sentenceItems; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    }
    // --- END: SentenceItem and SessionState Definitions ---


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();

        // Initialize sentenceItems and sentenceAdapter here to prevent NullPointerException
        sentenceItems = new ArrayList<>();
        sentenceAdapter = new SentenceAdapter(sentenceItems, this);

        // Initialize AudioRecorderManager here, passing 'this' as context and callback
        audioRecorderManager = new AudioRecorderManager(this, this);


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
        btnLoadSession = findViewById(R.id.btn_load_session);
        btnExitActivity = findViewById(R.id.btn_exit_activity);

        sentencesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sentencesRecyclerView.setAdapter(sentenceAdapter); // Set the adapter early

        // Simplified audioLevelRunnable as MediaRecorder.getMaxAmplitude() is no longer used
        audioLevelHandler = new Handler(Looper.getMainLooper());
        audioLevelRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    audioLevelIndicatorTextView.setText("● Recording...");
                    audioLevelIndicatorTextView.setTextColor(ContextCompat.getColor(ProcessingActivity.this, R.color.custom_red));
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                } else if (isPlaying) {
                    audioLevelIndicatorTextView.setText("Playing audio...");
                    audioLevelIndicatorTextView.setTextColor(ContextCompat.getColor(ProcessingActivity.this, R.color.black));
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                }
            }
        };

        btnStartProcessing.setOnClickListener(v -> toggleRecording());
        btnPlayAudio.setOnClickListener(v -> {
            if (isPlaying) {
                stopPlayingAudio();
            } else {
                handlePlayAudio();
            }
        });
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnNextItem.setOnClickListener(v -> handleNextSentence());
        btnSaveSession.setOnClickListener(v -> showSaveSessionDialog());
        btnLoadSession.setOnClickListener(v -> {
            LoadSessionDialogFragment loadDialog = LoadSessionDialogFragment.newInstance(currentUserId); // Use newInstance
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });
        btnExitActivity.setOnClickListener(v -> showExitConfirmationDialog()); // Call the private method

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog(); // Call the private method
            }
        });

        // --- START: State Restoration Logic ---
        if (savedInstanceState != null) {
            Log.d(TAG, "onCreate: Restoring state from savedInstanceState.");
            currentSessionId = savedInstanceState.getString(STATE_CURRENT_SESSION_ID);
            String savedUsername = savedInstanceState.getString(STATE_USERNAME);
            String savedWorkingFolderUriString = savedInstanceState.getString(STATE_WORKING_FOLDER_URI);
            String savedCopiedInputFileUriString = savedInstanceState.getString(STATE_COPIED_INPUT_FILE_URI);
            currentSentenceIndex = savedInstanceState.getInt(STATE_CURRENT_SENTENCE_INDEX, -1);
            String sentenceItemsJson = savedInstanceState.getString(STATE_SENTENCE_ITEMS_JSON);
            isRecording = savedInstanceState.getBoolean(STATE_IS_RECORDING, false);
            isPlaying = savedInstanceState.getBoolean(STATE_IS_PLAYING, false);

            usernameTextView.setText("Speaker: " + savedUsername);

            if (savedWorkingFolderUriString != null) {
                workingFolderDocument = DocumentFile.fromTreeUri(this, Uri.parse(savedWorkingFolderUriString));
                if (workingFolderDocument != null && workingFolderDocument.exists() && workingFolderDocument.isDirectory()) {
                    fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());
                    Log.d(TAG, "Restored working folder: " + workingFolderDocument.getUri().toString());
                } else {
                    Log.e(TAG, "Restored working folder URI invalid or inaccessible: " + savedWorkingFolderUriString);
                    Toast.makeText(this, "Failed to restore working folder. Please restart session.", Toast.LENGTH_LONG).show();
                    handleInitializationError("Failed to restore working folder from saved state.");
                    return; // Exit if critical component fails to restore
                }
            } else {
                Log.e(TAG, "No working folder URI found in saved instance state.");
                Toast.makeText(this, "Failed to restore session (no working folder data).", Toast.LENGTH_LONG).show();
                handleInitializationError("No working folder URI in saved state.");
                return; // Exit if critical component fails to restore
            }

            if (savedCopiedInputFileUriString != null) {
                copiedInputFileUri = Uri.parse(savedCopiedInputFileUriString);
                DocumentFile restoredInputFile = DocumentFile.fromSingleUri(this, copiedInputFileUri);
                if (restoredInputFile != null && restoredInputFile.exists() && restoredInputFile.isFile()) {
                    readFileContentAndPopulateList(copiedInputFileUri); // Re-populate sentences
                    loadedFileNameTextView.setText("Loaded File: " + restoredInputFile.getName());
                    Log.d(TAG, "Restored copied input file: " + copiedInputFileUri.toString());
                } else {
                    Log.e(TAG, "Restored copied input file URI invalid or inaccessible: " + savedCopiedInputFileUriString);
                    Toast.makeText(this, "Failed to restore input file. Session loaded partially.", Toast.LENGTH_LONG).show();
                    loadedFileNameTextView.setText("Loaded File: Not Restored");
                    // Continue without input file content but log the error
                }
            } else {
                Log.w(TAG, "No copied input file URI found in saved instance state.");
                loadedFileNameTextView.setText("Loaded File: Not Available");
            }


            if (sentenceItemsJson != null) {
                Type type = new TypeToken<List<SentenceItem>>(){}.getType();
                List<SentenceItem> restoredSentenceItems = gson.fromJson(sentenceItemsJson, type);
                if (restoredSentenceItems != null) {
                    sentenceItems.clear();
                    sentenceItems.addAll(restoredSentenceItems);
                    // Re-associate recordedFileUri for each item as it's transient
                    for (SentenceItem item : sentenceItems) {
                        if (item.getRecordedFileUriString() != null) {
                            // Corrected line: Use setRecordedFileUriString
                            item.setRecordedFileUriString(item.getRecordedFileUriString());
                        }
                    }
                    sentenceAdapter.updateData(sentenceItems);
                    Log.d(TAG, "Restored " + sentenceItems.size() + " sentence items.");
                } else {
                    Log.e(TAG, "Failed to deserialize sentence items from JSON.");
                    Toast.makeText(this, "Failed to restore sentence data.", Toast.LENGTH_LONG).show();
                    sentenceItems.clear();
                    sentenceAdapter.updateData(sentenceItems);
                }
            } else {
                Log.w(TAG, "No sentence items JSON found in saved instance state.");
                // If no sentences, the list will remain empty from initial ArrayList creation
            }

            // After all data is restored, select the sentence and update UI
            if (!sentenceItems.isEmpty() && currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                selectSentence(currentSentenceIndex);
            } else if (!sentenceItems.isEmpty()) {
                selectSentence(0); // Select first if index invalid but list not empty
            } else {
                currentSelectedSentenceTextView.setText("No sentences loaded.");
            }

            Log.d(TAG, "State restoration complete.");
            Toast.makeText(this, "Session restored successfully!", Toast.LENGTH_SHORT).show();

            // Re-authenticate Firebase after restoring state to ensure currentUserId is set
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                currentUserId = user.getUid();
                                Log.d(TAG, "Firebase re-authenticated on restore. UID: " + currentUserId);
                                updateButtonStates(); // Update buttons after auth
                            } else {
                                Log.e(TAG, "Firebase re-authentication successful, but user is null.");
                                Toast.makeText(this, "Authentication issue on restore.", Toast.LENGTH_SHORT).show();
                                handleInitializationError("Firebase re-authentication failed (user null).");
                            }
                        } else {
                            Log.e(TAG, "Firebase re-authentication failed on restore.", task.getException());
                            Toast.makeText(this, "Authentication failed on restore.", Toast.LENGTH_SHORT).show();
                            handleInitializationError("Firebase re-authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        }
                    });

        } else {
            // --- END: State Restoration Logic ---
            // Original initialization logic for first launch
            Log.d(TAG, "onCreate: No savedInstanceState. Initializing session from intent.");
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "signInAnonymously:success");
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    currentUserId = user.getUid();
                                    Log.d(TAG, "Authenticated with UID: " + currentUserId);
                                    initializeSessionBasedOnIntent();
                                } else {
                                    Log.e(TAG, "signInAnonymously:success but current user is null.");
                                    Toast.makeText(ProcessingActivity.this, "Authentication failed: User null.", Toast.LENGTH_LONG).show();
                                    handleInitializationError("Firebase authentication successful but user is null.");
                                }
                            } else {
                                Log.e(TAG, "signInAnonymously:failure", task.getException());
                                Toast.makeText(ProcessingActivity.this, "Authentication failed. Cannot save/load sessions.", Toast.LENGTH_LONG).show();
                                handleInitializationError("Firebase authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                            }
                        }
                    });
        }

        updateProgressBar();
        updateButtonStates();
        audioLevelIndicatorTextView.setText("Ready to record.");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: Saving current session state.");

        // Save primitive types
        outState.putString(STATE_CURRENT_SESSION_ID, currentSessionId);
        outState.putString(STATE_USERNAME, usernameTextView.getText().toString().replace("Speaker: ", ""));
        outState.putInt(STATE_CURRENT_SENTENCE_INDEX, currentSentenceIndex);
        outState.putBoolean(STATE_IS_RECORDING, isRecording);
        outState.putBoolean(STATE_IS_PLAYING, isPlaying);

        // Save URIs as strings
        if (workingFolderDocument != null && workingFolderDocument.getUri() != null) {
            outState.putString(STATE_WORKING_FOLDER_URI, workingFolderDocument.getUri().toString());
        }
        if (copiedInputFileUri != null) {
            outState.putString(STATE_COPIED_INPUT_FILE_URI, copiedInputFileUri.toString());
        }

        // Save sentenceItems list as JSON string
        if (sentenceItems != null && !sentenceItems.isEmpty()) {
            String sentenceItemsJson = gson.toJson(sentenceItems);
            outState.putString(STATE_SENTENCE_ITEMS_JSON, sentenceItemsJson);
            Log.d(TAG, "Saved " + sentenceItems.size() + " sentence items to JSON.");
        } else {
            outState.putString(STATE_SENTENCE_ITEMS_JSON, null);
            Log.d(TAG, "No sentence items to save.");
        }
    }


    /**
     * Called after Firebase authentication to determine whether to setup a new session or load an existing one.
     * This method is now only called if `savedInstanceState` is null (i.e., not a rotation/recreation).
     */
    private void initializeSessionBasedOnIntent() {
        String usernameFromIntent = getIntent().getStringExtra("username");
        Uri originalInputFileUriFromIntent = getIntent().getData(); // This is the *external* URI passed from MainActivity
        String rootFolderUriStringFromIntent = getIntent().getStringExtra("root_folder_uri");
        String sessionIdFromIntent = getIntent().getStringExtra("session_id"); // This would be null for new sessions

        Log.d(TAG, "initializeSessionBasedOnIntent: sessionIdFromIntent=" + sessionIdFromIntent +
                ", usernameFromIntent=" + usernameFromIntent + ", originalInputFileUriFromIntent=" + originalInputFileUriFromIntent +
                ", rootFolderUriStringFromIntent=" + rootFolderUriStringFromIntent);

        if (sessionIdFromIntent != null && currentUserId != null) {
            Log.d(TAG, "initializeSessionBasedOnIntent: Attempting to load saved session with ID: " + sessionIdFromIntent + " for user: " + currentUserId);
            currentSessionId = sessionIdFromIntent;

            // Fetch the specific session state from Firestore
            String appId = getApplicationContext().getPackageName();
            db.collection("artifacts")
                    .document(appId)
                    .collection("users")
                    .document(currentUserId)
                    .collection(FIRESTORE_COLLECTION_SESSIONS)
                    .document(sessionIdFromIntent)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            ProcessingActivity.SessionState loadedSessionState = task.getResult().toObject(ProcessingActivity.SessionState.class);
                            if (loadedSessionState != null) {
                                Log.d(TAG, "initializeSessionBasedOnIntent: Session data fetched successfully for ID: " + sessionIdFromIntent);
                                onSessionSelected(loadedSessionState); // Pass the loaded SessionState to the handler
                            } else {
                                Log.e(TAG, "initializeSessionBasedOnIntent: Failed to deserialize session data for ID: " + sessionIdFromIntent);
                                Toast.makeText(this, "Failed to load session data. It might be corrupted.", Toast.LENGTH_LONG).show();
                                handleInitializationError("Failed to deserialize session state from Firestore.");
                            }
                        } else {
                            Log.e(TAG, "initializeSessionBasedOnIntent: Error fetching session " + sessionIdFromIntent + ": " + task.getException());
                            Toast.makeText(this, "Failed to load session: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                            handleInitializationError("Error fetching session from Firestore: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        }
                    });

        } else if (usernameFromIntent != null && originalInputFileUriFromIntent != null && rootFolderUriStringFromIntent != null) {
            Log.d(TAG, "initializeSessionBasedOnIntent: Starting new session from intent.");
            currentSessionId = UUID.randomUUID().toString();
            setupNewSession(usernameFromIntent, originalInputFileUriFromIntent, rootFolderUriStringFromIntent);
        } else {
            Log.e(TAG, "initializeSessionBasedOnIntent: No session data provided in intent. Cannot proceed.");
            Toast.makeText(this, "No session data found. Please start a new session or load a saved one from the previous screen.", Toast.LENGTH_LONG).show();
            handleInitializationError("Missing intent data for new session (username, original file URI, or root folder URI is null).");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioLevelHandler != null) {
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
        }
        // Ensure AudioRecorderManager is shut down
        if (audioRecorderManager != null) {
            audioRecorderManager.shutdown();
        }
        if (mediaPlayer != null) {
            stopPlayingAudio();
        }
    }

    private void setupNewSession(String username, Uri originalInputFileUri, String rootFolderUriString) {
        Log.d(TAG, "setupNewSession: Setting up new session for username: " + username);
        usernameTextView.setText("Speaker: " + username);
        Uri rootFolderUri = Uri.parse(rootFolderUriString);
        DocumentFile rootDocument = DocumentFile.fromTreeUri(this, rootFolderUri);

        if (rootDocument != null && rootDocument.isDirectory()) {
            String originalFileName = (DocumentFile.fromSingleUri(this, originalInputFileUri) != null && DocumentFile.fromSingleUri(this, originalInputFileUri).getName() != null)
                    ? DocumentFile.fromSingleUri(this, originalInputFileUri).getName()
                    : "unknown_file";
            // Sanitize the original file name to be used as ScriptID
            String scriptId = originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                    .replaceAll("[^a-zA-Z0-9_\\-]", ""); // Remove non-alphanumeric chars, keep underscore/hyphen

            // Sanitize speaker name for folder
            String sanitizedSpeakerName = username.replaceAll("[^a-zA-Z0-9_\\-]", "");

            // Get current date for session date
            String sessionDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

            // Construct the folder name: {SpeakerName}_{SessionDate}_{OptionalScriptID}
            String folderName = String.format(Locale.US, "%s_%s_%s",
                    sanitizedSpeakerName,
                    sessionDate,
                    scriptId);

            Log.d(TAG, "setupNewSession: Attempting to create working folder: " + folderName);

            workingFolderDocument = rootDocument.createDirectory(folderName);

            if (workingFolderDocument != null) {
                //Toast.makeText(this, "Working folder created: " + folderName, Toast.LENGTH_SHORT).show();
                fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());
                Log.d(TAG, "setupNewSession: Working folder created at: " + workingFolderDocument.getUri().toString());

                // Copy the original input file into the newly created working folder
                DocumentFile newFileInWorkingFolder = copyInputFileToWorkingFolder(originalInputFileUri, workingFolderDocument);

                if (newFileInWorkingFolder != null) {
                    copiedInputFileUri = newFileInWorkingFolder.getUri(); // Store the URI of the *copied* file
                    loadedFileNameTextView.setText("Loaded File: " + newFileInWorkingFolder.getName());
                    readFileContentAndPopulateList(copiedInputFileUri); // Read from the copied file
                } else {
                    Log.e(TAG, "setupNewSession: Failed to copy input file to working folder.");
                    Toast.makeText(this, "Failed to copy input file. Session may be incomplete.", Toast.LENGTH_LONG).show();
                    loadedFileNameTextView.setText("Loaded File: Copy Failed");
                    // Proceed without sentences if copy fails, or handleInitializationError if critical
                    sentenceItems.clear(); // Ensure list is initialized even if file copy fails
                    sentenceAdapter.updateData(sentenceItems); // Update RecyclerView
                }

            } else {
                Log.e(TAG, "setupNewSession: Failed to create working folder in " + rootFolderUri.toString());
                Toast.makeText(this, "Failed to create working folder. Check permissions.", Toast.LENGTH_LONG).show();
                handleInitializationError("Failed to create working folder: " + rootFolderUri.toString());
            }
        } else {
            Log.e(TAG, "setupNewSession: Root folder document is invalid or not a directory: " + rootFolderUriString);
            Toast.makeText(this, "Invalid root folder. Please re-select a folder in previous screen.", Toast.LENGTH_LONG).show();
            handleInitializationError("Invalid root folder document: " + rootFolderUriString);
        }
    }

    /**
     * Shows a dialog to prompt the user for a session name before saving.
     */
    private void showSaveSessionDialog() {
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated. Cannot save session.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sentenceItems == null || sentenceItems.isEmpty()) {
            Toast.makeText(this, "No sentences to save. Load a file first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (workingFolderDocument == null) {
            Toast.makeText(this, "Working folder not set. Cannot save session.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Session");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_save_session, null, false);
        final EditText input = viewInflated.findViewById(R.id.input_session_name);
        builder.setView(viewInflated);

        String proposedSessionName = null;

        // 1. Try to use the working folder name as the primary default
        if (workingFolderDocument != null && workingFolderDocument.getName() != null && !workingFolderDocument.getName().trim().isEmpty()) {
            proposedSessionName = workingFolderDocument.getName();
        }

        // 2. If working folder name is not available, try to use currentSessionId if it's a user-defined name
        if (proposedSessionName == null && currentSessionId != null && !currentSessionId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            proposedSessionName = currentSessionId;
        }

        // 3. Fallback to a timestamp if no other suitable name is found
        if (proposedSessionName == null) {
            proposedSessionName = "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        }

        input.setText(proposedSessionName);


        builder.setPositiveButton("Save", (dialog, which) -> {
            String sessionName = input.getText().toString().trim();
            if (sessionName.isEmpty()) {
                Toast.makeText(this, "Session name cannot be empty. Please try again.", Toast.LENGTH_SHORT).show();
                sessionName = "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                Toast.makeText(this, "Using auto-generated name: " + sessionName, Toast.LENGTH_SHORT).show();
            }
            currentSessionId = sessionName;
            saveSessionState(currentSessionId);
            Toast.makeText(this, "Session state saved as '" + sessionName + "'!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Saves the current session state to Firestore with a given session ID.
     * @param sessionId The ID to use for the Firestore document.
     */
    private void saveSessionState(String sessionId) {
        if (currentUserId == null) {
            Log.e(TAG, "User not authenticated. Cannot save session.");
            Toast.makeText(this, "Error: Not authenticated to save session.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (usernameTextView.getText().toString().equals("Speaker: N/A") || workingFolderDocument == null || sentenceItems == null || sentenceItems.isEmpty()) {
            Log.w(TAG, "Cannot save session: essential data is missing or empty.");
            Toast.makeText(this, "Cannot save session: Missing data.", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = usernameTextView.getText().toString().replace("Speaker: ", "");
        // IMPORTANT: Use copiedInputFileUri for saving, as this is the accessible file within the working folder
        String originalInputFileUriString = copiedInputFileUri != null ? copiedInputFileUri.toString() : null;
        // The rootFolderUriString is needed for new session setup, but not directly used in saved state restoration
        // It's part of the initial intent, so we can retrieve it from there if needed for full SessionState object
        String rootFolderUriString = getIntent().getStringExtra("root_folder_uri");
        String workingFolderUriString = workingFolderDocument.getUri().toString();
        long lastModified = System.currentTimeMillis();

        List<SentenceItem> serializableSentenceItems = new ArrayList<>();
        for (SentenceItem item : sentenceItems) {
            SentenceItem serializableItem = new SentenceItem(item.getIndex(), item.getText());
            if (item.getRecordedFileName() != null && item.getRecordedFileUri() != null) {
                serializableItem.setRecordedFile(item.getRecordedFileName(), item.getRecordedFileUri());
            }
            serializableSentenceItems.add(serializableItem);
        }

        SessionState sessionState = new SessionState(
                sessionId,
                username,
                originalInputFileUriString,
                rootFolderUriString, // Store this for completeness, though not always used in load
                workingFolderUriString,
                currentSentenceIndex,
                serializableSentenceItems,
                lastModified
        );

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("sessionId", sessionState.getSessionId());
        sessionData.put("username", sessionState.getUsername());
        sessionData.put("originalInputFileUriString", sessionState.getOriginalInputFileUriString());
        sessionData.put("rootFolderUriString", sessionState.getRootFolderUriString());
        sessionData.put("workingFolderUriString", sessionState.getWorkingFolderUriString());
        sessionData.put("currentSentenceIndex", sessionState.getCurrentSentenceIndex());
        sessionData.put("lastModified", sessionState.getLastModified());

        List<Map<String, Object>> serializableSentenceItemsMap = new ArrayList<>();
        for (SentenceItem item : serializableSentenceItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("index", item.getIndex());
            itemMap.put("text", item.getText());
            itemMap.put("recordedFileName", item.getRecordedFileName());
            itemMap.put("recordedFileUriString", item.getRecordedFileUri() != null ? item.getRecordedFileUri().toString() : null);
            itemMap.put("selected", item.isSelected());
            serializableSentenceItemsMap.add(itemMap);
        }
        sessionData.put("sentenceItems", serializableSentenceItemsMap);

        String appId = getApplicationContext().getPackageName();
        DocumentReference sessionDocRef = db.collection("artifacts")
                .document(appId)
                .collection("users")
                .document(currentUserId)
                .collection(FIRESTORE_COLLECTION_SESSIONS)
                .document(sessionId);

        Log.d(TAG, "Saving session to Firestore at path: " + sessionDocRef.getPath());
        sessionDocRef.set(sessionData, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Session state saved to Firestore successfully: " + sessionId);
                    } else {
                        Log.e(TAG, "Error saving session state to Firestore: " + task.getException());
                        Toast.makeText(this, "Error saving session: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Loads a specific session state from Firestore.
     * @param sessionState The SessionState object loaded from Firestore.
     */
    @Override
    public void onSessionSelected(ProcessingActivity.SessionState sessionState) {
        if (sessionState == null || sessionState.getSessionId() == null) {
            Toast.makeText(this, "Failed to load session: Invalid session data received.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onSessionSelected: Invalid sessionState received from LoadSessionDialogFragment.");
            handleInitializationError("Invalid session state received during load."); // Add specific reason
            return;
        }

        Log.d(TAG, "onSessionSelected: Attempting to load session with ID: " + sessionState.getSessionId());

        usernameTextView.setText("Speaker: " + sessionState.getUsername());

        // Reconstruct workingFolderDocument
        if (sessionState.getWorkingFolderUriString() != null) {
            Uri workingFolderUri = Uri.parse(sessionState.getWorkingFolderUriString());
            workingFolderDocument = DocumentFile.fromTreeUri(this, workingFolderUri);
            if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
                Log.e(TAG, "onSessionSelected: Loaded working folder does not exist or is not a directory: " + sessionState.getWorkingFolderUriString());
                Toast.makeText(this, "Saved session's working folder not found or accessible. Please re-select folder.", Toast.LENGTH_LONG).show();
                handleInitializationError("Loaded working folder not found or accessible: " + sessionState.getWorkingFolderUriString());
                return;
            }
            fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());
            Log.d(TAG, "onSessionSelected: Working folder set to: " + workingFolderDocument.getUri().toString());
        } else {
            Log.e(TAG, "onSessionSelected: Loaded session state missing working folder URI.");
            Toast.makeText(this, "Saved session data incomplete: Missing working folder.", Toast.LENGTH_LONG).show();
            handleInitializationError("Saved session data incomplete: Missing working folder URI.");
            return;
        }

        // Initialize sentenceItems and sentenceAdapter before attempting to read file or update adapter
        // These are already initialized in onCreate, but ensure they are cleared for a new load
        sentenceItems.clear();
        sentenceAdapter.updateData(sentenceItems);


        // Now, read the content from the *copied* input file within the working folder
        if (sessionState.getOriginalInputFileUriString() != null) {
            Uri copiedInputFileUriFromSession = Uri.parse(sessionState.getOriginalInputFileUriString());
            DocumentFile copiedInputFile = DocumentFile.fromSingleUri(this, copiedInputFileUriFromSession);

            if (copiedInputFile != null && copiedInputFile.exists() && copiedInputFile.isFile()) {
                Log.d(TAG, "onSessionSelected: Attempting to read copied input file from working folder: " + copiedInputFile.getUri().toString());
                readFileContentAndPopulateList(copiedInputFile.getUri()); // Read from the copied file
                loadedFileNameTextView.setText("Loaded File: " + copiedInputFile.getName());
                this.copiedInputFileUri = copiedInputFile.getUri(); // Update the member variable
            } else {
                Log.e(TAG, "onSessionSelected: Copied input file not found or inaccessible in working folder: " + sessionState.getOriginalInputFileUriString());
                Toast.makeText(this, "Original text file not found in working folder. Session loaded partially.", Toast.LENGTH_LONG).show();
                sentenceItems.clear(); // Clear sentences if copied file cannot be found
                sentenceAdapter.updateData(sentenceItems); // Update RecyclerView with empty list
                loadedFileNameTextView.setText("Loaded File: Not Found in Folder");
                this.copiedInputFileUri = null; // Clear the member variable
            }
        } else {
            Log.e(TAG, "onSessionSelected: Loaded session state missing copied input file URI.");
            Toast.makeText(this, "Saved session data incomplete: Missing original input file URI. Continuing without text content.", Toast.LENGTH_LONG).show();
            sentenceItems.clear(); // Clear sentences if URI is missing
            sentenceAdapter.updateData(sentenceItems); // Update RecyclerView with empty list
            loadedFileNameTextView.setText("Loaded File: Missing URI");
            this.copiedInputFileUri = null; // Clear the member variable
        }

        // After parsing sentences (or clearing them due to error), update their recorded file Uris
        if (sentenceItems != null && sessionState.getSentenceItems() != null) {
            for (int i = 0; i < sentenceItems.size() && i < sessionState.getSentenceItems().size(); i++) {
                SentenceItem currentItem = sentenceItems.get(i);
                SentenceItem loadedItem = sessionState.getSentenceItems().get(i);
                if (loadedItem.getRecordedFileName() != null && loadedItem.getRecordedFileUriString() != null) {
                    // Re-check if the recorded file actually exists on device within the working folder
                    Uri recordedFileUri = Uri.parse(loadedItem.getRecordedFileUriString());
                    DocumentFile recordedDocument = DocumentFile.fromSingleUri(this, recordedFileUri);
                    if (recordedDocument != null && recordedDocument.exists()) {
                        currentItem.setRecordedFile(loadedItem.getRecordedFileName(), recordedFileUri);
                    } else {
                        Log.w(TAG, "Recorded file not found on device for sentence " + i + ": " + recordedFileUri.toString());
                        currentItem.clearRecordedFile(); // Clear if file doesn't exist
                    }
                }
            }
            // This line is safe because sentenceAdapter is guaranteed to be non-null here.
            sentenceAdapter.notifyDataSetChanged();
            Log.d(TAG, "onSessionSelected: Recorded file URIs updated from loaded state.");
        } else {
            Log.d(TAG, "onSessionSelected: No sentence items to update or loaded state is null.");
        }


        currentSentenceIndex = sessionState.getCurrentSentenceIndex();
        if (sentenceItems != null && !sentenceItems.isEmpty() && currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
            selectSentence(currentSentenceIndex);
            Log.d(TAG, "onSessionSelected: Selected sentence index: " + currentSentenceIndex);
        } else if (sentenceItems != null && !sentenceItems.isEmpty()) {
            selectSentence(0);
            Log.d(TAG, "onSessionSelected: Current index invalid or no index provided, selecting first sentence (0).");
        } else {
            Log.d(TAG, "onSessionSelected: No sentences to select.");
            currentSelectedSentenceTextView.setText("No sentences loaded.");
        }

        currentSessionId = sessionState.getSessionId();
        Toast.makeText(this, "Session loaded successfully!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onSessionSelected: Session state loaded successfully: " + currentSessionId);
        updateProgressBar();
        updateButtonStates();
    }

    // --- NEW: Implementations for ExitConfirmationDialogFragment.ExitConfirmationListener ---
    @Override
    public void onSaveAndExit() {
        // User chose to save and exit
        Log.d(TAG, "onSaveAndExit: User chose to save and exit.");
        String sessionIdToSave;
        if (workingFolderDocument != null && workingFolderDocument.getName() != null && !workingFolderDocument.getName().trim().isEmpty()) {
            sessionIdToSave = workingFolderDocument.getName();
            Log.d(TAG, "onSaveAndExit: Using working folder name for session ID: " + sessionIdToSave);
        } else {
            sessionIdToSave = "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            Log.w(TAG, "onSaveAndExit: Working folder name not available, using timestamp for session ID: " + sessionIdToSave);
        }
        saveSessionState(sessionIdToSave);
        finish(); // Exit the activity
    }

    @Override
    public void onContinueRecording() {
        // User chose to continue, dialog is dismissed automatically
        Log.d(TAG, "onContinueRecording: User chose to continue recording.");
        Toast.makeText(this, "Continuing session.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onExitWithoutSaving() {
        // User chose to exit without saving
        Log.d(TAG, "onExitWithoutSaving: User chose to exit without saving.");

        boolean hasActiveRecordings = false;
        if (sentenceItems != null) {
            for (SentenceItem item : sentenceItems) {
                if (item.getRecordedFileUri() != null) {
                    hasActiveRecordings = true;
                    break;
                }
            }
        }

        if (hasActiveRecordings) {
            Log.d(TAG, "onExitWithoutSaving: Cannot delete working folder. Active recorded sentences exist.");
            Toast.makeText(this, "Cannot exit without saving: Some sentences have recordings. Please save or delete them.", Toast.LENGTH_LONG).show();
        } else {
            if (workingFolderDocument != null && workingFolderDocument.exists()) {
                Log.d(TAG, "onExitWithoutSaving: No active recordings found. Attempting to delete working folder: " + workingFolderDocument.getUri().toString());
                try {
                    if (workingFolderDocument.delete()) {
                        Toast.makeText(this, "Working folder deleted.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Working folder and its contents successfully deleted.");
                    } else {
                        Toast.makeText(this, "Failed to delete working folder.", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Failed to delete working folder: DocumentFile.delete() returned false.");
                    }
                } catch (SecurityException e) {
                    Toast.makeText(this, "Permission denied to delete working folder.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "SecurityException while deleting working folder: " + e.getMessage());
                } catch (Exception e) {
                    Toast.makeText(this, "Error deleting working folder.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Generic Exception while deleting working folder: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "onExitWithoutSaving: No working folder to delete or it does not exist.");
            }
            Toast.makeText(this, "Exiting without saving session state.", Toast.LENGTH_SHORT).show();
            finish(); // Exit the activity
        }
    }
    // --- END: Implementations for ExitConfirmationDialogFragment.ExitConfirmationListener ---

    // --- NEW: Private method to show the exit confirmation dialog ---
    private void showExitConfirmationDialog() {
        Log.d(TAG, "showExitConfirmationDialog: Displaying exit confirmation dialog.");
        ExitConfirmationDialogFragment dialog = new ExitConfirmationDialogFragment();
        dialog.show(getSupportFragmentManager(), "ExitConfirmationDialog");
    }
    // --- END: Private method to show the exit confirmation dialog ---


    private void handleInitializationError(String reason) {
        Log.e(TAG, "Initialization Error: " + reason + " UI disabled. Finishing activity.");
        fileUriTextView.setText("Working Folder: Error");
        loadedFileNameTextView.setText("Loaded File: Error");
        currentSelectedSentenceTextView.setText("Initialization Error. Please restart.");
        audioLevelIndicatorTextView.setText("Error.");
        btnStartProcessing.setEnabled(false);
        btnDeleteFile.setEnabled(false);
        btnPlayAudio.setEnabled(false);
        btnNextItem.setEnabled(false);
        btnSaveSession.setEnabled(false);
        btnLoadSession.setEnabled(false);
        btnExitActivity.setEnabled(true); // Always allow exiting

        // Ensure sentenceItems and sentenceAdapter are initialized and cleared on error
        if (sentenceItems == null) {
            sentenceItems = new ArrayList<>();
        } else {
            sentenceItems.clear();
        }

        if (sentenceAdapter == null) {
            sentenceAdapter = new SentenceAdapter(sentenceItems, this);
            sentencesRecyclerView.setAdapter(sentenceAdapter);
        } else {
            sentenceAdapter.updateData(sentenceItems); // Clear RecyclerView
        }
        updateProgressBar();
        finish(); // This will close the activity
    }

    /**
     * Copies a file from a source URI to a target DocumentFile folder.
     * @param sourceUri The URI of the source file.
     * @param targetFolder The DocumentFile representing the target directory.
     * @return The DocumentFile of the newly created copied file, or null if copy fails.
     */
    private DocumentFile copyInputFileToWorkingFolder(Uri sourceUri, DocumentFile targetFolder) {
        DocumentFile sourceFile = DocumentFile.fromSingleUri(this, sourceUri);
        if (sourceFile == null || !sourceFile.isFile()) {
            Toast.makeText(this, "Source file not found or is not a file.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Source file invalid: " + sourceUri);
            return null;
        }

        try {
            // Use sourceFile.getName() to preserve the original name
            DocumentFile newFileInWorkingFolder = targetFolder.createFile(sourceFile.getType(), sourceFile.getName());

            if (newFileInWorkingFolder != null) {
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = getContentResolver().openOutputStream(newFileInWorkingFolder.getUri())) {

                    if (in == null || out == null) {
                        Log.e(TAG, "Failed to open streams for file copy.");
                        Toast.makeText(this, "Failed to open streams for file copy.", Toast.LENGTH_LONG).show();
                        return null;
                    }

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    //Toast.makeText(this, "Input file copied to working folder!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Input file copied to: " + newFileInWorkingFolder.getUri().toString());
                    return newFileInWorkingFolder; // Return the DocumentFile of the copied file
                }
            } else {
                Toast.makeText(this, "Failed to create copy of input file.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to create copy of input file: " + sourceFile.getName());
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying input file: " + e.getMessage(), e);
            Toast.makeText(this, "Error copying input file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during file copy: " + e.getMessage(), e);
            Toast.makeText(this, "Permission denied to copy input file.", Toast.LENGTH_LONG).show();
            return null;
        }
    }

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
            //Toast.makeText(this, "File loaded and sentences parsed!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "readFileContentAndPopulateList: File read successfully from " + uri.toString());

            if (!sentenceItems.isEmpty()) {
                selectSentence(0);
            }
            updateProgressBar();
        } catch (Exception e) {
            Log.e(TAG, "readFileContentAndPopulateList: Error reading file content from URI: " + uri.toString() + " - " + e.getMessage(), e);
            currentSelectedSentenceTextView.setText("Error reading file: " + e.getMessage());
            Toast.makeText(this, "Failed to read file content.", Toast.LENGTH_LONG).show();
            sentenceItems.clear(); // Ensure sentenceItems is empty on failure
            sentenceAdapter.updateData(sentenceItems); // Clear RecyclerView
            updateProgressBar();
            // Do NOT call handleInitializationError() here, as it's a specific file read error,
            // not necessarily a full app initialization failure that should close the activity.
        }
    }

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
        sentenceAdapter.updateData(sentenceItems);
    }

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

    private void selectSentence(int index) {
        if (sentenceItems == null || sentenceItems.isEmpty() || index < 0 || index >= sentenceItems.size()) {
            currentSelectedSentenceTextView.setText("No sentence selected.");
            currentSentenceIndex = -1;
            updateButtonStates();
            return;
        }

        if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
            sentenceItems.get(currentSentenceIndex).setSelected(false);
        }

        currentSentenceIndex = index;
        sentenceItems.get(currentSentenceIndex).setSelected(true);
        sentenceAdapter.setSelectedPosition(currentSentenceIndex);

        currentSelectedSentenceTextView.setText(sentenceItems.get(currentSentenceIndex).getText());

        sentencesRecyclerView.scrollToPosition(currentSentenceIndex);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSentences = sentenceItems != null && !sentenceItems.isEmpty();
        boolean isSentenceSelected = currentSentenceIndex != -1 && hasSentences;
        SentenceItem selectedItem = isSentenceSelected ? sentenceItems.get(currentSentenceIndex) : null;
        boolean hasRecordedAudio = selectedItem != null && selectedItem.getRecordedFileUri() != null;

        if (isRecording) {
            btnStartProcessing.setText("Stop Recording");
            btnStartProcessing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_red));
            btnStartProcessing.setEnabled(true);
            btnDeleteFile.setEnabled(false);
            btnPlayAudio.setEnabled(false);
            btnNextItem.setEnabled(false);
            btnSaveSession.setEnabled(false);
            btnLoadSession.setEnabled(false);
            btnExitActivity.setEnabled(false);
        } else if (isPlaying) {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_green));
            btnStartProcessing.setEnabled(false);
            btnDeleteFile.setEnabled(false);
            btnPlayAudio.setText("Stop Playing");
            btnPlayAudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_red));
            btnPlayAudio.setEnabled(true);
            btnNextItem.setEnabled(false);
            btnSaveSession.setEnabled(false);
            btnLoadSession.setEnabled(false);
            btnExitActivity.setEnabled(false);
        }
        else {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_green));
            btnStartProcessing.setEnabled(isSentenceSelected && !hasRecordedAudio);
            btnDeleteFile.setEnabled(hasRecordedAudio);
            btnPlayAudio.setText("Play");
            btnPlayAudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_blue));
            btnPlayAudio.setEnabled(hasRecordedAudio);
            btnNextItem.setEnabled(hasSentences && currentSentenceIndex < sentenceItems.size() - 1);
            btnSaveSession.setEnabled(currentUserId != null && hasSentences); // Can save if authenticated AND has sentences
            btnLoadSession.setEnabled(currentUserId != null); // Can load if authenticated
            btnExitActivity.setEnabled(true);
        }
    }

    private void toggleRecording() {
        if (isPlaying) {
            Toast.makeText(this, "Please stop audio playback before recording.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) { // If currently not recording, try to start
            if (currentSentenceIndex == -1) {
                Toast.makeText(this, "Please select a sentence to record.", Toast.LENGTH_SHORT).show();
                return;
            }

            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);

            if (selectedItem.getRecordedFileUri() != null) {
                Log.d(TAG, "Attempting to record on a sentence with existing record. URI: " + selectedItem.getRecordedFileUri().toString());
                Toast.makeText(this, "A record already exists for this sentence. Please delete it first.", Toast.LENGTH_LONG).show();
                return;
            }

            if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
                Toast.makeText(this, "Working folder not accessible. Cannot record.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Working folder is null, does not exist, or is not a directory.");
                return;
            }

            // Check for RECORD_AUDIO permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed to create file and start recording
                try {
                    // Construct the audio file name: {SentenceIndex}_{UniqueTimestamp}.wav
                    String recordedFileName = String.format(Locale.US, "%04d_%d.wav", // Format: 0001_123456789.wav
                            currentSentenceIndex + 1, // Sentence index (1-based)
                            System.currentTimeMillis()); // Unique timestamp in milliseconds

                    // Create the DocumentFile for the WAV output
                    tempRecordingDocumentFile = workingFolderDocument.createFile("audio/wav", recordedFileName); // Changed MIME type

                    if (tempRecordingDocumentFile == null) {
                        Toast.makeText(this, "Failed to create audio file for recording.", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Failed to create audio file in working folder.");
                        return;
                    }

                    // Start recording using AudioRecorderManager
                    audioRecorderManager.startRecording(tempRecordingDocumentFile);

                } catch (Exception e) {
                    Log.e(TAG, "Error preparing for recording: " + e.getMessage(), e);
                    Toast.makeText(this, "Error preparing for recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Ensure state is reset on error
                    isRecording = false;
                    updateButtonStates();
                    audioLevelHandler.removeCallbacks(audioLevelRunnable);
                    audioLevelIndicatorTextView.setText("Recording Error.");
                    if (tempRecordingDocumentFile != null && tempRecordingDocumentFile.exists()) {
                        tempRecordingDocumentFile.delete(); // Clean up incomplete file
                    }
                }
            } else {
                // Request permission if not granted
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        } else { // If currently recording, stop it
            audioRecorderManager.stopRecording();
        }
    }

    // --- Implementations of AudioRecorderManager.RecordingCallback ---
    @Override
    public void onRecordingStarted() {
        isRecording = true;
        updateButtonStates();
        //Toast.makeText(this, "Recording started for: \"" + sentenceItems.get(currentSentenceIndex).getText() + "\"", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Recording started by AudioRecorderManager.");
        audioLevelHandler.post(audioLevelRunnable); // Start simplified audio level indicator
    }

    @Override
    public void onRecordingStopped(Uri fileUri) {
        isRecording = false;
        audioLevelHandler.removeCallbacks(audioLevelRunnable);
        audioLevelIndicatorTextView.setText("Recording stopped.");

        if (currentSentenceIndex != -1 && fileUri != null) {
            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
            // Use the name from the DocumentFile created earlier, or derive from URI if needed
            String recordedFileName = tempRecordingDocumentFile != null ? tempRecordingDocumentFile.getName() : fileUri.getLastPathSegment();
            selectedItem.setRecordedFile(recordedFileName, fileUri);
            sentenceAdapter.notifyItemChanged(currentSentenceIndex);
            //Toast.makeText(this, "Recording stopped and saved.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording saved to: " + fileUri.toString());
            updateProgressBar();
            saveSessionState(currentSessionId);
            handleNextSentence(); // Automatically move to next sentence
        } else {
            Toast.makeText(this, "Recording stopped, but file was not saved or found.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Recording stopped but fileUri was null or currentSentenceIndex invalid.");
            if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                sentenceItems.get(currentSentenceIndex).clearRecordedFile();
                sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                updateProgressBar();
            }
        }
        tempRecordingDocumentFile = null; // Clear temporary reference
        updateButtonStates();
    }

    @Override
    public void onRecordingError(String errorMessage) {
        isRecording = false;
        audioLevelHandler.removeCallbacks(audioLevelRunnable);
        audioLevelIndicatorTextView.setText("Recording Error.");
        Toast.makeText(this, "Recording error: " + errorMessage, Toast.LENGTH_LONG).show();
        Log.e(TAG, "AudioRecorderManager error: " + errorMessage);

        // Clean up any partially created file if an error occurred during recording setup/process
        if (tempRecordingDocumentFile != null && tempRecordingDocumentFile.exists()) {
            tempRecordingDocumentFile.delete();
            Log.d(TAG, "Deleted incomplete recording file due to error.");
        }
        if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
            sentenceItems.get(currentSentenceIndex).clearRecordedFile(); // Clear recorded status for current sentence
            sentenceAdapter.notifyItemChanged(currentSentenceIndex);
        }
        tempRecordingDocumentFile = null; // Clear temporary reference
        updateButtonStates();
    }
    // --- End of AudioRecorderManager.RecordingCallback implementations ---


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
                            //Toast.makeText(this, "Recording deleted for sentence " + (currentSentenceIndex + 1), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Deleted file: " + fileToDelete.getName());
                            updateProgressBar();
                            saveSessionState(currentSessionId);
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
                        saveSessionState(currentSessionId);
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

    private void handlePlayAudio() {
        if (isRecording) {
            Toast.makeText(this, "Cannot play while recording is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPlaying) {
            stopPlayingAudio();
            //Toast.makeText(this, "Playback stopped.", Toast.LENGTH_SHORT).show();
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
                            audioLevelHandler.post(audioLevelRunnable); // Start simplified audio level indicator for playback
                            //Toast.makeText(this, "Playing: " + selectedItem.getRecordedFileName(), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Playing audio from: " + selectedItem.getRecordedFileUri().toString());
                        });

                        mediaPlayer.setOnCompletionListener(mp -> {
                            stopPlayingAudio();
                            //Toast.makeText(this, "Playback finished.", Toast.LENGTH_SHORT).show();
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
