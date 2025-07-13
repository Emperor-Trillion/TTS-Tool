// app/src/main/java/com/example/tts_tool/MainActivity.java
package com.example.tts_tool;

import android.content.Intent;
import android.content.SharedPreferences; // Keep for input file uri persistence if needed
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

// Removed unused imports from the previous version of MainActivity related to folder selection
// import java.text.SimpleDateFormat;
// import java.util.Date;
// import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "TTSRecorderPrefs"; // Still used for KEY_SAVED_WORKING_FOLDER_URI if you decide to save input file URI here
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri"; // This key is now primarily managed by ExploreActivityPage
    private static final String KEY_WORKING_FOLDER_SELECTED_ONCE = "workingFolderSelectedOnce"; // This key is now primarily managed by ExploreActivityPage

    private EditText usernameEditText;
    private Button btnSelectInputFile;
    private TextView tvSelectedInputFileName;
    // private Button btnSelectWorkingFolder; // REMOVE THIS BUTTON DECLARATION
    private Button btnStartProcessing;

    private Uri selectedInputFileUri;
    private Uri rootFolderUriFromExploreActivity; // This will hold the URI passed from ExploreActivityPage
    private ActivityResultLauncher<String[]> openDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameEditText = findViewById(R.id.username_edit_text);
        btnSelectInputFile = findViewById(R.id.btn_select_input_file);
        tvSelectedInputFileName = findViewById(R.id.tv_selected_input_file_name);
        // btnSelectWorkingFolder = findViewById(R.id.btn_select_working_folder); // REMOVE THIS FINDVIEWBYID
        btnStartProcessing = findViewById(R.id.btn_start_processing);

        Log.d(TAG, "MainActivity launched.");

        // Get the root folder URI passed from ExploreActivityPage
        Intent intent = getIntent();
        String rootFolderUriString = intent.getStringExtra("root_folder_uri");
        if (rootFolderUriString != null) {
            rootFolderUriFromExploreActivity = Uri.parse(rootFolderUriString);
            Log.d(TAG, "Received root folder URI from ExploreActivityPage: " + rootFolderUriFromExploreActivity.toString());
            // You can optionally display this somewhere or use it to inform the user
        } else {
            // This case should ideally not happen if ExploreActivityPage always passes the URI
            Log.e(TAG, "No root folder URI received from ExploreActivityPage!");
            Toast.makeText(this, "Error: No working folder provided.", Toast.LENGTH_LONG).show();
            finish(); // Consider finishing if essential URI is missing
            return;
        }

        // REMOVE ALL THE SHARED PREFERENCES LOGIC FOR KEY_SAVED_WORKING_FOLDER_URI AND KEY_WORKING_FOLDER_SELECTED_ONCE
        // This is now managed by ExploreActivityPage
        /*
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
        */

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

        // REMOVE THE OPEN DIRECTORY LAUNCHER REGISTRATION AND ITS CALLBACK
        /*
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
        */

        btnSelectInputFile.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"text/plain"}));
        // REMOVE THE ONCLICKLISTENER FOR btnSelectWorkingFolder
        // btnSelectWorkingFolder.setOnClickListener(v -> openDirectoryLauncher.launch(null));

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int intbefore, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                updateButtonStates();
            }
        });

        btnStartProcessing.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter a speaker name.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedInputFileUri == null) {
                Toast.makeText(MainActivity.this, "Please select an input text file.", Toast.LENGTH_SHORT).show();
                return;
            }
            // The working folder is now guaranteed to be non-null if we reach here
            // as it's passed from ExploreActivityPage.
            if (rootFolderUriFromExploreActivity == null) {
                Toast.makeText(MainActivity.this, "Error: Working folder not set.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent newIntent = new Intent(MainActivity.this, ProcessingActivity.class);
            newIntent.putExtra("username", username);
            newIntent.setData(selectedInputFileUri);
            newIntent.putExtra("root_folder_uri", rootFolderUriFromExploreActivity.toString()); // Use the received URI
            startActivity(newIntent);
            finish();
        });

        tvSelectedInputFileName.setText("No file selected");
        tvSelectedInputFileName.setVisibility(View.GONE);

        updateButtonStates();

        Log.d(TAG, "Initial button states after onCreate:");
        Log.d(TAG, "  btnStartProcessing.isEnabled(): " + btnStartProcessing.isEnabled());
        Log.d(TAG, "  usernameEditText.getText().isEmpty(): " + usernameEditText.getText().toString().trim().isEmpty());
        Log.d(TAG, "  selectedInputFileUri is null: " + (selectedInputFileUri == null));
        Log.d(TAG, "  rootFolderUriFromExploreActivity is null: " + (rootFolderUriFromExploreActivity == null));
    }

    private void updateButtonStates() {
        boolean isUsernameEntered = !usernameEditText.getText().toString().trim().isEmpty();
        boolean isInputFileSelected = selectedInputFileUri != null;
        boolean isWorkingFolderProvided = rootFolderUriFromExploreActivity != null; // Check if received

        btnStartProcessing.setEnabled(isUsernameEntered && isInputFileSelected && isWorkingFolderProvided);
    }

    private String getFileName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        return documentFile != null ? documentFile.getName() : "Unknown File";
    }

    // REMOVE THIS METHOD, IT'S NO LONGER NEEDED IN MAINACTIVITY
    /*
    private String getFolderName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
        return documentFile != null ? documentFile.getName() : "Unknown Folder";
    }
    */
}