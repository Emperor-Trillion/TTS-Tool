package com.example.tts_tool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// ExploreActivityPage now implements InputSelectionDialogFragment.InputSelectionListener
public class ExploreActivityPage extends AppCompatActivity implements FileListAdapter.OnItemClickListener, InputSelectionDialogFragment.InputSelectionListener {

    private DocumentFile currentFolderDocument; // This is the root folder selected in MainActivity
    private RecyclerView fileListRecyclerView;
    private FileListAdapter fileListAdapter;
    private TextView currentFolderPathTextView;
    private FloatingActionButton startButton;

    private final Stack<DocumentFile> folderHistory = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore_page);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        currentFolderPathTextView = findViewById(R.id.current_folder_path);
        fileListRecyclerView = findViewById(R.id.file_list_recycler_view);
        startButton = findViewById(R.id.button);

        fileListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d("ExploreActivityPage", "Back button pressed. History size: " + folderHistory.size());
                if (folderHistory.size() > 1) {
                    folderHistory.pop();
                    DocumentFile parentDocument = folderHistory.peek();
                    Log.d("ExploreActivityPage", "Navigating back to: " + parentDocument.getName());
                    try {
                        displayFolderContents(parentDocument);
                    } catch (SecurityException e) {
                        Log.e("ExploreActivityPage", "SecurityException during back navigation: " + e.getMessage());
                        Toast.makeText(ExploreActivityPage.this, "Permission denied to access folder.", Toast.LENGTH_LONG).show();
                        finish();
                    } catch (Exception e) {
                        Log.e("ExploreActivityPage", "Error during back navigation: " + e.getMessage(), e);
                        Toast.makeText(ExploreActivityPage.this, "An error occurred navigating back.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.d("ExploreActivityPage", "At root folder, finishing activity.");
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });

        if (getIntent().hasExtra("folder_uri")) {
            String uriString = getIntent().getStringExtra("folder_uri");
            Uri initialFolderUri = Uri.parse(uriString);

            try {
                DocumentFile initialDocument = DocumentFile.fromTreeUri(this, initialFolderUri);

                if (initialDocument != null && initialDocument.isDirectory()) {
                    currentFolderDocument = initialDocument; // Store the root folder DocumentFile
                    folderHistory.push(initialDocument);

                    Log.d("ExploreActivityPage", "Received initial folder URI: " + initialFolderUri.toString());
                    displayFolderContents(currentFolderDocument);
                } else {
                    Log.e("ExploreActivityPage", "Initial folder URI is invalid or not a directory: " + uriString);
                    currentFolderPathTextView.setText(R.string.no_folder_selected_text);
                    Toast.makeText(this, R.string.no_folder_selected_toast, Toast.LENGTH_LONG).show();
                    startButton.setEnabled(false);
                }
            } catch (SecurityException e) {
                Log.e("ExploreActivityPage", "SecurityException on initial folder access: " + e.getMessage());
                currentFolderPathTextView.setText(R.string.error_cannot_access_folder_text);
                Toast.makeText(this, "Permission denied to access the selected folder. Please re-select.", Toast.LENGTH_LONG).show();
                startButton.setEnabled(false);
            } catch (Exception e) {
                Log.e("ExploreActivityPage", "Error on initial folder access: " + e.getMessage(), e);
                currentFolderPathTextView.setText(R.string.error_cannot_access_folder_text);
                Toast.makeText(this, "An error occurred accessing the initial folder.", Toast.LENGTH_LONG).show();
                startButton.setEnabled(false);
            }
        } else {
            Log.e("ExploreActivityPage", getString(R.string.no_folder_received_log));
            currentFolderPathTextView.setText(R.string.no_folder_selected_text);
            Toast.makeText(this, R.string.no_folder_selected_toast, Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
        }
    }

    private void displayFolderContents(DocumentFile folderDocument) {
        Log.d("ExploreActivityPage", "Attempting to display contents of DocumentFile: " + (folderDocument != null ? folderDocument.getName() : "null"));

        if (folderDocument == null || !folderDocument.isDirectory()) {
            currentFolderPathTextView.setText(R.string.error_cannot_access_folder_text);
            Log.e("ExploreActivityPage", getString(R.string.error_invalid_folder_log, folderDocument != null ? folderDocument.getUri().toString() : "null"));
            Toast.makeText(this, getString(R.string.error_could_not_access_folder_toast, (folderDocument != null ? folderDocument.getName() : "Unknown")), Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
            return;
        }

        currentFolderPathTextView.setText(getString(R.string.current_folder_display, folderDocument.getName()));

        List<FileItem> fileItems = new ArrayList<>();
        try {
            DocumentFile[] files = folderDocument.listFiles();
            if (files != null) {
                for (DocumentFile file : files) {
                    if (file != null && file.getName() != null) {
                        fileItems.add(new FileItem(file.getName(), file, file.isDirectory()));
                    }
                }
            } else {
                Log.e("ExploreActivityPage", "listFiles() returned null for DocumentFile: " + folderDocument.getName());
                Toast.makeText(this, "Could not list contents of folder. Check permissions.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e("ExploreActivityPage", "SecurityException listing files for " + folderDocument.getName() + ": " + e.getMessage());
            Toast.makeText(this, "Permission denied to list folder contents.", Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
            return;
        } catch (Exception e) {
            Log.e("ExploreActivityPage", "Error listing files for " + folderDocument.getName() + ": " + e.getMessage(), e);
            Toast.makeText(this, "An error occurred listing folder contents.", Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
            return;
        }

        fileItems.sort((o1, o2) -> {
            if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            } else {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        fileListAdapter = new FileListAdapter(fileItems, this);
        fileListRecyclerView.setAdapter(fileListAdapter);
        currentFolderDocument = folderDocument;
        Log.d("ExploreActivityPage", "Contents displayed for: " + folderDocument.getName());
    }

    @Override
    public void onItemClick(FileItem item) {
        Log.d("ExploreActivityPage", "onItemClick triggered for: " + item.getName() + ", isDirectory: " + item.isDirectory());
        if (item.isDirectory()) {
            Log.d("ExploreActivityPage", "Clicked item is a directory: " + item.getName());
            DocumentFile clickedDirectory = item.getDocumentFile();

            if (clickedDirectory == null || !clickedDirectory.isDirectory()) {
                Log.e("ExploreActivityPage", "Clicked directory DocumentFile is null or not a directory: " + item.getName());
                Toast.makeText(this, "Cannot open invalid directory: " + item.getName(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!folderHistory.isEmpty() && !folderHistory.peek().getUri().equals(clickedDirectory.getUri())) {
                folderHistory.push(clickedDirectory);
                Log.d("ExploreActivityPage", "Pushed " + clickedDirectory.getName() + " to history. New size: " + folderHistory.size());
            } else if (folderHistory.isEmpty()) {
                folderHistory.push(clickedDirectory);
                Log.d("ExploreActivityPage", "History was empty, pushed " + clickedDirectory.getName() + ". New size: " + folderHistory.size());
            } else {
                Log.d("ExploreActivityPage", "Clicked directory is already current, not pushing to history.");
            }
            Log.d("ExploreActivityPage", "Calling displayFolderContents for new DocumentFile: " + clickedDirectory.getName());
            try {
                displayFolderContents(clickedDirectory);
            } catch (SecurityException e) {
                Log.e("ExploreActivityPage", "SecurityException navigating into " + item.getName() + ": " + e.getMessage());
                Toast.makeText(this, "Permission denied to access folder: " + item.getName(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("ExploreActivityPage", "Error navigating into " + item.getName() + ": " + e.getMessage(), e);
                Toast.makeText(this, "An error occurred navigating into " + item.getName(), Toast.LENGTH_LONG).show();
            }
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String mimeType = item.getDocumentFile().getType();
                if (mimeType == null) {
                    mimeType = "*/*";
                    Log.w("ExploreActivityPage", "MIME type for " + item.getName() + " is null. Using default.");
                }
                intent.setDataAndType(item.getDocumentFile().getUri(), mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                Toast.makeText(this, "Opening file: " + item.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("ExploreActivityPage", getString(R.string.failed_to_open_file_log, item.getName()), e);
                Toast.makeText(this, getString(R.string.cannot_open_file_toast, item.getName()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * This method is called when the FloatingActionButton is clicked.
     * It now shows the InputSelectionDialogFragment.
     */
    public void beginRecording(View v) {
        // Create and show the dialog fragment
        InputSelectionDialogFragment dialogFragment = new InputSelectionDialogFragment();
        dialogFragment.show(getSupportFragmentManager(), "InputSelectionDialog");
    }

    /**
     * Callback from InputSelectionDialogFragment when username and file are selected.
     * This method launches the new ProcessingActivity.
     */
    @Override
    public void onInputSelected(String username, Uri fileUri) {
        if (username != null && !username.isEmpty() && fileUri != null) {
            Intent intent = new Intent(ExploreActivityPage.this, ProcessingActivity.class);
            intent.putExtra("username", username);
            intent.setData(fileUri); // Pass the selected text file URI as Intent data

            // Pass the root folder URI (where new recording folder will be created)
            if (currentFolderDocument != null && currentFolderDocument.getUri() != null) {
                intent.putExtra("root_folder_uri", currentFolderDocument.getUri().toString());
            } else {
                Log.e("ExploreActivityPage", "Root folder URI is null when launching ProcessingActivity.");
                Toast.makeText(this, "Error: Recording folder not set.", Toast.LENGTH_LONG).show();
                return; // Prevent crash if root folder is not available
            }

            startActivity(intent);
        } else {
            Toast.makeText(this, "Invalid input received from dialog.", Toast.LENGTH_SHORT).show();
        }
    }
}
