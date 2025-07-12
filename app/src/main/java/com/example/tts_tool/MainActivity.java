// app/src/main/java/com/example/tts_tool/MainActivity.java
package com.example.tts_tool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri";
    private static final String KEY_WORKING_FOLDER_SELECTED_ONCE = "workingFolderSelectedOnce";

    private EditText usernameEditText;
    private Button btnSelectInputFile;
    private TextView tvSelectedInputFileName;
    private Button btnSelectWorkingFolder;
    private Button btnStartProcessing;
    private Button btnLoadExistingSession;
    private Button btnManageFiles;

    private Uri selectedInputFileUri;
    private Uri selectedWorkingFolderUri;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<Uri> openDirectoryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameEditText = findViewById(R.id.username_edit_text);
        btnSelectInputFile = findViewById(R.id.btn_select_input_file);
        tvSelectedInputFileName = findViewById(R.id.tv_selected_input_file_name);
        btnSelectWorkingFolder = findViewById(R.id.btn_select_working_folder);
        btnStartProcessing = findViewById(R.id.btn_start_processing);
        btnLoadExistingSession = findViewById(R.id.btn_load_existing_session);
        btnManageFiles = findViewById(R.id.btn_manage_files);

        Log.d(TAG, "MainActivity launched.");

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUriString = sharedPreferences.getString(KEY_SAVED_WORKING_FOLDER_URI, null);
        boolean folderSelectedOnce = sharedPreferences.getBoolean(KEY_WORKING_FOLDER_SELECTED_ONCE, false);

        if (savedUriString != null) {
            selectedWorkingFolderUri = Uri.parse(savedUriString);
            try {
                getContentResolver().takePersistableUriPermission(selectedWorkingFolderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Log.d(TAG, "Loaded saved working folder URI: " + selectedWorkingFolderUri.toString());
                Toast.makeText(this, "Last working folder loaded. Ready for new session setup.", Toast.LENGTH_LONG).show();

                if (folderSelectedOnce) {
                    btnSelectWorkingFolder.setVisibility(View.GONE);
                    Log.d(TAG, "Working folder already selected once, hiding 'Select Working Folder' button.");
                }

            } catch (SecurityException e) {
                Log.e(TAG, "Permissions lost for saved working folder URI: " + e.getMessage());
                selectedWorkingFolderUri = null;
                Toast.makeText(this, "Permissions lost for previous working folder. Please re-select it.", Toast.LENGTH_LONG).show();
                btnSelectWorkingFolder.setVisibility(View.VISIBLE);
                sharedPreferences.edit().putBoolean(KEY_WORKING_FOLDER_SELECTED_ONCE, false).apply();
            }
        } else {
            Log.d(TAG, "No saved working folder URI found. User needs to select one.");
            Toast.makeText(this, "Please select a working folder.", Toast.LENGTH_LONG).show();
            btnSelectWorkingFolder.setVisibility(View.VISIBLE);
        }

        openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                selectedInputFileUri = uri;
                String fileName = getFileName(uri);
                Toast.makeText(MainActivity.this, "Input file selected: " + fileName, Toast.LENGTH_SHORT).show();
                tvSelectedInputFileName.setText("Selected File: " + fileName);
                tvSelectedInputFileName.setVisibility(View.VISIBLE);
                Log.d(TAG, "Selected input file URI: " + uri.toString());
            } else {
                selectedInputFileUri = null;
                Toast.makeText(MainActivity.this, "No input file selected.", Toast.LENGTH_SHORT).show();
                tvSelectedInputFileName.setText("No file selected");
                tvSelectedInputFileName.setVisibility(View.GONE);
            }
            updateButtonStates();
        });

        openDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                selectedWorkingFolderUri = uri;
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, takeFlags);

                sharedPreferences.edit().putString(KEY_SAVED_WORKING_FOLDER_URI, uri.toString()).apply();
                sharedPreferences.edit().putBoolean(KEY_WORKING_FOLDER_SELECTED_ONCE, true).apply();

                Toast.makeText(MainActivity.this, "Working folder selected: " + getFolderName(uri), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Selected working folder URI: " + uri.toString());

                btnSelectWorkingFolder.setVisibility(View.GONE);

            } else {
                Toast.makeText(MainActivity.this, "No working folder selected.", Toast.LENGTH_SHORT).show();
            }
            updateButtonStates();
        });

        btnSelectInputFile.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"text/plain"}));
        btnSelectWorkingFolder.setOnClickListener(v -> openDirectoryLauncher.launch(null));

        btnStartProcessing.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter a username.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedInputFileUri == null) {
                Toast.makeText(MainActivity.this, "Please select an input text file.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedWorkingFolderUri == null) {
                Toast.makeText(MainActivity.this, "Please select a working folder.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, ProcessingActivity.class);
            intent.putExtra("username", username);
            intent.setData(selectedInputFileUri);
            intent.putExtra("root_folder_uri", selectedWorkingFolderUri.toString());
            startActivity(intent);
            finish();
        });

        btnLoadExistingSession.setOnClickListener(v -> {
            Toast.makeText(this, "Please use 'Load Saved Session' from the Explore Page.", Toast.LENGTH_LONG).show();
        });

        btnManageFiles.setOnClickListener(v -> {
            Toast.makeText(this, "Please use 'View Files in Workspace' from the Explore Page.", Toast.LENGTH_LONG).show();
        });

        tvSelectedInputFileName.setText("No file selected");
        tvSelectedInputFileName.setVisibility(View.GONE);

        // Call updateButtonStates to set initial button states
        updateButtonStates();

        // --- NEW LOGGING FOR DEBUGGING ---
        Log.d(TAG, "Initial button states after onCreate:");
        Log.d(TAG, "  btnStartProcessing.isEnabled(): " + btnStartProcessing.isEnabled());
        Log.d(TAG, "  usernameEditText.getText().isEmpty(): " + usernameEditText.getText().toString().trim().isEmpty());
        Log.d(TAG, "  selectedInputFileUri is null: " + (selectedInputFileUri == null));
        Log.d(TAG, "  selectedWorkingFolderUri is null: " + (selectedWorkingFolderUri == null));
        // --- END NEW LOGGING ---
    }

    private void updateButtonStates() {
        boolean isUsernameEntered = !usernameEditText.getText().toString().trim().isEmpty();
        boolean isInputFileSelected = selectedInputFileUri != null;
        boolean isWorkingFolderSelected = selectedWorkingFolderUri != null;

        btnStartProcessing.setEnabled(isUsernameEntered && isInputFileSelected && isWorkingFolderSelected);

        btnLoadExistingSession.setEnabled(false);
        btnManageFiles.setEnabled(false);
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
