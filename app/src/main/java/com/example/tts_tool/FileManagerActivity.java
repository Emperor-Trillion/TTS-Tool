// app/src/main/java/com/example/tts_tool/FileManagerActivity.java
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack; // Import Stack

public class FileManagerActivity extends AppCompatActivity implements FileAdapter.OnItemClickListener,
        FileAdapter.OnItemLongClickListener {

    private static final String TAG = "FileManagerActivity";

    private TextView currentFolderPathTextView;
    private RecyclerView fileRecyclerView;
    private Button btnDeleteSelectedFiles;

    private FileAdapter fileAdapter;
    private List<DocumentFile> currentFilesAndFolders;
    private Uri currentWorkingFolderUri;
    private Uri initialRootFolderUri;

    // --- NEW: Stack to manage folder navigation history ---
    private Stack<Uri> folderHistoryStack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        currentFolderPathTextView = findViewById(R.id.current_folder_path_text_view);
        fileRecyclerView = findViewById(R.id.file_recycler_view);
        btnDeleteSelectedFiles = findViewById(R.id.btn_delete_selected_files);

        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        currentFilesAndFolders = new ArrayList<>();
        fileAdapter = new FileAdapter(currentFilesAndFolders, this, this);
        fileRecyclerView.setAdapter(fileAdapter);

        folderHistoryStack = new Stack<>(); // Initialize the stack

        // Get the working folder URI from the intent
        if (getIntent().hasExtra("working_folder_uri")) {
            String uriString = getIntent().getStringExtra("working_folder_uri");
            initialRootFolderUri = Uri.parse(uriString); // Store the initial root

            // Start at the initial root and push it onto the stack
            currentWorkingFolderUri = initialRootFolderUri;
            folderHistoryStack.push(initialRootFolderUri); // Push initial root

            Log.d(TAG, "Received working folder URI: " + currentWorkingFolderUri.toString());
            loadFolderContents(currentWorkingFolderUri);
        } else {
            Toast.makeText(this, "No working folder specified.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if no folder is specified
        }

        btnDeleteSelectedFiles.setOnClickListener(v -> showDeleteConfirmationDialog());

        updateDeleteButtonState();

        // --- MODIFIED: OnBackPressedCallback using the stack ---
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "handleOnBackPressed: Current Stack Size: " + folderHistoryStack.size());

                if (folderHistoryStack.size() > 1) {
                    // Pop the current folder (which we just navigated to)
                    folderHistoryStack.pop();
                    // Get the previous folder from the top of the stack
                    currentWorkingFolderUri = folderHistoryStack.peek();
                    Log.d(TAG, "handleOnBackPressed: Navigating up to: " + currentWorkingFolderUri.toString());
                    loadFolderContents(currentWorkingFolderUri);
                } else {
                    // If the stack has 1 or 0 elements, it means we are at the initial root
                    // or there's nothing else to go back to within this activity.
                    Log.d(TAG, "handleOnBackPressed: At initial root or empty history, finishing activity.");
                    // Let the system handle the back press, which will finish this activity.
                    // This is equivalent to calling super.onBackPressed() if it were available here,
                    // but directly finishing is clearer for this context.
                    finish();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void loadFolderContents(Uri uri) {
        currentFilesAndFolders.clear();
        fileAdapter.clearSelection(); // Clear selection when loading new folder
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);

        if (documentFile != null && documentFile.isDirectory()) {
            currentFolderPathTextView.setText("Current Folder: " + documentFile.getName());
            for (DocumentFile file : documentFile.listFiles()) {
                currentFilesAndFolders.add(file);
            }
            // Sort alphabetically (folders first, then files)
            currentFilesAndFolders.sort((f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            fileAdapter.notifyDataSetChanged();
            if (currentFilesAndFolders.isEmpty()) {
                Toast.makeText(this, "Folder is empty.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Invalid folder or access denied.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to load folder contents for URI: " + uri.toString());
            finish(); // If we can't load the folder, we should probably exit
        }
        updateDeleteButtonState();
    }

    @Override
    public void onItemClick(int position) {
        DocumentFile clickedItem = currentFilesAndFolders.get(position);
        if (clickedItem.isDirectory()) {
            // Navigate into the directory
            currentWorkingFolderUri = clickedItem.getUri();
            folderHistoryStack.push(currentWorkingFolderUri); // Push new folder onto stack
            Log.d(TAG, "onItemClick: Navigated into: " + currentWorkingFolderUri.toString());
            loadFolderContents(currentWorkingFolderUri);
        } else {
            // Open the file
            openFile(clickedItem);
        }
    }

    @Override
    public void onItemLongClick(int position) {
        fileAdapter.toggleSelection(position);
        updateDeleteButtonState();
    }

    private void openFile(DocumentFile file) {
        if (file == null || !file.isFile() || !file.exists()) {
            Toast.makeText(this, "Cannot open file: Invalid or non-existent.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(file.getUri(), file.getType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open file with..."));
        } catch (Exception e) {
            Log.e(TAG, "Error opening file: " + e.getMessage(), e);
            Toast.makeText(this, "Cannot open file: No app available to handle this type.", Toast.LENGTH_LONG).show();
        }
    }

    private void showDeleteConfirmationDialog() {
        List<DocumentFile> selectedItems = fileAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No items selected for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete " + selectedItems.size() + " selected item(s)? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedFiles(selectedItems))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteSelectedFiles(List<DocumentFile> itemsToDelete) {
        int deletedCount = 0;
        for (DocumentFile item : itemsToDelete) {
            try {
                if (item.delete()) {
                    deletedCount++;
                    Log.d(TAG, "Deleted: " + item.getName());
                } else {
                    Log.e(TAG, "Failed to delete: " + item.getName());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied to delete: " + item.getName() + " - " + e.getMessage());
                Toast.makeText(this, "Permission denied for: " + item.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting " + item.getName() + ": " + e.getMessage());
                Toast.makeText(this, "Error deleting " + item.getName(), Toast.LENGTH_SHORT).show();
            }
        }
        Toast.makeText(this, "Deleted " + deletedCount + " item(s).", Toast.LENGTH_SHORT).show();
        loadFolderContents(currentWorkingFolderUri); // Refresh the list
        fileAdapter.clearSelection(); // Clear selection after deletion
        updateDeleteButtonState();
    }

    private void updateDeleteButtonState() {
        btnDeleteSelectedFiles.setEnabled(!fileAdapter.getSelectedItems().isEmpty());
    }
}