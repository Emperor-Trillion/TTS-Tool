package com.example.tts_tool;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

public class InputSelectionDialogFragment extends DialogFragment {

    private EditText usernameEditText;
    private TextView selectedFileNameTextView;
    private Button selectFileButton;
    private Button proceedButton;

    private Uri selectedFileUri = null;
    private String username = "";

    // Interface to communicate results back to the hosting activity
    public interface InputSelectionListener {
        void onInputSelected(String username, Uri fileUri);
    }

    private InputSelectionListener listener;

    // ActivityResultLauncher for picking a text file
    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof InputSelectionListener) {
            listener = (InputSelectionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement InputSelectionListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the file picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        selectedFileUri = uri;
                        // Get content resolver to persist URI permission
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        // Display selected file name (last path segment)
                        String fileName = getFileNameFromUri(uri);
                        selectedFileNameTextView.setText("Selected: " + fileName);
                        checkAndEnableProceedButton();
                    } else {
                        selectedFileUri = null;
                        selectedFileNameTextView.setText("No file selected");
                        Toast.makeText(getContext(), "File selection cancelled.", Toast.LENGTH_SHORT).show();
                        checkAndEnableProceedButton();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_input_selection, container, false);

        usernameEditText = view.findViewById(R.id.username_edit_text);
        selectedFileNameTextView = view.findViewById(R.id.selected_file_name_text_view);
        selectFileButton = view.findViewById(R.id.select_file_button);
        proceedButton = view.findViewById(R.id.proceed_button);

        // Initially disable the proceed button
        proceedButton.setEnabled(false);

        // Listener for username input changes
        usernameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                username = s.toString().trim();
                checkAndEnableProceedButton();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        selectFileButton.setOnClickListener(v -> {
            // Launch file picker for text files
            filePickerLauncher.launch(new String[]{"text/plain"});
        });

        proceedButton.setOnClickListener(v -> {
            if (listener != null) {
                // Ensure username and file are selected before proceeding
                if (!username.isEmpty() && selectedFileUri != null) {
                    listener.onInputSelected(username, selectedFileUri);
                    dismiss(); // Dismiss the dialog after successful input
                } else {
                    Toast.makeText(getContext(), "Please enter a username and select a file.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    /**
     * Checks if both username and file are selected and enables/disables the proceed button.
     */
    private void checkAndEnableProceedButton() {
        boolean isUsernameEntered = !usernameEditText.getText().toString().trim().isEmpty();
        boolean isFileSelected = selectedFileUri != null;
        proceedButton.setEnabled(isUsernameEntered && isFileSelected);
    }

    /**
     * Helper to get the display name of a file from its URI.
     */
    private String getFileNameFromUri(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(requireContext(), uri);
        if (documentFile != null && documentFile.getName() != null) {
            return documentFile.getName();
        }
        return uri.getLastPathSegment(); // Fallback
    }

    // Optional: Customize dialog behavior (e.g., set style, non-cancellable)
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // dialog.setCanceledOnTouchOutside(false); // Make it non-cancellable by touching outside
        // dialog.setCancelable(false); // Make it non-cancellable by back button
        return dialog;
    }
}
