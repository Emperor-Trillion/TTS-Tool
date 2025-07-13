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

import androidx.activity.result.ActivityResultLauncher; // New import
import androidx.activity.result.contract.ActivityResultContracts; // New import
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.provider.DocumentsContract;

public class ExploreActivityPage extends AppCompatActivity implements LoadSessionDialogFragment.OnSessionSelectedListener {

    private static final String TAG = "ExploreActivityPage";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri";
    public static final String EXTRA_IS_NEW_SESSION = "is_new_session";

    private Button btnLoadSavedSession;
    private Button btnViewFilesInWorkspace;
    private TextView tvNoSavedSessionHint;
    private ProgressBar authProgressBar;
    private Button btnStartNewSession; // Declare here to access in folder selection logic

    private Uri selectedWorkingFolderUri;
    private FirebaseAuth mAuth;
    private String currentUserId;

    // ActivityResultLauncher for selecting a directory
    private ActivityResultLauncher<Uri> openDirectoryLauncher; // Changed to accept Uri for initial_uri

    // Flag to indicate if folder selection is for starting a new session
    private boolean isSelectingFolderForNewSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_page);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        btnStartNewSession = findViewById(R.id.btn_start_new_session);
        btnLoadSavedSession = findViewById(R.id.btn_load_saved_session);
        btnViewFilesInWorkspace = findViewById(R.id.btn_view_files_in_workspace);
        tvNoSavedSessionHint = findViewById(R.id.tv_no_saved_session_hint);
        authProgressBar = findViewById(R.id.auth_progress_bar);

        authenticateAnonymously();

        // Initialize the ActivityResultLauncher for selecting a directory
        openDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                selectedWorkingFolderUri = uri;
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    sharedPreferences.edit().putString(KEY_SAVED_WORKING_FOLDER_URI, uri.toString()).apply();
                    Toast.makeText(ExploreActivityPage.this, "Working folder selected: " + getFolderName(uri), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Selected and persisted working folder URI: " + uri.toString());

                    // If the folder selection was initiated by "Start New Session"
                    if (isSelectingFolderForNewSession) {
                        isSelectingFolderForNewSession = false; // Reset the flag
                        startNewSessionWithFolder(uri); // Proceed to start new session
                    }

                } catch (SecurityException e) {
                    Log.e(TAG, "Permissions denied for selected working folder: " + e.getMessage());
                    Toast.makeText(ExploreActivityPage.this, "Permission denied for selected folder. Please try again.", Toast.LENGTH_LONG).show();
                    selectedWorkingFolderUri = null; // Clear if permission is denied
                }
            } else {
                Toast.makeText(ExploreActivityPage.this, "No working folder selected.", Toast.LENGTH_SHORT).show();
                selectedWorkingFolderUri = null; // Clear if no folder is selected
            }
            updateButtonStates();
        });


        btnStartNewSession.setOnClickListener(v -> {
            Log.d(TAG, "Start New Session button clicked.");
            if (selectedWorkingFolderUri == null) {
                // If no folder is selected, prompt user to select one first
                Toast.makeText(ExploreActivityPage.this, "Please select a working folder first.", Toast.LENGTH_LONG).show();
                isSelectingFolderForNewSession = true; // Set flag
                openDirectoryLauncher.launch(null); // Launch folder picker
            } else {
                // Folder is already selected, proceed to MainActivity
                startNewSessionWithFolder(selectedWorkingFolderUri);
            }
        });

        btnLoadSavedSession.setOnClickListener(v -> {
            if (currentUserId == null) {
                Toast.makeText(this, "Authentication not complete. Please wait or restart.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Attempted to load session before authentication was complete.");
                return;
            }
            if (selectedWorkingFolderUri == null) {
                // This scenario should ideally not happen if handleInitialNavigation() works correctly
                Toast.makeText(this, "No workspace folder selected to load sessions from. Please select one first.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Load Saved Session button clicked. Launching LoadSessionDialogFragment.");
            LoadSessionDialogFragment loadDialog = LoadSessionDialogFragment.newInstance(currentUserId);
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });

        btnViewFilesInWorkspace.setOnClickListener(v -> {
            if (selectedWorkingFolderUri != null) {
                Log.d(TAG, "View Files in Workspace button clicked. Launching system file picker for: " + selectedWorkingFolderUri.toString());
                openSystemDocumentTree(selectedWorkingFolderUri); // Use a new method for viewing
            } else {
                // If no folder is selected, prompt user to select one
                Toast.makeText(ExploreActivityPage.this, "Please select a working folder first to view files.", Toast.LENGTH_LONG).show();
                isSelectingFolderForNewSession = false; // Ensure this flag is false for viewing
                openDirectoryLauncher.launch(null); // Launch folder picker
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
                getContentResolver().takePersistableUriPermission(tempUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                DocumentFile folder = DocumentFile.fromTreeUri(this, tempUri);
                if (folder != null && folder.exists() && folder.isDirectory()) {
                    selectedWorkingFolderUri = tempUri;
                    Log.d(TAG, "onResume: Successfully loaded and verified saved working folder URI: " + selectedWorkingFolderUri.toString());
                } else {
                    Log.w(TAG, "onResume: Saved working folder URI is no longer valid or accessible (folder not found/is not directory): " + tempUri.toString());
                    selectedWorkingFolderUri = null;
                    sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
                    Toast.makeText(this, "Previously selected workspace is no longer accessible. Please re-select it.", Toast.LENGTH_LONG).show();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "onResume: Permissions lost for saved working folder URI: " + e.getMessage());
                selectedWorkingFolderUri = null;
                sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
                Toast.makeText(this, "Lost access to previously selected workspace. Please select it again.", Toast.LENGTH_LONG).show();
            } catch (IllegalArgumentException e) {
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
        // The initial navigation logic from MainActivity is now handled here.
        // We will no longer force a redirect to MainActivity if no workspace is selected.
        // Instead, the "Start New Session" and "View Files in Workspace" buttons
        // will prompt for folder selection if needed.
    }

    private void startNewSessionWithFolder(Uri folderUri) {
        Intent intent = new Intent(ExploreActivityPage.this, MainActivity.class);
        intent.putExtra("root_folder_uri", folderUri.toString()); // Pass the URI to MainActivity
        // No need for EXTRA_IS_NEW_SESSION here, MainActivity will handle the setup.
        startActivity(intent);
    }

    private void updateButtonStates() {
        boolean isWorkingFolderSelected = (selectedWorkingFolderUri != null);
        boolean isAuthenticated = (currentUserId != null);

        // Enable/disable buttons based on whether a working folder is selected and authenticated
        // The "Start New Session" button should always be enabled, it will handle folder selection if needed
        btnStartNewSession.setEnabled(true);

        btnLoadSavedSession.setEnabled(isWorkingFolderSelected && isAuthenticated);
        btnViewFilesInWorkspace.setEnabled(isWorkingFolderSelected && isAuthenticated);

        if (isWorkingFolderSelected) {
            tvNoSavedSessionHint.setVisibility(View.GONE);
        } else {
            tvNoSavedSessionHint.setVisibility(View.VISIBLE);
        }
    }

    // New method to open the system file picker to view the contents of the selected folder
    private void openSystemDocumentTree(Uri treeUri) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Attempt to open the picker at the already selected URI
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }
        try {
            // Using startActivity, not startActivityForResult, as we don't need a result back from viewing.
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file manager found on device that can handle this request.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No Activity found to handle ACTION_OPEN_DOCUMENT_TREE: " + e.getMessage());
        }
    }

    // Removed onActivityResult as we are now using ActivityResultLauncher
    /*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // This method is no longer needed for folder selection with ActivityResultLauncher
    }
    */

    private String getFolderName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
        return documentFile != null ? documentFile.getName() : "Unknown Folder";
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