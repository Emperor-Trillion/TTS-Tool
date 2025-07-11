// app/src/main/java/com/example/tts_tool/ProcessingActivity.java
package com.example.tts_tool;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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

public class ProcessingActivity extends AppCompatActivity implements SentenceAdapter.OnItemClickListener,
        ExitConfirmationDialogFragment.ExitConfirmationListener,
        LoadSessionDialogFragment.OnSessionSelectedListener {

    private static final String TAG = "ProcessingActivity";
    private static final int AMPLITUDE_UPDATE_INTERVAL = 100;
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri"; // Added for consistency
    private static final String FIRESTORE_COLLECTION_SESSIONS = "sessions";

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
    // private Button btnDeleteSessionFolder; // Removed as per request

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

    private SharedPreferences sharedPreferences;
    private Gson gson;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;
    private String currentSessionId; // This will hold the ID of the currently active session

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

    // --- START: SentenceItem and SessionState Definitions for Firestore Compatibility ---
    // These public static classes ensure proper deserialization from Firestore
    public static class SentenceItem {
        private int index;
        private String text;
        private String recordedFileName;
        private String recordedFileUriString; // For Firestore persistence
        private transient Uri recordedFileUri; // For in-memory Uri object, transient to avoid Gson trying to serialize it directly

        public SentenceItem() {
            // No-argument constructor required for Firestore deserialization
        }

        public SentenceItem(int index, String text) {
            this.index = index;
            this.text = text;
            this.recordedFileName = null;
            this.recordedFileUriString = null;
            this.recordedFileUri = null;
        }

        // Getters for Firestore
        public int getIndex() { return index; }
        public String getText() { return text; }
        public String getRecordedFileName() { return recordedFileName; }
        public String getRecordedFileUriString() { return recordedFileUriString; }

        // Setters for Firestore (needed for toObject)
        public void setIndex(int index) { this.index = index; }
        public void setText(String text) { this.text = text; }
        public void setRecordedFileName(String recordedFileName) { this.recordedFileName = recordedFileName; }
        public void setRecordedFileUriString(String recordedFileUriString) {
            this.recordedFileUriString = recordedFileUriString;
            // When setting the string, also update the Uri object
            this.recordedFileUri = (recordedFileUriString != null) ? Uri.parse(recordedFileUriString) : null;
        }

        // Custom methods for internal use (managing Uri object)
        public void setRecordedFile(String fileName, Uri fileUri) {
            this.recordedFileName = fileName;
            this.recordedFileUri = fileUri;
            this.recordedFileUriString = (fileUri != null) ? fileUri.toString() : null;
        }

        public Uri getRecordedFileUri() {
            // Ensure recordedFileUri is populated if only recordedFileUriString is present (e.g., after deserialization)
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
        String originalInputFileUriString;
        String rootFolderUriString;
        String workingFolderUriString;
        int currentSentenceIndex;
        List<SentenceItem> sentenceItems;
        long lastModified;

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

        public SessionState() {}

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

        // Authenticate anonymously
        Log.d(TAG, "Attempting anonymous Firebase authentication...");
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
                                // Now that we have a user ID, proceed with session setup/loading
                                initializeSessionBasedOnIntent();
                            } else {
                                Log.e(TAG, "signInAnonymously:success but current user is null.");
                                Toast.makeText(ProcessingActivity.this, "Authentication failed: User null.", Toast.LENGTH_LONG).show();
                                handleInitializationError();
                            }
                        } else {
                            Log.e(TAG, "signInAnonymously:failure", task.getException());
                            Toast.makeText(ProcessingActivity.this, "Authentication failed. Cannot save/load sessions.", Toast.LENGTH_LONG).show();
                            handleInitializationError(); // Disable functionality if auth fails
                        }
                    }
                });

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();

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
        btnLoadSession = findViewById(R.id.btn_load_session); // Initialize new button
        btnExitActivity = findViewById(R.id.btn_exit_activity);
        // btnDeleteSessionFolder = findViewById(R.id.btn_delete_session_folder); // Removed as per request

        sentencesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        audioLevelHandler = new Handler(Looper.getMainLooper());
        audioLevelRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording && mediaRecorder != null) {
                    int amplitude = mediaRecorder.getMaxAmplitude();
                    updateAudioLevelIndicator(amplitude); // Call the new method
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                } else if (isPlaying) {
                    audioLevelIndicatorTextView.setText("Playing audio...");
                    audioLevelHandler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL);
                }
            }
        };

        btnStartProcessing.setOnClickListener(v -> toggleRecording());
        // Modified btnPlayAudio click listener to handle stop functionality
        btnPlayAudio.setOnClickListener(v -> {
            if (isPlaying) {
                stopPlayingAudio();
            } else {
                handlePlayAudio();
            }
        });
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnNextItem.setOnClickListener(v -> handleNextSentence());
        btnSaveSession.setOnClickListener(v -> showSaveSessionDialog()); // Call dialog for saving
        btnLoadSession.setOnClickListener(v -> {
            // This button now launches the LoadSessionDialogFragment
            LoadSessionDialogFragment loadDialog = new LoadSessionDialogFragment();
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });
        btnExitActivity.setOnClickListener(v -> showExitConfirmationDialog());
        // btnDeleteSessionFolder.setOnClickListener(v -> handleDeleteSessionFolder()); // Removed as per request

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        });

        // Initial UI update, will be re-updated after session setup/load
        updateProgressBar();
        updateButtonStates();
        audioLevelIndicatorTextView.setText("Ready to record.");
    }

    /**
     * Called after Firebase authentication to determine whether to setup a new session or load an existing one.
     */
    private void initializeSessionBasedOnIntent() {
        String usernameFromIntent = getIntent().getStringExtra("username");
        Uri originalInputFileUriFromIntent = getIntent().getData(); // This is the text file URI
        String rootFolderUriStringFromIntent = getIntent().getStringExtra("root_folder_uri"); // This is the working folder URI
        String sessionIdFromIntent = getIntent().getStringExtra("session_id"); // New: for loading specific sessions

        if (sessionIdFromIntent != null) {
            Log.d(TAG, "Attempting to load saved session with ID: " + sessionIdFromIntent);
            currentSessionId = sessionIdFromIntent; // Set current session ID
            loadSessionState(sessionIdFromIntent); // Load specific session
        } else if (usernameFromIntent != null && originalInputFileUriFromIntent != null && rootFolderUriStringFromIntent != null) {
            Log.d(TAG, "Starting new session from intent.");
            // For a new session, generate a new ID here
            currentSessionId = UUID.randomUUID().toString(); // Generate ID for new session
            setupNewSession(usernameFromIntent, originalInputFileUriFromIntent, rootFolderUriStringFromIntent);
        } else {
            Log.e(TAG, "No session data provided in intent. Cannot proceed.");
            Toast.makeText(this, "No session data found. Please start a new session or load a saved one from the previous screen.", Toast.LENGTH_LONG).show();
            handleInitializationError();
        }
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

                // Save initial state of the new session
                saveSessionState(currentSessionId);

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

        // Set a default name if it's a new session or the current one is an auto-generated UUID
        if (currentSessionId == null || currentSessionId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            input.setText("Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
        } else {
            input.setText(currentSessionId); // Pre-fill with current session ID if it's a user-defined one
        }


        builder.setPositiveButton("Save", (dialog, which) -> {
            String sessionName = input.getText().toString().trim();
            if (sessionName.isEmpty()) {
                Toast.makeText(this, "Session name cannot be empty. Please try again.", Toast.LENGTH_SHORT).show();
                // Optionally, re-show the dialog or generate a default name
                sessionName = "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                Toast.makeText(this, "Using auto-generated name: " + sessionName, Toast.LENGTH_SHORT).show();
            }
            currentSessionId = sessionName; // Set the current session ID to the user-provided name
            saveSessionState(currentSessionId); // Save with the chosen name
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
        String originalInputFileUriString = getIntent().getData() != null ? getIntent().getData().toString() : null;
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
                sessionId, // Use the provided session ID
                username,
                originalInputFileUriString,
                rootFolderUriString,
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
                .document(sessionId); // Use the provided session ID

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
     * @param sessionId The ID of the session to load.
     */
    private void loadSessionState(String sessionId) {
        if (currentUserId == null) {
            Log.e(TAG, "User not authenticated. Cannot load session.");
            Toast.makeText(this, "Error: Not authenticated to load session.", Toast.LENGTH_SHORT).show();
            handleInitializationError();
            return;
        }

        String appId = getApplicationContext().getPackageName();
        DocumentReference sessionDocRef = db.collection("artifacts")
                .document(appId)
                .collection("users")
                .document(currentUserId)
                .collection(FIRESTORE_COLLECTION_SESSIONS)
                .document(sessionId);

        Log.d(TAG, "Attempting to load session from Firestore at path: " + sessionDocRef.getPath());
        sessionDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult().exists()) {
                    SessionState loadedState = task.getResult().toObject(SessionState.class); // Deserialize directly to SessionState
                    if (loadedState != null) {
                        usernameTextView.setText("Speaker: " + loadedState.getUsername());

                        // Reconstruct workingFolderDocument
                        if (loadedState.getWorkingFolderUriString() != null) {
                            Uri workingFolderUri = Uri.parse(loadedState.getWorkingFolderUriString());
                            workingFolderDocument = DocumentFile.fromTreeUri(this, workingFolderUri);
                            if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
                                Log.e(TAG, "Loaded working folder does not exist or is not a directory: " + loadedState.getWorkingFolderUriString());
                                Toast.makeText(this, "Saved session's working folder not found or accessible. Please re-select folder.", Toast.LENGTH_LONG).show();
                                handleInitializationError();
                                return;
                            }
                            fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());
                        } else {
                            Log.e(TAG, "Loaded session state missing working folder URI.");
                            handleInitializationError();
                            return;
                        }

                        // Re-read file content and populate list based on the original input file URI
                        if (loadedState.getOriginalInputFileUriString() != null) {
                            Uri originalInputFileUri = Uri.parse(loadedState.getOriginalInputFileUriString());
                            readFileContentAndPopulateList(originalInputFileUri);
                            loadedFileNameTextView.setText("Loaded File: " + (DocumentFile.fromSingleUri(this, originalInputFileUri) != null ? DocumentFile.fromSingleUri(this, originalInputFileUri).getName() : "N/A"));
                        } else {
                            Log.e(TAG, "Loaded session state missing original input file URI.");
                            handleInitializationError();
                            return;
                        }

                        // After parsing sentences, update their recorded file Uris from loadedState.sentenceItems
                        if (sentenceItems != null && loadedState.getSentenceItems() != null) {
                            for (int i = 0; i < sentenceItems.size() && i < loadedState.getSentenceItems().size(); i++) {
                                SentenceItem currentItem = sentenceItems.get(i);
                                SentenceItem loadedItem = loadedState.getSentenceItems().get(i);
                                if (loadedItem.getRecordedFileName() != null && loadedItem.getRecordedFileUriString() != null) {
                                    currentItem.setRecordedFile(loadedItem.getRecordedFileName(), Uri.parse(loadedItem.getRecordedFileUriString()));
                                }
                            }
                            sentenceAdapter.notifyDataSetChanged();
                        }

                        currentSentenceIndex = loadedState.getCurrentSentenceIndex();
                        if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                            selectSentence(currentSentenceIndex);
                        } else if (!sentenceItems.isEmpty()) {
                            selectSentence(0);
                        }

                        currentSessionId = sessionId; // Set the current session ID to the loaded one
                        Toast.makeText(this, "Session loaded successfully!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Session state loaded successfully: " + sessionId);
                        updateProgressBar();
                        updateButtonStates();
                    } else {
                        Log.e(TAG, "Loaded SessionState object is null for ID: " + sessionId);
                        Toast.makeText(this, "Failed to load session: Data corrupted.", Toast.LENGTH_LONG).show();
                        handleInitializationError();
                    }
                } else {
                    Log.w(TAG, "Session document not found for ID: " + sessionId);
                    Toast.makeText(this, "Saved session not found.", Toast.LENGTH_LONG).show();
                    handleInitializationError();
                }
            } else {
                Log.e(TAG, "Error fetching session from Firestore: " + task.getException());
                Toast.makeText(this, "Error loading session: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                handleInitializationError();
            }
        });
    }

    /**
     * Callback from LoadSessionDialogFragment when a session is selected.
     * This method is called to load the selected session into the current activity.
     * @param sessionState The SessionState object representing the selected session.
     */
    @Override
    public void onSessionSelected(ProcessingActivity.SessionState sessionState) {
        if (sessionState != null && sessionState.getSessionId() != null) {
            Log.d(TAG, "Session selected from dialog: " + sessionState.getSessionId());
            // Stop any ongoing recording or playback before loading a new session
            stopRecordingInternal();
            stopPlayingAudio();
            loadSessionState(sessionState.getSessionId());
        } else {
            Toast.makeText(this, "Failed to load session: Invalid session data from dialog.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid sessionState received from LoadSessionDialogFragment.");
        }
    }


    private void showExitConfirmationDialog() {
        if (isRecording || isPlaying) {
            Toast.makeText(this, "Cannot exit while recording or playing is in progress. Please stop first.", Toast.LENGTH_LONG).show();
            return;
        }
        ExitConfirmationDialogFragment dialog = new ExitConfirmationDialogFragment();
        dialog.show(getSupportFragmentManager(), "ExitConfirmationDialog");
    }

    @Override
    public void onSaveAndExit() {
        // When saving on exit, use the currentSessionId or generate a default one if it's a new session
        String sessionIdToSave = currentSessionId;
        if (sessionIdToSave == null || sessionIdToSave.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            sessionIdToSave = "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        }
        saveSessionState(sessionIdToSave);
        Toast.makeText(this, "Session saved and exiting!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User chose Save & Exit.");
        finish();
    }

    @Override
    public void onContinueRecording() {
        Toast.makeText(this, "Continuing recording session.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User chose Continue Recording.");
    }

    @Override
    public void onExitWithoutSaving() {
        Log.d(TAG, "User chose Exit Without Saving.");

        boolean hasRecordedSentence = false;
        if (sentenceItems != null) {
            for (SentenceItem item : sentenceItems) {
                if (item.getRecordedFileUri() != null) {
                    hasRecordedSentence = true;
                    break;
                }
            }
        }

        if (!hasRecordedSentence && workingFolderDocument != null && workingFolderDocument.exists()) {
            // If no sentences recorded AND working folder exists, delete silently
            deleteCurrentSessionSilently();
            Toast.makeText(this, "Exiting without saving session. Session data deleted.", Toast.LENGTH_LONG).show();
        } else {
            // If sentences were recorded, or no working folder to begin with, just exit
            Toast.makeText(this, "Exiting without saving session state.", Toast.LENGTH_SHORT).show();
        }
        finish(); // Exit the activity
    }

    /**
     * Deletes the current session's working folder and its Firestore entry silently (without confirmation dialog).
     * Resets UI and internal state.
     */
    private void deleteCurrentSessionSilently() {
        if (workingFolderDocument != null && workingFolderDocument.exists()) {
            try {
                if (workingFolderDocument.delete()) {
                    Log.d(TAG, "Session folder deleted: " + workingFolderDocument.getUri().toString());

                    // Delete from Firestore
                    if (currentUserId != null && currentSessionId != null) {
                        String appId = getApplicationContext().getPackageName();
                        db.collection("artifacts")
                                .document(appId)
                                .collection("users")
                                .document(currentUserId)
                                .collection(FIRESTORE_COLLECTION_SESSIONS)
                                .document(currentSessionId)
                                .delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Log.d(TAG, "Session document deleted from Firestore: " + currentSessionId);
                                    } else {
                                        Log.e(TAG, "Error deleting session document from Firestore: " + task.getException());
                                        // Toast.makeText(this, "Error deleting session record from cloud.", Toast.LENGTH_LONG).show(); // Removed as per "just a toast message"
                                    }
                                });
                    }
                } else {
                    Log.e(TAG, "Failed to delete session folder: " + workingFolderDocument.getUri().toString());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied to delete folder: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "An error occurred during silent folder deletion: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "No working folder to delete or it doesn't exist.");
        }

        // Reset UI and internal state regardless of deletion success/failure
        sentenceItems = new ArrayList<>();
        currentSentenceIndex = -1;
        workingFolderDocument = null;
        currentSessionId = null; // Clear current session ID
        usernameTextView.setText("Speaker: N/A");
        fileUriTextView.setText("Working Folder: N/A");
        loadedFileNameTextView.setText("Loaded File: N/A");
        currentSelectedSentenceTextView.setText("Please load a file or start a new session.");
        audioLevelIndicatorTextView.setText("Ready to record.");
        if (sentenceAdapter != null) {
            sentenceAdapter.updateData(sentenceItems); // Clear RecyclerView
        } else {
            sentenceAdapter = new SentenceAdapter(sentenceItems, this);
            sentencesRecyclerView.setAdapter(sentenceAdapter);
        }
        updateProgressBar();
        updateButtonStates();
    }


    private void handleInitializationError() {
        fileUriTextView.setText("Working Folder: Error");
        loadedFileNameTextView.setText("Loaded File: Error");
        currentSelectedSentenceTextView.setText("Initialization Error. Please restart.");
        audioLevelIndicatorTextView.setText("Error.");
        btnStartProcessing.setEnabled(false);
        btnDeleteFile.setEnabled(false);
        btnPlayAudio.setEnabled(false);
        btnNextItem.setEnabled(false);
        btnSaveSession.setEnabled(false);
        btnLoadSession.setEnabled(false); // Disable load button on error
        btnExitActivity.setEnabled(false);
        // btnDeleteSessionFolder.setEnabled(false); // Removed as per request
        if (sentencesRecyclerView.getAdapter() == null) {
            sentencesRecyclerView.setAdapter(new SentenceAdapter(new ArrayList<>(), this));
        }
        updateProgressBar();
        Log.e(TAG, "Initialization Error: UI disabled.");
    }

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
            Log.e(TAG, "Error copying input file: " + e.getMessage(), e); // Fixed typo: Hmessage() -> getMessage()
            Toast.makeText(this, "Error copying input file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during file copy: " + e.getMessage(), e);
            Toast.makeText(this, "Permission denied to copy input file.", Toast.LENGTH_LONG).show();
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

    private void updateButtonStates() {
        boolean hasSentences = sentenceItems != null && !sentenceItems.isEmpty();
        boolean isSentenceSelected = currentSentenceIndex != -1 && hasSentences;
        SentenceItem selectedItem = isSentenceSelected ? sentenceItems.get(currentSentenceIndex) : null;
        boolean hasRecordedAudio = selectedItem != null && selectedItem.getRecordedFileUri() != null; // Use getRecordedFileUri()

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
            btnPlayAudio.setText("Stop Playing"); // Added to ensure button text updates
            btnPlayAudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_red)); // Added to ensure button color updates
            btnPlayAudio.setEnabled(true);
            btnNextItem.setEnabled(false);
            btnSaveSession.setEnabled(false);
            btnLoadSession.setEnabled(false);
            btnExitActivity.setEnabled(false);
        }
        else {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_green));
            // Enable start recording only if a sentence is selected AND no recording exists for it
            btnStartProcessing.setEnabled(isSentenceSelected && !hasRecordedAudio);
            btnDeleteFile.setEnabled(hasRecordedAudio);
            btnPlayAudio.setText("Play"); // Ensure button text is "Play" when not playing
            btnPlayAudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_blue)); // Ensure button color is "Play" color
            btnPlayAudio.setEnabled(hasRecordedAudio); // Only enabled if there's a recording
            btnNextItem.setEnabled(hasSentences && currentSentenceIndex < sentenceItems.size() - 1);
            btnSaveSession.setEnabled(currentUserId != null); // Can save if authenticated
            btnLoadSession.setEnabled(currentUserId != null); // Can load if authenticated
            btnExitActivity.setEnabled(true);
        }
    }

    private void toggleRecording() {
        if (isPlaying) {
            Toast.makeText(this, "Please stop audio playback before recording.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) {
            // This is the primary check for existing recordings
            if (currentSentenceIndex != -1 && sentenceItems.get(currentSentenceIndex).getRecordedFileUri() != null) {
                Log.d(TAG, "Attempting to record on a sentence with existing record. URI: " + sentenceItems.get(currentSentenceIndex).getRecordedFileUri().toString()); // Added log for debugging
                Toast.makeText(this, "A record already exists for this sentence. Please delete it first.", Toast.LENGTH_LONG).show();
                return; // Stop here if a record exists
            }

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

    private void startRecordingInternal() {
        if (currentSentenceIndex == -1) {
            Toast.makeText(this, "Please select a sentence to record.", Toast.LENGTH_SHORT).show();
            return;
        }

        SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);

        if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
            Toast.makeText(this, "Working folder not accessible. Cannot record.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Working folder is null, does not exist, or is not a directory.");
            return;
        }

        try {
            String recordedFileName = String.format(Locale.US, "%s_%03d_%s.mp3",
                    usernameTextView.getText().toString().replace("Speaker: ", "").replaceAll("[^a-zA-Z0-9_\\-]", "_"),
                    currentSentenceIndex + 1,
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));

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

        boolean stopSuccessful = false;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            stopSuccessful = true;

            isRecording = false; // Set state immediately
            audioLevelHandler.removeCallbacks(audioLevelRunnable); // Stop updates immediately
            audioLevelIndicatorTextView.setText("Recording stopped."); // Update UI immediately

            if (currentSentenceIndex != -1 && currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
                selectedItem.setRecordedFile(currentRecordingDocumentFile.getName(), currentRecordingDocumentFile.getUri());
                sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                Toast.makeText(this, "Recording stopped and saved.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Recording saved to: " + currentRecordingDocumentFile.getUri().toString());
                updateProgressBar();
                saveSessionState(currentSessionId); // Save after recording a sentence
                handleNextSentence(); // Auto-advance
            } else {
                Toast.makeText(this, "Recording stopped, but file was not saved or found.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Recording stopped but currentRecordingDocumentFile was null or did not exist.");
                // Ensure the SentenceItem is cleared if the file wasn't saved correctly
                if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                    sentenceItems.get(currentSentenceIndex).clearRecordedFile();
                    sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                    updateProgressBar();
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException during MediaRecorder stop/release: " + e.getMessage(), e);
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            audioLevelHandler.removeCallbacks(audioLevelRunnable);
            audioLevelIndicatorTextView.setText("Recording Error.");
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete(); // Attempt to clean up corrupted file
                if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                    sentenceItems.get(currentSentenceIndex).clearRecordedFile(); // Clear state if file deleted
                }
            }
            isRecording = false; // Ensure state is reset even on error
            Toast.makeText(this, "Recording failed or was corrupted.", Toast.LENGTH_SHORT).show();
        } finally {
            currentRecordingDocumentFile = null; // Always clear this reference
            updateButtonStates(); // Always update button states
        }
    }


    private void updateAudioLevelIndicator(int amplitude) {
        if (amplitude > 1000) {
            audioLevelIndicatorTextView.setText("● Recording (High Sound)");
            audioLevelIndicatorTextView.setTextColor(ContextCompat.getColor(this, R.color.custom_red));
        } else if (amplitude > 100) {
            audioLevelIndicatorTextView.setText("● Recording (Medium Sound)");
            audioLevelIndicatorTextView.setTextColor(ContextCompat.getColor(this, R.color.custom_orange));
        } else {
            audioLevelIndicatorTextView.setText("○ Recording (Low/No Sound)");
            audioLevelIndicatorTextView.setTextColor(ContextCompat.getColor(this, R.color.black));
        }
    }

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
                            saveSessionState(currentSessionId); // Save after deleting a recording
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
                        saveSessionState(currentSessionId); // Save even if file not found but data cleared
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

    private void handleExitActivity() {
        showExitConfirmationDialog();
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
