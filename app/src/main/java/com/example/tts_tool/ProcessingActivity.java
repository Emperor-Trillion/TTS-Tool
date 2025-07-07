package com.example.tts_tool;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class ProcessingActivity extends AppCompatActivity implements SentenceAdapter.OnItemClickListener {

    private static final String TAG = "ProcessingActivity";

    private TextView usernameTextView;
    private TextView fileUriTextView; // This now shows the unique working folder name
    private TextView loadedFileNameTextView; // This shows the name of the copied text file
    private TextView recordingProgressTextView;
    private ProgressBar recordingProgressBar;
    private TextView currentSelectedSentenceTextView;
    private RecyclerView sentencesRecyclerView;
    private SentenceAdapter sentenceAdapter;

    private Button btnStartProcessing;
    private Button btnDeleteFile;
    private Button btnPlayAudio;
    private Button btnNextItem;

    private List<SentenceItem> sentenceItems;
    private int currentSentenceIndex = -1;
    private DocumentFile workingFolderDocument;

    private MediaRecorder mediaRecorder; // MediaRecorder instance for audio recording
    private boolean isRecording = false; // State variable to track recording status
    private DocumentFile currentRecordingDocumentFile; // DocumentFile for the audio being recorded

    // ActivityResultLauncher for requesting RECORD_AUDIO permission
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "RECORD_AUDIO permission granted.");
                    // Permission is granted, proceed with recording
                    startRecordingInternal();
                } else {
                    Log.w(TAG, "RECORD_AUDIO permission denied.");
                    Toast.makeText(this, "Audio recording permission denied. Cannot record.", Toast.LENGTH_LONG).show();
                    isRecording = false; // Ensure state is reset
                    updateButtonStates();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Initialize UI components
        usernameTextView = findViewById(R.id.username_display_text_view);
        fileUriTextView = findViewById(R.id.file_uri_display_text_view);
        loadedFileNameTextView = findViewById(R.id.loaded_file_name_text_view);
        recordingProgressTextView = findViewById(R.id.recording_progress_text_view);
        recordingProgressBar = findViewById(R.id.recording_progress_bar);
        currentSelectedSentenceTextView = findViewById(R.id.current_selected_sentence_text_view);
        sentencesRecyclerView = findViewById(R.id.sentences_recycler_view);
        btnStartProcessing = findViewById(R.id.btn_start_processing);
        btnDeleteFile = findViewById(R.id.btn_delete_file);
        btnPlayAudio = findViewById(R.id.btn_play_audio);
        btnNextItem = findViewById(R.id.btn_next_item);

        sentencesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Retrieve data from the Intent
        String username = getIntent().getStringExtra("username");
        Uri originalInputFileUri = getIntent().getData();
        String rootFolderUriString = getIntent().getStringExtra("root_folder_uri");

        if (username != null) {
            usernameTextView.setText("Speaker: " + username);
        } else {
            usernameTextView.setText("Speaker: N/A");
        }

        if (originalInputFileUri != null && rootFolderUriString != null) {
            Uri rootFolderUri = Uri.parse(rootFolderUriString);
            DocumentFile rootDocument = DocumentFile.fromTreeUri(this, rootFolderUri);

            if (rootDocument != null && rootDocument.isDirectory()) {
                String originalFileName = (DocumentFile.fromSingleUri(this, originalInputFileUri) != null && DocumentFile.fromSingleUri(this, originalInputFileUri).getName() != null)
                        ? DocumentFile.fromSingleUri(this, originalInputFileUri).getName()
                        : "unknown_file";
                String sanitizedFileName = originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                        .replaceAll("[^a-zA-Z0-9_\\-]", "_");

                String folderName = "TTS_" + sanitizedFileName + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                Log.d(TAG, "Attempting to create working folder: " + folderName);

                workingFolderDocument = rootDocument.createDirectory(folderName);

                if (workingFolderDocument != null) {
                    Toast.makeText(this, "Working folder created: " + folderName, Toast.LENGTH_SHORT).show();
                    fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());

                    copyInputFileToWorkingFolder(originalInputFileUri, workingFolderDocument);

                    DocumentFile selectedFile = DocumentFile.fromSingleUri(this, originalInputFileUri);
                    loadedFileNameTextView.setText("Loaded File: " + (selectedFile != null ? selectedFile.getName() : "N/A"));
                    readFileContentAndPopulateList(originalInputFileUri);

                } else {
                    Log.e(TAG, "Failed to create working folder in " + rootFolderUri.toString());
                    Toast.makeText(this, "Failed to create working folder. Check permissions.", Toast.LENGTH_LONG).show();
                    handleInitializationError();
                }
            } else {
                Log.e(TAG, "Root folder document is invalid or not a directory: " + rootFolderUriString);
                Toast.makeText(this, "Invalid root folder. Please re-select a folder in previous screen.", Toast.LENGTH_LONG).show();
                handleInitializationError();
            }
        } else {
            Log.e(TAG, "Original input file URI or root folder URI is null.");
            Toast.makeText(this, "Missing input file or root folder information.", Toast.LENGTH_LONG).show();
            handleInitializationError();
        }

        // Set up button click listeners
        btnStartProcessing.setOnClickListener(v -> toggleRecording());
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnPlayAudio.setOnClickListener(v -> handlePlayAudio());
        btnNextItem.setOnClickListener(v -> handleNextSentence());

        updateProgressBar();
        updateButtonStates(); // Initial update of button states
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release MediaRecorder resources if it's still active
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    mediaRecorder.stop(); // Stop if still recording
                }
                mediaRecorder.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "RuntimeException on MediaRecorder release in onDestroy: " + e.getMessage());
            } finally {
                mediaRecorder = null;
            }
        }
    }

    /**
     * Handles errors during activity initialization by disabling buttons and showing default text.
     */
    private void handleInitializationError() {
        fileUriTextView.setText("Working Folder: Error");
        loadedFileNameTextView.setText("Loaded File: Error");
        currentSelectedSentenceTextView.setText("Initialization Error. Please restart.");
        btnStartProcessing.setEnabled(false);
        btnDeleteFile.setEnabled(false);
        btnPlayAudio.setEnabled(false);
        btnNextItem.setEnabled(false);
        if (sentencesRecyclerView.getAdapter() == null) {
            sentencesRecyclerView.setAdapter(new SentenceAdapter(new ArrayList<>(), this));
        }
        updateProgressBar();
    }

    /**
     * Copies the selected input text file into the newly generated working folder.
     * @param sourceUri The URI of the original text file.
     * @param targetFolder The DocumentFile representing the newly created working folder.
     */
    private void copyInputFileToWorkingFolder(Uri sourceUri, DocumentFile targetFolder) {
        DocumentFile sourceFile = DocumentFile.fromSingleUri(this, sourceUri);
        if (sourceFile == null || !sourceFile.isFile()) {
            Toast.makeText(this, "Source file not found or is not a file.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Source file invalid: " + sourceUri);
            return;
        }

        try {
            DocumentFile newFileInWorkingFolder = targetFolder.createFile(sourceFile.getType(), sourceFile.getName());

            if (newFileInWorkingFolder != null) {
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = getContentResolver().openOutputStream(newFileInWorkingFolder.getUri())) {

                    if (in == null || out == null) {
                        Log.e(TAG, "Failed to open streams for file copy.");
                        Toast.makeText(this, "Failed to open streams for file copy.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Toast.makeText(this, "Input file copied to working folder!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Input file copied to: " + newFileInWorkingFolder.getUri().toString());
                }
            } else {
                Toast.makeText(this, "Failed to create copy of input file.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to create copy of input file: " + sourceFile.getName());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying input file: " + e.getMessage(), e);
            Toast.makeText(this, "Error copying input file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied during file copy: " + e.getMessage(), e);
            Toast.makeText(this, "Permission denied to copy input file.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Reads the content of the selected text file and populates the sentences list.
     * @param uri The URI of the text file.
     */
    private void readFileContentAndPopulateList(Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append(" ");
            }
            String fullText = stringBuilder.toString().trim();
            parseTextIntoSentences(fullText);
            Toast.makeText(this, "File loaded and sentences parsed!", Toast.LENGTH_SHORT).show();

            if (!sentenceItems.isEmpty()) {
                selectSentence(0);
            }
            updateProgressBar();
        } catch (Exception e) {
            Log.e(TAG, "Error reading file content: " + e.getMessage(), e);
            currentSelectedSentenceTextView.setText("Error reading file: " + e.getMessage());
            Toast.makeText(this, "Failed to read file content.", Toast.LENGTH_LONG).show();
            updateProgressBar();
        }
    }

    /**
     * Parses the given text into individual sentences using a simple regex.
     * @param fullText The entire text content from the file.
     */
    private void parseTextIntoSentences(String fullText) {
        sentenceItems = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9])|\\n");
        String[] sentences = pattern.split(fullText);

        int index = 0;
        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (!trimmedSentence.isEmpty()) {
                sentenceItems.add(new SentenceItem(index++, trimmedSentence));
            }
        }
        sentenceAdapter = new SentenceAdapter(sentenceItems, this);
        sentencesRecyclerView.setAdapter(sentenceAdapter);
    }

    /**
     * Handles item clicks in the RecyclerView (sentence selection).
     * @param position The position of the clicked sentence.
     */
    @Override
    public void onItemClick(int position) {
        if (!isRecording) {
            selectSentence(position);
        } else {
            Toast.makeText(this, "Cannot select sentence while recording is in progress.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Selects a sentence by its index, updates UI, and scrolls to it.
     * @param index The index of the sentence to select.
     */
    private void selectSentence(int index) {
        if (index >= 0 && index < sentenceItems.size()) {
            if (currentSentenceIndex != -1 && currentSentenceIndex < sentenceItems.size()) {
                sentenceItems.get(currentSentenceIndex).setSelected(false);
            }

            currentSentenceIndex = index;
            sentenceItems.get(currentSentenceIndex).setSelected(true);
            sentenceAdapter.setSelectedPosition(currentSentenceIndex);

            currentSelectedSentenceTextView.setText(sentenceItems.get(currentSentenceIndex).getText());

            sentencesRecyclerView.scrollToPosition(currentSentenceIndex);

            updateButtonStates();
        } else {
            currentSelectedSentenceTextView.setText("No sentence selected.");
            currentSentenceIndex = -1;
            updateButtonStates();
        }
    }

    /**
     * Updates the enabled/disabled state of action buttons and the start button text/color.
     */
    private void updateButtonStates() {
        boolean isSentenceSelected = currentSentenceIndex != -1;
        SentenceItem selectedItem = isSentenceSelected ? sentenceItems.get(currentSentenceIndex) : null;
        boolean hasRecordedAudio = selectedItem != null && selectedItem.getRecordedFileName() != null;

        if (isRecording) {
            btnStartProcessing.setText("Stop Recording");
            btnStartProcessing.setBackgroundTintList(getResources().getColorStateList(R.color.custom_red, getTheme()));
            btnStartProcessing.setEnabled(true); // Always enabled to stop
            btnDeleteFile.setEnabled(false);
            btnPlayAudio.setEnabled(false);
            btnNextItem.setEnabled(false);
        } else {
            btnStartProcessing.setText("Start Recording");
            btnStartProcessing.setBackgroundTintList(getResources().getColorStateList(R.color.custom_green, getTheme()));
            btnStartProcessing.setEnabled(isSentenceSelected);
            btnDeleteFile.setEnabled(hasRecordedAudio);
            btnPlayAudio.setEnabled(hasRecordedAudio);
            btnNextItem.setEnabled(isSentenceSelected && currentSentenceIndex < sentenceItems.size() - 1);
        }
    }

    /**
     * Toggles the recording state (Start -> Stop, Stop -> Start).
     */
    private void toggleRecording() {
        if (!isRecording) {
            // Check for RECORD_AUDIO permission before starting
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission is already granted, proceed
                startRecordingInternal();
            } else {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        } else {
            stopRecordingInternal();
        }
    }

    /**
     * Internal method to handle starting the recording.
     * This method assumes RECORD_AUDIO permission has been granted.
     */
    private void startRecordingInternal() {
        if (currentSentenceIndex == -1) {
            Toast.makeText(this, "Please select a sentence to record.", Toast.LENGTH_SHORT).show();
            return;
        }

        SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);

        if (selectedItem.getRecordedFileName() != null && !selectedItem.getRecordedFileName().isEmpty()) {
            Toast.makeText(this, "This sentence has already been recorded. Delete it first to re-record.", Toast.LENGTH_LONG).show();
            return;
        }

        if (workingFolderDocument == null || !workingFolderDocument.exists() || !workingFolderDocument.isDirectory()) {
            Toast.makeText(this, "Working folder not accessible. Cannot record.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Working folder is null, does not exist, or is not a directory.");
            return;
        }

        try {
            // Create the output file for the recording
            String recordedFileName = "sentence_" + (selectedItem.getIndex() + 1) + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp3";
            currentRecordingDocumentFile = workingFolderDocument.createFile("audio/mpeg", recordedFileName);

            if (currentRecordingDocumentFile == null) {
                Toast.makeText(this, "Failed to create audio file for recording.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to create audio file in working folder.");
                return;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // Using MP4 container
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // Using AAC encoder
            mediaRecorder.setOutputFile(getContentResolver().openFileDescriptor(currentRecordingDocumentFile.getUri(), "w").getFileDescriptor());

            mediaRecorder.prepare();
            mediaRecorder.start(); // Start recording!

            isRecording = true;
            updateButtonStates();
            Toast.makeText(this, "Recording started for: \"" + selectedItem.getText() + "\"", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording to: " + currentRecordingDocumentFile.getUri().toString());

        } catch (IOException e) {
            Log.e(TAG, "IOException during MediaRecorder setup/start: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isRecording = false; // Reset state on error
            updateButtonStates();
            // Delete the partially created file if an error occurred during setup
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException during MediaRecorder setup/start: " + e.getMessage(), e);
            Toast.makeText(this, "Runtime error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isRecording = false; // Reset state on error
            updateButtonStates();
            // Delete the partially created file if an error occurred during setup
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        }
    }

    /**
     * Internal method to handle stopping the recording.
     */
    private void stopRecordingInternal() {
        if (mediaRecorder == null) {
            Log.w(TAG, "MediaRecorder is null, cannot stop recording.");
            Toast.makeText(this, "Recording not active.", Toast.LENGTH_SHORT).show();
            isRecording = false;
            updateButtonStates();
            return;
        }

        try {
            mediaRecorder.stop(); // Stop recording
            mediaRecorder.release(); // Release resources
            mediaRecorder = null; // Clear the reference

            isRecording = false; // Update state
            updateButtonStates(); // Update button text and states

            if (currentSentenceIndex != -1 && currentRecordingDocumentFile != null) {
                SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
                selectedItem.setRecordedFile(currentRecordingDocumentFile.getName(), currentRecordingDocumentFile.getUri());
                sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                Toast.makeText(this, "Recording stopped and saved.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Recording saved to: " + currentRecordingDocumentFile.getUri().toString());
                updateProgressBar();
                handleNextSentence(); // Automatically move to the next sentence
            } else {
                Toast.makeText(this, "Recording stopped, but no sentence selected or file created.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Recording stopped but currentSentenceIndex or currentRecordingDocumentFile was null.");
            }
        } catch (RuntimeException e) {
            // This can happen if stop is called before start, or if recording failed internally
            Log.e(TAG, "RuntimeException during MediaRecorder stop/release: " + e.getMessage(), e);
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            // Attempt to clean up partially recorded file if it exists
            if (currentRecordingDocumentFile != null && currentRecordingDocumentFile.exists()) {
                currentRecordingDocumentFile.delete();
            }
        } finally {
            currentRecordingDocumentFile = null; // Clear reference to the recording file
        }
    }

    /**
     * Handles the "Delete" button click.
     * Clears the recorded audio path for the currently selected sentence and deletes the file.
     */
    private void handleDeleteRecording() {
        if (isRecording) {
            Toast.makeText(this, "Cannot delete while recording is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex != -1) {
            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
            if (selectedItem.getRecordedFileUri() != null) {
                try {
                    DocumentFile fileToDelete = DocumentFile.fromSingleUri(this, selectedItem.getRecordedFileUri());
                    if (fileToDelete != null && fileToDelete.exists()) {
                        if (fileToDelete.delete()) {
                            selectedItem.clearRecordedFile();
                            sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                            Toast.makeText(this, "Recording deleted for sentence " + (currentSentenceIndex + 1), Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Deleted file: " + fileToDelete.getName());
                            updateProgressBar();
                        } else {
                            Toast.makeText(this, "Failed to delete recording.", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "DocumentFile.delete() returned false for " + fileToDelete.getName());
                        }
                    } else {
                        Toast.makeText(this, "Recorded file not found.", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Attempted to delete non-existent file: " + selectedItem.getRecordedFileUri());
                        selectedItem.clearRecordedFile();
                        sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                        updateProgressBar();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied to delete file: " + e.getMessage(), e);
                    Toast.makeText(this, "Permission denied to delete recording.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting recording: " + e.getMessage(), e);
                    Toast.makeText(this, "An error occurred deleting recording.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "No recording to delete for this sentence.", Toast.LENGTH_SHORT).show();
            }
        }
        updateButtonStates();
    }

    /**
     * Handles the "Play" button click.
     * Simulates playing the audio associated with the currently selected sentence.
     */
    private void handlePlayAudio() {
        if (isRecording) {
            Toast.makeText(this, "Cannot play while recording is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex != -1) {
            SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);
            if (selectedItem.getRecordedFileUri() != null) {
                Toast.makeText(this, "Playing: " + selectedItem.getRecordedFileName(), Toast.LENGTH_SHORT).show();
                // TODO: Implement actual audio playback using MediaPlayer or ExoPlayer
                // Example: MediaPlayer mediaPlayer = MediaPlayer.create(this, selectedItem.getRecordedFileUri());
                // mediaPlayer.start();
            } else {
                Toast.makeText(this, "No audio recorded for this sentence.", Toast.LENGTH_SHORT).show();
            }
        }
        updateButtonStates();
    }

    /**
     * Handles the "Next" button click.
     * Moves to and selects the next sentence in the list.
     */
    private void handleNextSentence() {
        if (isRecording) {
            Toast.makeText(this, "Cannot move to next sentence while recording is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSentenceIndex < sentenceItems.size() - 1) {
            selectSentence(currentSentenceIndex + 1);
        } else {
            Toast.makeText(this, "End of sentences.", Toast.LENGTH_SHORT).show();
        }
        updateButtonStates();
    }

    /**
     * Updates the progress bar and text to show recorded sentences vs total sentences.
     */
    private void updateProgressBar() {
        if (sentenceItems == null || sentenceItems.isEmpty()) {
            recordingProgressBar.setProgress(0);
            recordingProgressTextView.setText("Progress: 0/0 Recorded");
            return;
        }

        int totalSentences = sentenceItems.size();
        int recordedSentences = 0;
        for (SentenceItem item : sentenceItems) {
            if (item.getRecordedFileName() != null) {
                recordedSentences++;
            }
        }

        recordingProgressTextView.setText(String.format(Locale.getDefault(), "Progress: %d/%d Recorded", recordedSentences, totalSentences));

        int progress = (totalSentences > 0) ? (recordedSentences * 100 / totalSentences) : 0;
        recordingProgressBar.setProgress(progress);
    }
}
