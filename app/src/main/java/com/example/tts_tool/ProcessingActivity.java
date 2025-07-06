package com.example.tts_tool;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
    private TextView fileContentTextView; // To display file content for testing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing); // Set the layout for this activity

        usernameTextView = findViewById(R.id.username_display_text_view);
        fileUriTextView = findViewById(R.id.file_uri_display_text_view);
        fileContentTextView = findViewById(R.id.file_content_text_view);

        // Retrieve data from the Intent
        String username = getIntent().getStringExtra("username");
        Uri fileUri = getIntent().getData(); // File URI is passed as Intent data

        if (username != null) {
            usernameTextView.setText("User: " + username);
        } else {
            usernameTextView.setText("User: N/A");
        }

        if (fileUri != null) {
            fileUriTextView.setText("File URI: " + fileUri.toString());
            // Attempt to read and display file content
            readFileContent(fileUri);
        } else {
            fileUriTextView.setText("File URI: N/A");
            fileContentTextView.setText("No file content to display.");
        }
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
