// InputSelectionDialogFragment.java (Example structure)
package com.example.tts_tool;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;

public class InputSelectionDialogFragment extends DialogFragment {

    public interface InputSelectionListener {
        void onInputSelected(String username, Uri fileUri, Uri folderUri);
    }

    private InputSelectionListener listener;
    private EditText dialogUsernameEditText;
    private Button dialogSelectFileButton;
    private Button dialogSelectFolderButton; // Re-added
    private Button dialogStartButton;
    private TextView dialogSelectedFileTextView;
    private TextView dialogSelectedFolderTextView; // Re-added

    private Uri selectedFileUri;
    private Uri selectedFolderUri;

    // SharedPreferences for persisting folder URI
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "TTSRecorderPrefs";
    private static final String KEY_SAVED_WORKING_FOLDER_URI = "savedWorkingFolderUri";
    private static final String TAG = "InputSelectionDialog";

    private ActivityResultLauncher<String[]> selectFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    dialogSelectedFileTextView.setText("File: " + getFileName(selectedFileUri));
                    updateDialogStartButtonState();
                } else {
                    Toast.makeText(getContext(), "File selection cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    // Re-added selectFolderLauncher
    private ActivityResultLauncher<Uri> selectFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    selectedFolderUri = uri;
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContext().getContentResolver().takePersistableUriPermission(selectedFolderUri, takeFlags);

                    // Save the selected folder URI to SharedPreferences
                    if (sharedPreferences != null) {
                        sharedPreferences.edit().putString(KEY_SAVED_WORKING_FOLDER_URI, selectedFolderUri.toString()).apply();
                        Toast.makeText(getContext(), "Working folder saved!", Toast.LENGTH_SHORT).show();
                    }

                    dialogSelectedFolderTextView.setText("Folder: " + getFolderName(selectedFolderUri));
                    updateDialogStartButtonState();
                } else {
                    Toast.makeText(getContext(), "Folder selection cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (InputSelectionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement InputSelectionListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_input_selection, null);

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        dialogUsernameEditText = view.findViewById(R.id.dialog_username_edit_text);
        dialogSelectFileButton = view.findViewById(R.id.dialog_select_file_button);
        dialogSelectFolderButton = view.findViewById(R.id.dialog_select_folder_button); // Re-added
        dialogStartButton = view.findViewById(R.id.dialog_start_button);
        dialogSelectedFileTextView = view.findViewById(R.id.dialog_selected_file_text_view);
        dialogSelectedFolderTextView = view.findViewById(R.id.dialog_selected_folder_text_view); // Re-added

        // Attempt to load saved folder URI
        String savedFolderUriString = sharedPreferences.getString(KEY_SAVED_WORKING_FOLDER_URI, null);
        if (savedFolderUriString != null) {
            selectedFolderUri = Uri.parse(savedFolderUriString);
            DocumentFile docFile = DocumentFile.fromTreeUri(requireContext(), selectedFolderUri);
            if (docFile != null && docFile.exists() && docFile.isDirectory()) {
                Log.d(TAG, "Using saved working folder: " + getFolderName(selectedFolderUri));
                dialogSelectedFolderTextView.setText("Folder: " + getFolderName(selectedFolderUri) + " (Saved)");
                // Hide the folder selection button since a valid one is already saved
                dialogSelectFolderButton.setVisibility(View.GONE);
                dialogSelectedFolderTextView.setVisibility(View.GONE); // Hide the text view too
                Toast.makeText(getContext(), "Using previously saved working folder.", Toast.LENGTH_SHORT).show();
            } else {
                // If saved URI is invalid, clear it and make folder selection visible
                Log.w(TAG, "Saved working folder URI is invalid or inaccessible. Clearing preference.");
                selectedFolderUri = null;
                sharedPreferences.edit().remove(KEY_SAVED_WORKING_FOLDER_URI).apply();
                dialogSelectedFolderTextView.setText("Folder: Not selected");
                dialogSelectFolderButton.setVisibility(View.VISIBLE); // Make button visible
                dialogSelectedFolderTextView.setVisibility(View.VISIBLE); // Make text view visible
                Toast.makeText(getContext(), "Saved working folder not found. Please select again.", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.i(TAG, "No saved working folder found. Prompting user to select.");
            dialogSelectedFolderTextView.setText("Folder: Not selected");
            dialogSelectFolderButton.setVisibility(View.VISIBLE); // Make button visible
            dialogSelectedFolderTextView.setVisibility(View.VISIBLE); // Make text view visible
            Toast.makeText(getContext(), "Please select a working folder.", Toast.LENGTH_LONG).show();
        }

        dialogSelectFileButton.setOnClickListener(v -> selectFileLauncher.launch(new String[]{"text/plain"}));
        dialogSelectFolderButton.setOnClickListener(v -> selectFolderLauncher.launch(null)); // Re-added listener

        dialogUsernameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateDialogStartButtonState();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        dialogStartButton.setOnClickListener(v -> {
            String username = dialogUsernameEditText.getText().toString().trim();
            if (listener != null && !username.isEmpty() && selectedFileUri != null && selectedFolderUri != null) {
                listener.onInputSelected(username, selectedFileUri, selectedFolderUri);
                dismiss();
            } else {
                Toast.makeText(getContext(), "Please enter username, select a text file, and a working folder.", Toast.LENGTH_SHORT).show();
            }
        });

        updateDialogStartButtonState(); // Initial state

        builder.setView(view);
        return builder.create();
    }

    private void updateDialogStartButtonState() {
        boolean isUsernameEntered = dialogUsernameEditText != null && !dialogUsernameEditText.getText().toString().trim().isEmpty();
        boolean isFileSelected = selectedFileUri != null;
        boolean isFolderSelected = selectedFolderUri != null;

        if (dialogStartButton != null) {
            dialogStartButton.setEnabled(isUsernameEntered && isFileSelected && isFolderSelected);
        }
    }

    private String getFileName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(getContext(), uri);
        return documentFile != null ? documentFile.getName() : "Unknown File";
    }

    private String getFolderName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(getContext(), uri);
        return documentFile != null ? documentFile.getName() : "Unknown Folder";
    }
}
