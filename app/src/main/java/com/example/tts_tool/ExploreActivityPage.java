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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

// ExploreActivityPage now implements InputSelectionDialogFragment.InputSelectionListener
// AND LoadSessionDialogFragment.OnSessionSelectedListener
public class ExploreActivityPage extends AppCompatActivity implements
        InputSelectionDialogFragment.InputSelectionListener,
        LoadSessionDialogFragment.OnSessionSelectedListener { // Added listener for LoadSessionDialogFragment

    private static final String TAG = "ExploreActivityPage";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String SESSION_KEY = "currentSession"; // This key is for local storage, not directly used for Firestore check

    private Button btnStartNewSession;
    private Button btnLoadSavedSession;
    private TextView tvNoSavedSessionHint;

    private SharedPreferences sharedPreferences;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_page);

        // Initialize SharedPreferences and Gson
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();

        btnStartNewSession = findViewById(R.id.btn_start_new_session);
        btnLoadSavedSession = findViewById(R.id.btn_load_saved_session);
        tvNoSavedSessionHint = findViewById(R.id.tv_no_saved_session_hint);

        // Handle Android Back button press to simply finish this activity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "Back button pressed on ExploreActivityPage. Finishing activity.");
                finish(); // Exit the app or go to the device's home screen
            }
        });

        btnStartNewSession.setOnClickListener(v -> {
            // Show the InputSelectionDialogFragment to get username and file/folder
            InputSelectionDialogFragment dialogFragment = new InputSelectionDialogFragment();
            dialogFragment.show(getSupportFragmentManager(), "InputSelectionDialog");
        });

        btnLoadSavedSession.setOnClickListener(v -> {
            // Show the LoadSessionDialogFragment
            LoadSessionDialogFragment loadDialog = new LoadSessionDialogFragment();
            loadDialog.show(getSupportFragmentManager(), "LoadSessionDialog");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check for saved session availability (from Firestore) whenever this activity resumes
        // This check is now more conceptual, as the LoadSessionDialogFragment will handle the actual fetching
        // We'll enable the button if Firebase auth is ready, assuming sessions *might* exist.
        // A more robust check would involve a quick Firestore query here, but that might be overkill for UI enablement.
        // For simplicity, we'll enable the button and let the dialog handle "no sessions found".
        btnLoadSavedSession.setEnabled(true); // Always enable, let dialog show "no sessions"
        tvNoSavedSessionHint.setVisibility(View.GONE); // Hide hint, as load button is always enabled
    }

    /**
     * Callback from InputSelectionDialogFragment when username and file/folder are selected.
     * This method launches the ProcessingActivity for a new session.
     */
    @Override
    public void onInputSelected(String username, Uri fileUri, Uri folderUri) {
        if (username != null && !username.isEmpty() && fileUri != null && folderUri != null) {
            Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
            intent.putExtra("username", username);
            intent.setData(fileUri); // Pass the selected text file URI as Intent data
            intent.putExtra("root_folder_uri", folderUri.toString()); // Pass the root folder URI as string
            startActivity(intent);
        } else {
            Toast.makeText(this, "Invalid input received from dialog. Please select all fields.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Callback from LoadSessionDialogFragment when a session is selected.
     * This method launches the ProcessingActivity to load the selected session.
     */
    @Override
    public void onSessionSelected(ProcessingActivity.SessionState sessionState) {
        if (sessionState != null && sessionState.getSessionId() != null) {
            Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
            // Pass necessary data to ProcessingActivity to load the session
            intent.putExtra("session_id", sessionState.getSessionId());
            // You might also pass other relevant data if ProcessingActivity needs it before loading from Firestore
            // e.g., intent.putExtra("username", sessionState.getUsername());
            // However, ProcessingActivity's loadSessionState method already fetches everything from Firestore.
            startActivity(intent);
        } else {
            Toast.makeText(this, "Failed to load session: Invalid session data.", Toast.LENGTH_SHORT).show();
        }
    }
}
