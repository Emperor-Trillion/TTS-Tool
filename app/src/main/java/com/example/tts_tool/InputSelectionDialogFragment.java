// InputSelectionDialogFragment.java (Example structure)
package com.example.tts_tool;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
    private Button dialogSelectFolderButton;
    private Button dialogStartButton;
    private TextView dialogSelectedFileTextView;
    private TextView dialogSelectedFolderTextView;

    private Uri selectedFileUri;
    private Uri selectedFolderUri;

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

    private ActivityResultLauncher<Uri> selectFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    selectedFolderUri = uri;
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContext().getContentResolver().takePersistableUriPermission(selectedFolderUri, takeFlags);
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
        View view = inflater.inflate(R.layout.dialog_input_selection, null); // You'll need to create this layout

        dialogUsernameEditText = view.findViewById(R.id.dialog_username_edit_text);
        dialogSelectFileButton = view.findViewById(R.id.dialog_select_file_button);
        dialogSelectFolderButton = view.findViewById(R.id.dialog_select_folder_button);
        dialogStartButton = view.findViewById(R.id.dialog_start_button);
        dialogSelectedFileTextView = view.findViewById(R.id.dialog_selected_file_text_view);
        dialogSelectedFolderTextView = view.findViewById(R.id.dialog_selected_folder_text_view);

        dialogSelectFileButton.setOnClickListener(v -> selectFileLauncher.launch(new String[]{"text/plain"}));
        dialogSelectFolderButton.setOnClickListener(v -> selectFolderLauncher.launch(null));

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
                Toast.makeText(getContext(), "Please fill all fields and select files/folders.", Toast.LENGTH_SHORT).show();
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