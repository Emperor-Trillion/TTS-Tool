package com.example.tts_tool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private Uri currentFolderUri; // Store the currently active folder URI

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 🧠 Initialize folder picker launcher
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri folderUri = result.getData().getData();

                        // Store the URI globally in MainActivity
                        currentFolderUri = folderUri;

                        getContentResolver().takePersistableUriPermission(
                                folderUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );

                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("folder_uri", folderUri.toString())
                                .apply();

                        listSubfolders(folderUri); // Still log here for initial check
                    }
                }
        );

        // 🧭 Check if folder URI exists, otherwise ask user to pick one
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String folderUriString = prefs.getString("folder_uri", null);

        if (folderUriString == null) {
            launchFolderPicker();
        } else {
            Uri savedUri = Uri.parse(folderUriString);
            currentFolderUri = savedUri; // Assign to currentFolderUri
            listSubfolders(savedUri); // Still log here for initial check
        }
    }

    // 🗂️ Helper: Launch the folder picker intent
    private void launchFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        folderPickerLauncher.launch(intent);
    }

    // 📁 Helper: Display subfolders in log (can be used to populate a view)
    private void listSubfolders(Uri folderUri) {
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, folderUri);

        if (pickedDir != null && pickedDir.isDirectory()) {
            for (DocumentFile file : pickedDir.listFiles()) {
                if (file.isDirectory()) {
                    Log.d("Folder", "Subfolder: " + file.getName());
                } else {
                    Log.d("File", "File: " + file.getName());
                }
            }
        }
    }

    // 🎯 Explore activity button
    public void exploreActivity(View v) {
        if (currentFolderUri != null) {
            Intent myIntent = new Intent(MainActivity.this, ExploreActivityPage.class);
            // Pass the Uri to the next activity
            myIntent.putExtra("folder_uri", currentFolderUri.toString());
            startActivity(myIntent);
        } else {
            // Handle case where no folder is selected yet (e.g., show a Toast)
            Log.w("MainActivity", "No folder selected to explore.");
            // Toast.makeText(this, "Please select a folder first!", Toast.LENGTH_SHORT).show();
        }
    }
}