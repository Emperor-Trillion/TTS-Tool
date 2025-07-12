// app/src/main/java/com/example/tts_tool/ExploreActivityPage.java
package com.example.tts_tool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile; // Added for robust URI validation

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.provider.DocumentsContract; // New import for SAF operations

// Implement the OnSessionSelectedListener interface
public class ExploreActivityPage extends AppCompatActivity implements LoadSessionDialogFragment.OnSessionSelectedListener {

    private static final String TAG = "ExploreActivityPage";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri";
    public static final String EXTRA_IS_NEW_SESSION = "is_new_session";

    private Button btnLoadSavedSession;
    private Button btnViewFilesInWorkspace;
    private TextView tvNoSavedSessionHint;
    private ProgressBar authProgressBar;

    private Uri selectedWorkingFolderUri;
    private FirebaseAuth mAuth;
    private String currentUserId;

    private static final int OPEN_DOCUMENT_TREE_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_page);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        Button btnStartNewSession = findViewById(R.id.btn_start_new_session);
        btnLoadSavedSession = findViewById(R.id.btn_load_saved_session);
        btnViewFilesInWorkspace = findViewById(R.id.btn_view_files_in_workspace);
        tvNoSavedSessionHint = findViewById(R.id.tv_no_saved_session_hint);
        authProgressBar = findViewById(R.id.auth_progress_bar);

        authenticateAnonymously();

        btnStartNewSession.setOnClickListener(v -> {
            Log.d(TAG, "Start New Session button clicked.");
            Intent intent = new Intent(ExploreActivityPage.this, MainActivity.class);
            startActivity(intent);
        });

        btnLoadSavedSession.setOnClickListener(v -> {
            if (currentUserId == null) {
                Toast.makeText(this, "Authentication not complete. Please wait or restart.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Attempted to load session before authentication was complete.");
                return;
            }
            if (selectedWorkingFolderUri == null) {
                // This scenario should be rare if handleInitialNavigation() works correctly
                Toast.makeText(this, "No workspace folder selected to load sessions from. Please select one in the main setup screen first.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Load Saved Session button clicked. Launching LoadSessionDialogFragment.");
            LoadSessionDialogFragment loadDialog = LoadSessionDialogFragment.newInstance(currentUserId);
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });

        btnViewFilesInWorkspace.setOnClickListener(v -> {
            if (selectedWorkingFolderUri != null) {
                Log.d(TAG, "View Files in Workspace button clicked. Launching system file picker for: " + selectedWorkingFolderUri.toString());
                openDocumentTree(selectedWorkingFolderUri);
            } else {
                // This scenario should be rare if handleInitialNavigation() works correctly
                Toast.makeText(ExploreActivityPage.this, "No workspace folder selected. Please select one in the main setup screen first.", Toast.LENGTH_LONG).show();
                // Optionally, redirect to MainActivity here if selectedWorkingFolderUri is null.
                // However, handleInitialNavigation() should catch this on resume.
                handleInitialNavigation();
            }
        });

        updateButtonStates();
    }

    private void authenticateAnonymously() {
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "Attempting anonymous Firebase authentication in ExploreActivityPage...");
            if (authProgressBar != null) authProgressBar.setVisibility(View.VISIBLE);
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (authProgressBar != null) authProgressBar.setVisibility(View.GONE);
                            if (task.isSuccessful()) {
                                Log.d(TAG, "signInAnonymously in ExploreActivityPage:success");
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    currentUserId = user.getUid();
                                    Log.d(TAG, "Authenticated with UID: " + currentUserId);
                                } else {
                                    Log.e(TAG, "signInAnonymously in ExploreActivityPage:success but current user is null.");
                                }
                            } else {
                                Log.e(TAG, "signInAnonymously in ExploreActivityPage:failure", task.getException());
                                Toast.makeText(ExploreActivityPage.this, "Authentication failed. Cannot load/manage sessions.", Toast.LENGTH_LONG).show();
                                currentUserId = null;
                            }
                            updateButtonStates();
                        }
                    });
        } else {
            currentUserId = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Already authenticated with UID: " + currentUserId);
            updateButtonStates();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUriString = sharedPreferences.getString(KEY_SAVED_WORKING_FOLDER_URI, null);

        if (savedUriString != null) {
            Uri tempUri = Uri.parse(savedUriString);
            try {
                // Always try to re-take persistable URI permission on resume
                // This is crucial to maintain access across app restarts and device reboots.
                getContentResolver().takePersistableUriPermission(tempUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Use DocumentFile.fromTreeUri to actually check if the URI is still valid and accessible
                // This is a more robust check than just takePersistableUriPermission for validity
                DocumentFile folder = DocumentFile.fromTreeUri(this, tempUri);
                if (folder != null && folder.exists() && folder.isDirectory()) {
                    selectedWorkingFolderUri = tempUri;
                    Log.d(TAG, "onResume: Successfully loaded and verified saved working folder URI: " + selectedWorkingFolderUri.toString());
                } else {
                    // Folder no longer exists or is not a directory
                    Log.w(TAG, "onResume: Saved working folder URI is no longer valid or accessible (folder not found/is not directory): " + tempUri.toString());
                    selectedWorkingFolderUri = null;
                    sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
                    Toast.makeText(this, "Previously selected workspace is no longer accessible. Please re-select it.", Toast.LENGTH_LONG).show();
                }
            } catch (SecurityException e) {
                // Permissions were explicitly revoked by the user or system
                Log.e(TAG, "onResume: Permissions lost for saved working folder URI: " + e.getMessage());
                selectedWorkingFolderUri = null;
                sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
                Toast.makeText(this, "Lost access to previously selected workspace. Please select it again.", Toast.LENGTH_LONG).show();
            } catch (IllegalArgumentException e) {
                // Catch cases where Uri.parse might be invalid or DocumentFile creation fails
                Log.e(TAG, "onResume: Invalid URI format or DocumentFile creation failed: " + e.getMessage());
                selectedWorkingFolderUri = null;
                sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
            }
        } else {
            selectedWorkingFolderUri = null;
            Log.d(TAG, "onResume: No saved working folder URI found.");
        }

        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
        } else {
            currentUserId = null;
            authenticateAnonymously();
        }
        updateButtonStates();
        handleInitialNavigation();
    }

    private void handleInitialNavigation() {
        if (selectedWorkingFolderUri == null) {
            Log.d(TAG, "No workspace selected, launching MainActivity for setup.");
            Intent intent = new Intent(ExploreActivityPage.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Log.d(TAG, "Workspace selected: " + selectedWorkingFolderUri.toString() + ". Staying on ExploreActivityPage.");
            tvNoSavedSessionHint.setVisibility(View.GONE);
        }
    }


    private void updateButtonStates() {
        boolean isWorkingFolderSelected = (selectedWorkingFolderUri != null);
        boolean isAuthenticated = (currentUserId != null);

        btnViewFilesInWorkspace.setEnabled(isWorkingFolderSelected && isAuthenticated);
        btnLoadSavedSession.setEnabled(isWorkingFolderSelected && isAuthenticated);

        if (isWorkingFolderSelected) {
            tvNoSavedSessionHint.setVisibility(View.GONE);
        } else {
            tvNoSavedSessionHint.setVisibility(View.VISIBLE);
        }
    }

    private void openDocumentTree(Uri treeUri) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }
        try {
            startActivityForResult(intent, OPEN_DOCUMENT_TREE_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file manager found on device that can handle this request.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No Activity found to handle ACTION_OPEN_DOCUMENT_TREE: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OPEN_DOCUMENT_TREE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri treeUri = data.getData();
                if (treeUri != null) {
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    try {
                        // Re-persist permissions for the URI returned by the picker.
                        // This is important even if it's the same URI, to ensure continuous access.
                        getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Log.d(TAG, "Re-persisted URI permissions for " + treeUri + " after user interaction.");
                        Toast.makeText(this, "Opened workspace: " + (treeUri.getLastPathSegment() != null ? treeUri.getLastPathSegment() : "Selected Folder"), Toast.LENGTH_SHORT).show();

                        // IMPORTANT: For "View Files in Workspace", the intent is to open the *already set* workspace.
                        // The user might have navigated or confirmed the same folder in the system picker.
                        // We do NOT change `selectedWorkingFolderUri` here or save it to SharedPreferences,
                        // as this button's role is not to *re-select* the app's primary workspace,
                        // but to *view* the one already established.
                        // The `selectedWorkingFolderUri` should retain the value loaded in onResume.

                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to take persistable URI permission for " + treeUri + " after browsing. " + e.getMessage());
                        Toast.makeText(this, "Permission denied for selected folder.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    @Override
    public void onSessionSelected(ProcessingActivity.SessionState sessionState) {
        if (sessionState != null && sessionState.getSessionId() != null) {
            Log.d(TAG, "onSessionSelected: Session selected from dialog: " + sessionState.getSessionId());

            Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
            intent.putExtra("session_id", sessionState.getSessionId());
            intent.putExtra("username", sessionState.getUsername());
            intent.putExtra("root_folder_uri", sessionState.getRootFolderUriString());
            if (sessionState.getOriginalInputFileUriString() != null) {
                intent.setData(Uri.parse(sessionState.getOriginalInputFileUriString()));
            }

            startActivity(intent);
        } else {
            Toast.makeText(this, "Failed to load session: Invalid session data received.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onSessionSelected: Invalid sessionState received from LoadSessionDialogFragment.");
        }
    }
}
