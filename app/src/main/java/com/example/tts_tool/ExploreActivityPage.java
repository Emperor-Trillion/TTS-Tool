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

// ExploreActivityPage now implements InputSelectionDialogFragment.InputSelectionListener again
public class ExploreActivityPage extends AppCompatActivity implements InputSelectionDialogFragment.InputSelectionListener {

    private static final String TAG = "ExploreActivityPage";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String SESSION_KEY = "currentSession";

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
            // Attempt to load a saved session
            loadSavedSession();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check for saved session whenever this activity resumes
        checkSavedSessionAvailability();
    }

    /**
     * Checks if a saved session exists in SharedPreferences and updates the UI accordingly.
     */
    private void checkSavedSessionAvailability() {
        String savedSessionJson = sharedPreferences.getString(SESSION_KEY, null);

        if (savedSessionJson != null && !savedSessionJson.isEmpty()) {
            btnLoadSavedSession.setEnabled(true);
            tvNoSavedSessionHint.setVisibility(View.GONE);
            Log.d(TAG, "Saved session found. Load button enabled.");
        } else {
            btnLoadSavedSession.setEnabled(false);
            tvNoSavedSessionHint.setVisibility(View.VISIBLE);
            Log.d(TAG, "No saved session found. Load button disabled.");
        }
    }

    /**
     * Launches ProcessingActivity to load a saved session.
     */
    private void loadSavedSession() {
        // Launch ProcessingActivity without specific new session data
        // ProcessingActivity will then attempt to load from SharedPreferences
        Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
        startActivity(intent);
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
}
