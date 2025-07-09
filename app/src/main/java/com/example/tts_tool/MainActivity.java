package com.example.tts_tool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_FIRST_LAUNCH = "firstLaunch";

    private EditText usernameEditText;
    private Button selectFileButton;
    private Button selectFolderButton;
    private Button startProcessingButton;

    private Uri selectedFileUri;
    private Uri selectedFolderUri;

    // ActivityResultLauncher for selecting a text file
    private ActivityResultLauncher<String[]> selectFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    Log.d(TAG, "Selected file URI: " + selectedFileUri.toString());
                    Toast.makeText(this, "File selected: " + getFileName(selectedFileUri), Toast.LENGTH_SHORT).show();
                    updateStartButtonState();
                } else {
                    Toast.makeText(this, "File selection cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    // ActivityResultLauncher for selecting a folder (directory)
    private ActivityResultLauncher<Uri> selectFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    selectedFolderUri = uri;
                    // Persist read/write permissions for the selected folder
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(selectedFolderUri, takeFlags);
                    Log.d(TAG, "Selected folder URI: " + selectedFolderUri.toString());
                    Toast.makeText(this, "Folder selected: " + getFolderName(selectedFolderUri), Toast.LENGTH_SHORT).show();
                    updateStartButtonState();
                } else {
                    Toast.makeText(this, "Folder selection cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);

        if (isFirstLaunch) {
            // Mark app as launched for the first time
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
            setContentView(R.layout.activity_main); // Show the original main activity
            initializeUI();
        } else {
            // Not the first launch, go to ExploreActivityPage
            Intent intent = new Intent(MainActivity.this, ExploreActivityPage.class);
            startActivity(intent);
            finish(); // Finish MainActivity so user can't go back to it
        }
    }

    private void initializeUI() {
        usernameEditText = findViewById(R.id.username_edit_text);
        selectFileButton = findViewById(R.id.select_file_button);
        selectFolderButton = findViewById(R.id.select_folder_button);
        startProcessingButton = findViewById(R.id.start_processing_button);

        selectFileButton.setOnClickListener(v -> selectFileLauncher.launch(new String[]{"text/plain"}));
        selectFolderButton.setOnClickListener(v -> selectFolderLauncher.launch(null));
        startProcessingButton.setOnClickListener(v -> startProcessingActivity());

        // Add a TextWatcher to usernameEditText to update button state
        usernameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateStartButtonState();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        updateStartButtonState(); // Initial state update
    }

    private void updateStartButtonState() {
        boolean isUsernameEntered = usernameEditText != null && !usernameEditText.getText().toString().trim().isEmpty();
        boolean isFileSelected = selectedFileUri != null;
        boolean isFolderSelected = selectedFolderUri != null;

        if (startProcessingButton != null) {
            startProcessingButton.setEnabled(isUsernameEntered && isFileSelected && isFolderSelected);
        }
    }

    private void startProcessingActivity() {
        String username = usernameEditText.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "Please enter your username.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a text file.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedFolderUri == null) {
            Toast.makeText(this, "Please select a working folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(MainActivity.this, ProcessingActivity.class);
        intent.putExtra("username", username);
        intent.setData(selectedFileUri); // Pass the text file URI
        intent.putExtra("root_folder_uri", selectedFolderUri.toString()); // Pass the folder URI as string
        startActivity(intent);
        // Do not finish MainActivity here, so user can go back to it if needed
        // (e.g., if they want to start another new session from ExploreActivityPage)
    }

    private String getFileName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        return documentFile != null ? documentFile.getName() : "Unknown File";
    }

    private String getFolderName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
        return documentFile != null ? documentFile.getName() : "Unknown Folder";
    }
}
