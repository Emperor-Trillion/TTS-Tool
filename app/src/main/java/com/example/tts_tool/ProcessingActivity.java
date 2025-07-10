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
        ExitConfirmationDialogFragment.ExitConfirmationListener {

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
    private Button btnDeleteFile;
    private Button btnPlayAudio;
    private Button btnNextItem;
    private Button btnSaveSession;
    private Button btnLoadSession; // New Load Session Button
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

    // SessionState class - Changed to public static
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
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnPlayAudio.setOnClickListener(v -> handlePlayAudio());
        btnNextItem.setOnClickListener(v -> handleNextSentence());
        btnSaveSession.setOnClickListener(v -> showSaveSessionDialog()); // Call dialog for saving
        btnLoadSession.setOnClickListener(v -> showLoadSessionDialog()); // Call dialog for loading
        btnExitActivity.setOnClickListener(v -> showExitConfirmationDialog());

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
     * Shows a dialog to display and select from existing saved sessions.
     */
    private void showLoadSessionDialog() {
        if (currentUserId == null) {
            Toast.makeText(this, "Not authenticated. Cannot load sessions.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load Session");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_load_session, null, false);
        RecyclerView loadSessionRecyclerView = viewInflated.findViewById(R.id.load_session_recycler_view);
        loadSessionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        builder.setView(viewInflated);

        List<SessionState> savedSessions = new ArrayList<>();
        LoadSessionAdapter loadSessionAdapter = new LoadSessionAdapter(savedSessions, sessionState -> {
            // Callback when a session is selected from the list
            currentSessionId = sessionState.getSessionId(); // Set the current session ID
            loadSessionState(sessionState.getSessionId()); // Load the selected session
            Toast.makeText(this, "Attempting to load session: " + sessionState.getSessionId(), Toast.LENGTH_SHORT).show();
            // Dismiss the dialog after selection
            if (loadSessionDialog != null) {
                loadSessionDialog.dismiss();
            }
        });
        loadSessionRecyclerView.setAdapter(loadSessionAdapter);

        // Fetch sessions from Firestore
        String appId = getApplicationContext().getPackageName();
        db.collection("artifacts")
                .document(appId)
                .collection("users")
                .document(currentUserId)
                .collection(FIRESTORE_COLLECTION_SESSIONS)
                .orderBy("lastModified", Query.Direction.DESCENDING) // Order by latest modified
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        savedSessions.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            SessionState session = document.toObject(SessionState.class);
                            savedSessions.add(session);
                        }
                        // Sort by last modified descending, just in case orderBy didn't fully work or for local list
                        Collections.sort(savedSessions, (s1, s2) -> Long.compare(s2.getLastModified(), s1.getLastModified()));
                        loadSessionAdapter.notifyDataSetChanged();

                        if (savedSessions.isEmpty()) {
                            Toast.makeText(this, "No saved sessions found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Error fetching saved sessions: " + task.getException());
                        Toast.makeText(this, "Error loading sessions: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        loadSessionDialog = builder.create(); // Store the dialog instance
        loadSessionDialog.show();
    }
    private AlertDialog loadSessionDialog; // Member variable to hold the dialog instance


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

                        // Set current sentence index and update UI
                        currentSentenceIndex = loadedState.getCurrentSentenceIndex();
                        if (currentSentenceIndex >= 0 && currentSentenceIndex < sentenceItems.size()) {
                            selectSentence(currentSentenceIndex);
                            sentencesRecyclerView.scrollToPosition(currentSentenceIndex);
                        } else {
                            // If index is out of bounds, select the first sentence or keep -1
                            if (!sentenceItems.isEmpty()) {
                                selectSentence(0);
                            } else {
                                currentSelectedSentenceTextView.setText("No sentences loaded.");
                            }
                        }
                        updateProgressBar();
                        updateButtonStates();
                        Toast.makeText(this, "Session '" + sessionId + "' loaded successfully!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Session loaded: " + sessionId);

                    } else {
                        Log.e(TAG, "Loaded SessionState object is null for session ID: " + sessionId);
                        Toast.makeText(this, "Failed to load session: Invalid data.", Toast.LENGTH_LONG).show();
                        handleInitializationError();
                    }
                } else {
                    Log.w(TAG, "Session document not found for ID: " + sessionId);
                    Toast.makeText(this, "Session '" + sessionId + "' not found.", Toast.LENGTH_LONG).show();
                    handleInitializationError();
                }
            } else {
                Log.e(TAG, "Error loading session from Firestore: " + task.getException());
                Toast.makeText(this, "Error loading session: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                handleInitializationError();
            }
        });
    }

    /**
     * Updates the audio level indicator TextView based on the current amplitude.
     * @param amplitude The current amplitude from the MediaRecorder.
     */
    private void updateAudioLevelIndicator(int amplitude) {
        // A simple way to visualize amplitude:
        // You can adjust the scaling or use a different visual representation (e.g., a bar)
        String level = "";
        if (amplitude == 0) {
            level = "Silent";
        } else if (amplitude < 1000) {
            level = "Low";
        } else if (amplitude < 5000) {
            level = "Medium";
        } else if (amplitude < 15000) {
            level = "High";
        } else {
            level = "Very High";
        }
        audioLevelIndicatorTextView.setText("Audio Level: " + level + " (" + amplitude + ")");
    }


    private void handleInitializationError() {
        // Disable all interactive elements and show an error message
        btnStartProcessing.setEnabled(false);
        btnDeleteFile.setEnabled(false);
        btnPlayAudio.setEnabled(false);
        btnNextItem.setEnabled(false);
        btnSaveSession.setEnabled(false);
        btnLoadSession.setEnabled(false);
        currentSelectedSentenceTextView.setText("Error: Cannot initialize session. Please restart app.");
    }

    private void toggleRecording() {
        if (currentSentenceIndex == -1 || sentenceItems == null || sentenceItems.isEmpty()) {
            Toast.makeText(this, "Please select a sentence to record first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        if (isRecording) {
            stopRecording();
        } else {
            startRecordingInternal();
        }
    }

    private void startRecordingInternal() {
        if (workingFolderDocument == null) {
            Toast.makeText(this, "Working folder not set. Cannot record.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop any currently playing audio before recording
        stopPlayingAudio();

        String fileName = String.format(Locale.US, "%s_%03d_%s.mp3",
                usernameTextView.getText().toString().replace("Speaker: ", "").replaceAll("[^a-zA-Z0-9_\\-]", "_"),
                currentSentenceIndex + 1,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));

        currentRecordingDocumentFile = workingFolderDocument.createFile("audio/mpeg", fileName); // Mime type for MP3

        if (currentRecordingDocumentFile == null) {
            Toast.makeText(this, "Failed to create audio file. Check folder permissions.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to create audio file: " + fileName);
            return;
        }

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // Use MPEG_4 for MP3-like output
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // AAC is good for quality/size
            mediaRecorder.setOutputFile(getContentResolver().openFileDescriptor(currentRecordingDocumentFile.getUri(), "w").getFileDescriptor());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording to: " + currentRecordingDocumentFile.getUri().toString());

            audioLevelHandler.postDelayed(audioLevelRunnable, AMPLITUDE_UPDATE_INTERVAL); // Start amplitude updates
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isRecording = false;
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
            if (currentRecordingDocumentFile != null) {
                currentRecordingDocumentFile.delete(); // Clean up incomplete file
            }
        } finally {
            updateButtonStates();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                audioLevelHandler.removeCallbacks(audioLevelRunnable); // Stop amplitude updates
                audioLevelIndicatorTextView.setText("Recording stopped.");

                // Update the SentenceItem with the recorded file info
                if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                    SentenceItem currentItem = sentenceItems.get(currentSentenceIndex);
                    currentItem.setRecordedFile(currentRecordingDocumentFile.getName(), currentRecordingDocumentFile.getUri());
                    sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                    updateProgressBar();
                    Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Recording saved for sentence " + currentSentenceIndex + ": " + currentRecordingDocumentFile.getUri().toString());
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "RuntimeException on MediaRecorder stop: " + e.getMessage());
                Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
                if (currentRecordingDocumentFile != null) {
                    currentRecordingDocumentFile.delete(); // Clean up incomplete file
                }
            } finally {
                updateButtonStates();
            }
        }
    }

    private void handleDeleteRecording() {
        if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
            SentenceItem currentItem = sentenceItems.get(currentSentenceIndex);
            if (currentItem.getRecordedFileUri() != null) {
                DocumentFile recordedFile = DocumentFile.fromSingleUri(this, currentItem.getRecordedFileUri());
                if (recordedFile != null && recordedFile.exists()) {
                    if (recordedFile.delete()) {
                        currentItem.clearRecordedFile();
                        sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                        updateProgressBar();
                        Toast.makeText(this, "Recording deleted.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Deleted recording for sentence " + currentSentenceIndex);
                    } else {
                        Toast.makeText(this, "Failed to delete recording.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to delete recording file: " + recordedFile.getUri().toString());
                    }
                } else {
                    currentItem.clearRecordedFile(); // Clear reference even if file doesn't exist
                    sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                    updateProgressBar();
                    Toast.makeText(this, "No recording found to delete.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No recording to delete for this sentence.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No sentence selected to delete recording from.", Toast.LENGTH_SHORT).show();
        }
        updateButtonStates();
    }

    private void handlePlayAudio() {
        if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
            SentenceItem currentItem = sentenceItems.get(currentSentenceIndex);
            if (currentItem.getRecordedFileUri() != null) {
                playAudio(currentItem.getRecordedFileUri());
            } else {
                Toast.makeText(this, "No audio recorded for this sentence yet.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No sentence selected to play audio from.", Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudio(Uri audioFileUri) {
        stopRecording(); // Ensure recording is stopped before playing

        if (mediaPlayer != null) {
            stopPlayingAudio();
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, audioFileUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            audioLevelHandler.postDelayed(audioLevelRunnable, AMPLITUDE_UPDATE_INTERVAL); // Indicate playing
            Toast.makeText(this, "Playing audio...", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Playing audio from: " + audioFileUri.toString());

            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayingAudio();
                audioLevelIndicatorTextView.setText("Ready to record."); // Reset indicator after playback
                updateButtonStates();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                Toast.makeText(this, "Error playing audio.", Toast.LENGTH_SHORT).show();
                stopPlayingAudio();
                audioLevelIndicatorTextView.setText("Ready to record.");
                updateButtonStates();
                return true;
            });
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio: " + e.getMessage());
            Toast.makeText(this, "Error playing audio: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopPlayingAudio();
        } finally {
            updateButtonStates();
        }
    }

    private void stopPlayingAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException on MediaPlayer stop/release: " + e.getMessage());
            } finally {
                mediaPlayer = null;
                isPlaying = false;
                audioLevelHandler.removeCallbacks(audioLevelRunnable);
                audioLevelIndicatorTextView.setText("Ready to record."); // Reset indicator
            }
        }
    }

    private void handleNextSentence() {
        stopRecording();
        stopPlayingAudio();

        if (sentenceItems == null || sentenceItems.isEmpty()) {
            Toast.makeText(this, "No sentences loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex < sentenceItems.size() - 1) {
            selectSentence(currentSentenceIndex + 1);
            sentencesRecyclerView.scrollToPosition(currentSentenceIndex);
        } else {
            Toast.makeText(this, "End of sentences.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onItemClick(int position) {
        stopRecording();
        stopPlayingAudio();
        selectSentence(position);
    }

    private void selectSentence(int position) {
        if (sentenceItems != null && position >= 0 && position < sentenceItems.size()) {
            // Deselect previous item
            if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                sentenceItems.get(currentSentenceIndex).setSelected(false);
            }

            currentSentenceIndex = position;
            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
            selectedItem.setSelected(true); // Select current item

            currentSelectedSentenceTextView.setText(selectedItem.getText());
            sentenceAdapter.setSelectedPosition(currentSentenceIndex); // Update adapter's selected position
            sentencesRecyclerView.scrollToPosition(currentSentenceIndex); // Scroll to selected item
            updateButtonStates();
            Log.d(TAG, "Selected sentence index: " + currentSentenceIndex);
        }
    }

    private void readFileContentAndPopulateList(Uri fileUri) {
        sentenceItems = new ArrayList<>();
        try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            int index = 0;
            // Pattern to split sentences by common delimiters, keeping the delimiter
            Pattern sentenceDelimiter = Pattern.compile("(?<=[.?!])\\s*|(?<=\\n)");

            StringBuilder currentParagraph = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                // Handle empty lines as paragraph breaks
                if (line.trim().isEmpty()) {
                    if (currentParagraph.length() > 0) {
                        String[] sentencesInParagraph = sentenceDelimiter.split(currentParagraph.toString().trim());
                        for (String sentence : sentencesInParagraph) {
                            if (!sentence.trim().isEmpty()) {
                                sentenceItems.add(new SentenceItem(index++, sentence.trim()));
                            }
                        }
                        currentParagraph = new StringBuilder(); // Reset for next paragraph
                    }
                } else {
                    // Append line to current paragraph, add a space if not first line
                    if (currentParagraph.length() > 0) {
                        currentParagraph.append(" ");
                    }
                    currentParagraph.append(line.trim());
                }
            }

            // Process any remaining content in currentParagraph after loop finishes
            if (currentParagraph.length() > 0) {
                String[] sentencesInParagraph = sentenceDelimiter.split(currentParagraph.toString().trim());
                for (String sentence : sentencesInParagraph) {
                    if (!sentence.trim().isEmpty()) {
                        sentenceItems.add(new SentenceItem(index++, sentence.trim()));
                    }
                }
            }

            if (sentenceItems.isEmpty()) {
                Toast.makeText(this, "No sentences found in the selected file.", Toast.LENGTH_LONG).show();
                handleInitializationError();
                return;
            }

            sentenceAdapter = new SentenceAdapter(sentenceItems, this);
            sentencesRecyclerView.setAdapter(sentenceAdapter);

            // Select the first sentence initially
            selectSentence(0);
            updateProgressBar();

        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            handleInitializationError();
        }
    }

    private void copyInputFileToWorkingFolder(Uri originalFileUri, DocumentFile destinationFolder) {
        DocumentFile originalDocumentFile = DocumentFile.fromSingleUri(this, originalFileUri);
        if (originalDocumentFile == null || !originalDocumentFile.exists()) {
            Log.e(TAG, "Original input file not found: " + originalFileUri.toString());
            Toast.makeText(this, "Original input file not found.", Toast.LENGTH_LONG).show();
            return;
        }

        try (InputStream in = getContentResolver().openInputStream(originalFileUri);
             OutputStream out = getContentResolver().openOutputStream(destinationFolder.createFile(originalDocumentFile.getType(), originalDocumentFile.getName()).getUri())) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            Log.d(TAG, "Input file copied to working folder: " + destinationFolder.getName() + "/" + originalDocumentFile.getName());
        } catch (IOException e) {
            Log.e(TAG, "Error copying input file to working folder: " + e.getMessage());
            Toast.makeText(this, "Error copying input file to working folder.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateProgressBar() {
        if (sentenceItems == null || sentenceItems.isEmpty()) {
            recordingProgressBar.setMax(1);
            recordingProgressBar.setProgress(0);
            recordingProgressTextView.setText("Progress: 0/0 Recorded");
            return;
        }

        int totalSentences = sentenceItems.size();
        int recordedSentences = 0;
        for (SentenceItem item : sentenceItems) {
            if (item.getRecordedFileUri() != null) {
                recordedSentences++;
            }
        }

        recordingProgressBar.setMax(totalSentences);
        recordingProgressBar.setProgress(recordedSentences);
        recordingProgressTextView.setText(String.format(Locale.US, "Progress: %d/%d Recorded", recordedSentences, totalSentences));
    }

    private void updateButtonStates() {
        boolean hasSentences = sentenceItems != null && !sentenceItems.isEmpty();
        boolean isSentenceSelected = currentSentenceIndex != -1 && hasSentences;
        boolean hasRecordingForSelectedSentence = isSentenceSelected && sentenceItems.get(currentSentenceIndex).getRecordedFileUri() != null;

        btnStartProcessing.setText(isRecording ? "Stop Recording" : "Start Recording");
        btnStartProcessing.setBackgroundTintList(ContextCompat.getColorStateList(this, isRecording ? R.color.custom_red : R.color.custom_green));
        btnStartProcessing.setEnabled(hasSentences); // Can only start recording if sentences are loaded

        btnDeleteFile.setEnabled(hasRecordingForSelectedSentence && !isRecording && !isPlaying);
        btnPlayAudio.setEnabled(hasRecordingForSelectedSentence && !isRecording && !isPlaying);
        btnNextItem.setEnabled(hasSentences && !isRecording && !isPlaying);
        btnSaveSession.setEnabled(hasSentences && currentUserId != null); // Can save if sentences are loaded and authenticated
        btnLoadSession.setEnabled(currentUserId != null); // Can load if authenticated
        btnExitActivity.setEnabled(true); // Always allow exiting
    }

    @Override
    public void onSaveAndExit() {
        // User chose to save and exit
        saveSessionState(currentSessionId != null ? currentSessionId : "Session_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
        finish(); // Exit the activity
    }

    @Override
    public void onContinueRecording() {
        // User chose to continue, dialog is dismissed automatically
        Toast.makeText(this, "Continuing session.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onExitWithoutSaving() {
        // User chose to exit without saving
        Toast.makeText(this, "Exiting without saving session state.", Toast.LENGTH_SHORT).show();
        finish(); // Exit the activity
    }

    private void showExitConfirmationDialog() {
        ExitConfirmationDialogFragment dialog = new ExitConfirmationDialogFragment();
        dialog.show(getSupportFragmentManager(), "ExitConfirmationDialog");
    }
}
