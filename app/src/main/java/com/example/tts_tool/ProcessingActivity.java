package com.example.tts_tool;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button; // Import Button
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

public class ProcessingActivity extends AppCompatActivity {

    private TextView usernameTextView;
    private TextView fileUriTextView;
    private TextView loadedFileNameTextView; // New ID for the loaded file name
    private TextView fileContentTextView;
    private Button btnStartProcessing; // New ID
    private Button btnDeleteFile;      // New ID
    private Button btnPlayAudio;       // New ID
    private Button btnNextItem;        // New ID
    private TextView selectedTextView; // New ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Initialize UI components with new IDs
        usernameTextView = findViewById(R.id.username_display_text_view);
        fileUriTextView = findViewById(R.id.file_uri_display_text_view);
        loadedFileNameTextView = findViewById(R.id.loaded_file_name_text_view); // Initialize new TextView
        fileContentTextView = findViewById(R.id.file_content_text_view);
        btnStartProcessing = findViewById(R.id.btn_start_processing);
        btnDeleteFile = findViewById(R.id.btn_delete_file);
        btnPlayAudio = findViewById(R.id.btn_play_audio);
        btnNextItem = findViewById(R.id.btn_next_item);
        selectedTextView = findViewById(R.id.selected_text_view);


        // Retrieve data from the Intent
        String username = getIntent().getStringExtra("username");
        Uri fileUri = getIntent().getData(); // File URI is passed as Intent data

        if (username != null) {
            usernameTextView.setText("Speaker: " + username);
        } else {
            usernameTextView.setText("Speaker: N/A");
        }

        if (fileUri != null) {
            fileUriTextView.setText("Selected Folder: " + fileUri.getLastPathSegment()); // Displaying only the last segment for folder name
            loadedFileNameTextView.setText("Loaded File: " + fileUri.getLastPathSegment()); // Displaying the loaded file name
            // Attempt to read and display file content
            readFileContent(fileUri);
        } else {
            fileUriTextView.setText("Selected Folder: N/A");
            loadedFileNameTextView.setText("Loaded File: No file selected");
            fileContentTextView.setText("No file content to display.");
        }

        // Set up button click listeners (add your actual logic here)
        btnStartProcessing.setOnClickListener(v -> Toast.makeText(this, "Start Processing clicked!", Toast.LENGTH_SHORT).show());
        btnDeleteFile.setOnClickListener(v -> Toast.makeText(this, "Delete clicked!", Toast.LENGTH_SHORT).show());
        btnPlayAudio.setOnClickListener(v -> Toast.makeText(this, "Play clicked!", Toast.LENGTH_SHORT).show());
        btnNextItem.setOnClickListener(v -> Toast.makeText(this, "Next clicked!", Toast.LENGTH_SHORT).show());
    }

    /**
     * Reads the content of the selected text file and displays it.
     * In a real TTS app, you would pass this content to your TTS engine.
     * @param uri The URI of the text file.
     */
    private void readFileContent(Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            fileContentTextView.setText(stringBuilder.toString());
            Toast.makeText(this, "File loaded successfully!", Toast.LENGTH_SHORT).show();
            // TODO: Here you would integrate your TTS engine to speak stringBuilder.toString()
        } catch (Exception e) {
            Log.e("ProcessingActivity", "Error reading file content: " + e.getMessage(), e);
            fileContentTextView.setText("Error reading file: " + e.getMessage());
            Toast.makeText(this, "Failed to read file content.", Toast.LENGTH_LONG).show();
        }
    }
}
