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
import android.widget.ProgressBar; // Added for loading indicator

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Implement the OnSessionSelectedListener interface
public class ExploreActivityPage extends AppCompatActivity implements LoadSessionDialogFragment.OnSessionSelectedListener {

    private static final String TAG = "ExploreActivityPage";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri";
    public static final String EXTRA_IS_NEW_SESSION = "is_new_session"; // New extra for MainActivity

    private Button btnStartNewSession;
    private Button btnLoadSavedSession;
    private Button btnViewFilesInWorkspace;
    private TextView tvNoSavedSessionHint;
    private ProgressBar authProgressBar; // New ProgressBar for auth status

    private Uri selectedWorkingFolderUri; // This will hold the URI of the chosen workspace
    private FirebaseAuth mAuth; // Firebase Auth instance
    private String currentUserId; // Current authenticated user ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_page);

        // Initialize Firebase (ensure this is done before any Firebase calls)
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        btnStartNewSession = findViewById(R.id.btn_start_new_session);
        btnLoadSavedSession = findViewById(R.id.btn_load_saved_session);
        btnViewFilesInWorkspace = findViewById(R.id.btn_view_files_in_workspace);
        tvNoSavedSessionHint = findViewById(R.id.tv_no_saved_session_hint);
        // Assuming you add a ProgressBar with this ID in activity_explore_page.xml
        // If not, you can remove this line or add the ProgressBar.
        authProgressBar = findViewById(R.id.auth_progress_bar);


        // Authenticate anonymously immediately
        authenticateAnonymously();

        // Set up click listeners
        btnStartNewSession.setOnClickListener(v -> {
            Log.d(TAG, "Start New Session button clicked.");
            // Always go to MainActivity to select a new input file and confirm/re-select workspace
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
                Toast.makeText(this, "No workspace folder selected to load sessions from. Please select one in the main setup screen first.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Load Saved Session button clicked. Launching LoadSessionDialogFragment.");
            LoadSessionDialogFragment loadDialog = LoadSessionDialogFragment.newInstance(currentUserId); // Use the newInstance method
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });

        btnViewFilesInWorkspace.setOnClickListener(v -> {
            if (selectedWorkingFolderUri != null) {
                Log.d(TAG, "View Files in Workspace button clicked. Launching FileManagerActivity.");
                Intent intent = new Intent(ExploreActivityPage.this, FileManagerActivity.class);
                intent.putExtra("working_folder_uri", selectedWorkingFolderUri.toString());
                startActivity(intent);
            } else {
                Toast.makeText(ExploreActivityPage.this, "No workspace folder selected. Please select one in the main setup screen first.", Toast.LENGTH_LONG).show();
            }
        });

        // Initial update of button states (will be updated again after auth completes)
        updateButtonStates();
    }

    private void authenticateAnonymously() {
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "Attempting anonymous Firebase authentication in ExploreActivityPage...");
            if (authProgressBar != null) authProgressBar.setVisibility(View.VISIBLE); // Show loading indicator
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (authProgressBar != null) authProgressBar.setVisibility(View.GONE); // Hide loading indicator
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
                                currentUserId = null; // Ensure userId is null on failure
                            }
                            updateButtonStates(); // Update buttons after auth attempt
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
        // Re-check the working folder URI in onResume in case it was set/cleared by MainActivity
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUriString = sharedPreferences.getString(KEY_SAVED_WORKING_FOLDER_URI, null);
        if (savedUriString != null) {
            selectedWorkingFolderUri = Uri.parse(savedUriString);
            try {
                getContentResolver().takePersistableUriPermission(selectedWorkingFolderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.e(TAG, "Permissions lost for saved working folder URI on resume: " + e.getMessage());
                selectedWorkingFolderUri = null;
            }
        } else {
            selectedWorkingFolderUri = null;
        }
        // Also re-check auth state in onResume
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
        } else {
            currentUserId = null; // Ensure currentUserId is null if not authenticated
            authenticateAnonymously(); // Attempt to re-authenticate if needed
        }
        updateButtonStates();
        // Initial navigation based on workspace availability
        handleInitialNavigation();
    }

    private void handleInitialNavigation() {
        if (selectedWorkingFolderUri == null) {
            // If no workspace is selected, automatically go to MainActivity for setup
            Log.d(TAG, "No workspace selected, launching MainActivity for setup.");
            Intent intent = new Intent(ExploreActivityPage.this, MainActivity.class);
            // Use FLAG_ACTIVITY_CLEAR_TOP to clear any activities above MainActivity if it's already in the stack
            // and FLAG_ACTIVITY_NEW_TASK if MainActivity is not the root of the task (e.g., if ExploreActivityPage is launcher)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Finish ExploreActivityPage if we're redirecting
        } else {
            Log.d(TAG, "Workspace selected: " + selectedWorkingFolderUri.toString() + ". Staying on ExploreActivityPage.");
            // If a workspace is selected, stay on this page and enable relevant buttons
            // Buttons are enabled by updateButtonStates() which is called after auth.
            tvNoSavedSessionHint.setVisibility(View.GONE); // Hide hint if workspace is selected
        }
    }


    private void updateButtonStates() {
        boolean isWorkingFolderSelected = (selectedWorkingFolderUri != null);
        boolean isAuthenticated = (currentUserId != null);

        btnViewFilesInWorkspace.setEnabled(isWorkingFolderSelected && isAuthenticated);
        btnLoadSavedSession.setEnabled(isWorkingFolderSelected && isAuthenticated);

        // Show/hide hint based on workspace selection
        if (isWorkingFolderSelected) {
            tvNoSavedSessionHint.setVisibility(View.GONE);
        } else {
            tvNoSavedSessionHint.setVisibility(View.VISIBLE);
        }
    }

    // --- NEW: Implementation of OnSessionSelectedListener ---
    @Override
    public void onSessionSelected(ProcessingActivity.SessionState sessionState) {
        if (sessionState != null && sessionState.getSessionId() != null) {
            Log.d(TAG, "onSessionSelected: Session selected from dialog: " + sessionState.getSessionId());

            // Launch ProcessingActivity with the selected session details
            Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
            // Pass the session ID to ProcessingActivity so it can load the specific session
            intent.putExtra("session_id", sessionState.getSessionId());
            // Also pass the initial setup data in case ProcessingActivity needs it for context
            intent.putExtra("username", sessionState.getUsername());
            intent.putExtra("root_folder_uri", sessionState.getRootFolderUriString());
            // If originalInputFileUri is crucial for ProcessingActivity's setup, pass it too
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
