package com.example.tts_tool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar; // Import ProgressBar
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    private TextView usernameTextView;
    private TextView fileUriTextView; // This now shows the unique working folder name
    private TextView loadedFileNameTextView; // This shows the name of the copied text file
    private TextView recordingProgressTextView; // New TextView for progress text
    private ProgressBar recordingProgressBar; // New ProgressBar
    private TextView currentSelectedSentenceTextView;
    private RecyclerView sentencesRecyclerView;
    private SentenceAdapter sentenceAdapter;

    private Button btnStartProcessing;
    private Button btnDeleteFile;
    private Button btnPlayAudio;
    private Button btnNextItem;

    private List<SentenceItem> sentenceItems;
    private int currentSentenceIndex = -1;
    private DocumentFile workingFolderDocument; // This will be the newly created unique folder for recordings

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Initialize UI components
        usernameTextView = findViewById(R.id.username_display_text_view);
        fileUriTextView = findViewById(R.id.file_uri_display_text_view);
        loadedFileNameTextView = findViewById(R.id.loaded_file_name_text_view);
        recordingProgressTextView = findViewById(R.id.recording_progress_text_view); // Initialize progress text
        recordingProgressBar = findViewById(R.id.recording_progress_bar); // Initialize progress bar
        currentSelectedSentenceTextView = findViewById(R.id.current_selected_sentence_text_view);
        sentencesRecyclerView = findViewById(R.id.sentences_recycler_view);
        btnStartProcessing = findViewById(R.id.btn_start_processing);
        btnDeleteFile = findViewById(R.id.btn_delete_file);
        btnPlayAudio = findViewById(R.id.btn_play_audio);
        btnNextItem = findViewById(R.id.btn_next_item);

        sentencesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Retrieve data from the Intent
        String username = getIntent().getStringExtra("username");
        Uri originalInputFileUri = getIntent().getData(); // The text file selected by the user
        String rootFolderUriString = getIntent().getStringExtra("root_folder_uri"); // The root folder from MainActivity

        if (username != null) {
            usernameTextView.setText("Speaker: " + username);
        } else {
            usernameTextView.setText("Speaker: N/A");
        }

        if (originalInputFileUri != null && rootFolderUriString != null) {
            Uri rootFolderUri = Uri.parse(rootFolderUriString);
            DocumentFile rootDocument = DocumentFile.fromTreeUri(this, rootFolderUri);

            if (rootDocument != null && rootDocument.isDirectory()) {
                // Generate a unique folder name based on current date and time stamp
                String folderName = "TTS_Recording_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                Log.d("ProcessingActivity", "Attempting to create working folder: " + folderName);

                // Create the new working folder within the rootDocument
                workingFolderDocument = rootDocument.createDirectory(folderName);

                if (workingFolderDocument != null) {
                    Toast.makeText(this, "Working folder created: " + folderName, Toast.LENGTH_SHORT).show();
                    fileUriTextView.setText("Working Folder: " + workingFolderDocument.getName());

                    // Copy the text file into this new generated folder
                    copyInputFileToWorkingFolder(originalInputFileUri, workingFolderDocument);

                    DocumentFile selectedFile = DocumentFile.fromSingleUri(this, originalInputFileUri);
                    loadedFileNameTextView.setText("Loaded File: " + (selectedFile != null ? selectedFile.getName() : "N/A"));
                    readFileContentAndPopulateList(originalInputFileUri); // Read file and populate sentences

                } else {
                    Log.e("ProcessingActivity", "Failed to create working folder in " + rootFolderUri.toString());
                    Toast.makeText(this, "Failed to create working folder. Check permissions.", Toast.LENGTH_LONG).show();
                    handleInitializationError();
                }
            } else {
                Log.e("ProcessingActivity", "Root folder document is invalid or not a directory: " + rootFolderUriString);
                Toast.makeText(this, "Invalid root folder. Please re-select a folder in previous screen.", Toast.LENGTH_LONG).show();
                handleInitializationError();
            }
        } else {
            Log.e("ProcessingActivity", "Original input file URI or root folder URI is null.");
            Toast.makeText(this, "Missing input file or root folder information.", Toast.LENGTH_LONG).show();
            handleInitializationError();
        }

        // Set up button click listeners
        btnStartProcessing.setOnClickListener(v -> handleStartRecording());
        btnDeleteFile.setOnClickListener(v -> handleDeleteRecording());
        btnPlayAudio.setOnClickListener(v -> handlePlayAudio());
        btnNextItem.setOnClickListener(v -> handleNextSentence());

        updateProgressBar(); // Initial update of the progress bar
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
        updateProgressBar(); // Update progress bar even on error
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
            Log.e("ProcessingActivity", "Source file invalid: " + sourceUri);
            return;
        }

        try {
            // Create a new file in the target folder with the same name and MIME type
            DocumentFile newFileInWorkingFolder = targetFolder.createFile(sourceFile.getType(), sourceFile.getName());

            if (newFileInWorkingFolder != null) {
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = getContentResolver().openOutputStream(newFileInWorkingFolder.getUri())) {

                    if (in == null || out == null) {
                        Log.e("ProcessingActivity", "Failed to open streams for file copy.");
                        Toast.makeText(this, "Failed to open streams for file copy.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    byte[] buffer = new byte[4096]; // 4KB buffer
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Toast.makeText(this, "Input file copied to working folder!", Toast.LENGTH_SHORT).show();
                    Log.d("ProcessingActivity", "Input file copied to: " + newFileInWorkingFolder.getUri().toString());
                }
            } else {
                Toast.makeText(this, "Failed to create copy of input file.", Toast.LENGTH_LONG).show();
                Log.e("ProcessingActivity", "Failed to create copy of input file: " + sourceFile.getName());
            }
        } catch (IOException e) {
            Log.e("ProcessingActivity", "Error copying input file: " + e.getMessage(), e);
            Toast.makeText(this, "Error copying input file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e("ProcessingActivity", "Permission denied during file copy: " + e.getMessage(), e);
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
            updateProgressBar(); // Update progress after sentences are loaded
        } catch (Exception e) {
            Log.e("ProcessingActivity", "Error reading file content: " + e.getMessage(), e);
            currentSelectedSentenceTextView.setText("Error reading file: " + e.getMessage());
            Toast.makeText(this, "Failed to read file content.", Toast.LENGTH_LONG).show();
            updateProgressBar(); // Update progress even on error
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
        selectSentence(position);
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
     * Updates the enabled/disabled state of action buttons based on current selection.
     */
    private void updateButtonStates() {
        boolean isSentenceSelected = currentSentenceIndex != -1;
        SentenceItem selectedItem = isSentenceSelected ? sentenceItems.get(currentSentenceIndex) : null;
        boolean hasRecordedAudio = selectedItem != null && selectedItem.getRecordedFileName() != null;

        btnStartProcessing.setEnabled(isSentenceSelected);
        btnPlayAudio.setEnabled(hasRecordedAudio);
        btnDeleteFile.setEnabled(hasRecordedAudio);
        btnNextItem.setEnabled(isSentenceSelected && currentSentenceIndex < sentenceItems.size() - 1);
    }

    /**
     * Handles the "Start Recording" button click.
     * Simulates recording and updates the selected sentence with a recorded file path.
     */
    private void handleStartRecording() {
        if (currentSentenceIndex == -1) {
            Toast.makeText(this, "Please select a sentence to record.", Toast.LENGTH_SHORT).show();
            return;
        }

        SentenceItem selectedItem = sentenceItems.get(currentSentenceIndex);

        // New Logic: Prevent re-recording if a recording is already present
        if (selectedItem.getRecordedFileName() != null && !selectedItem.getRecordedFileName().isEmpty()) {
            Toast.makeText(this, "This sentence has already been recorded. Delete it first to re-record.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Recording: \"" + selectedItem.getText() + "\"", Toast.LENGTH_SHORT).show();

        if (workingFolderDocument != null) {
            if (workingFolderDocument.exists() && workingFolderDocument.isDirectory()) {
                // New Logic: Name of file should use index + 1
                String recordedFileName = "sentence_" + (selectedItem.getIndex() + 1) + "_" + System.currentTimeMillis() + ".mp3";

                // Simulate creating the file within the working folder
                DocumentFile newRecordedFile = workingFolderDocument.createFile("audio/mpeg", recordedFileName);

                if (newRecordedFile != null) {
                    Uri recordedFileUri = newRecordedFile.getUri();
                    selectedItem.setRecordedFile(recordedFileName, recordedFileUri);
                    sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                    Toast.makeText(this, "Recorded: " + recordedFileName, Toast.LENGTH_SHORT).show();
                    Log.d("ProcessingActivity", "Simulated recording saved to: " + recordedFileUri.toString());

                    updateProgressBar(); // Update progress bar after recording
                    handleNextSentence(); // Automatically move to the next sentence after recording
                } else {
                    Toast.makeText(this, "Failed to create recorded audio file.", Toast.LENGTH_LONG).show();
                    Log.e("ProcessingActivity", "Failed to create audio file in working folder.");
                }
            } else {
                Toast.makeText(this, "Working folder is no longer accessible.", Toast.LENGTH_LONG).show();
                Log.e("ProcessingActivity", "Working folder does not exist or is not a directory.");
            }
        } else {
            Toast.makeText(this, "Working folder not initialized.", Toast.LENGTH_SHORT).show();
            Log.e("ProcessingActivity", "Working folder document is null.");
        }
        updateButtonStates();
    }

    /**
     * Handles the "Delete" button click.
     * Clears the recorded audio path for the currently selected sentence and deletes the file.
     */
    private void handleDeleteRecording() {
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
                            Log.d("ProcessingActivity", "Deleted file: " + fileToDelete.getName());
                            updateProgressBar(); // Update progress bar after deletion
                        } else {
                            Toast.makeText(this, "Failed to delete recording.", Toast.LENGTH_SHORT).show();
                            Log.e("ProcessingActivity", "DocumentFile.delete() returned false for " + fileToDelete.getName());
                        }
                    } else {
                        Toast.makeText(this, "Recorded file not found.", Toast.LENGTH_SHORT).show();
                        Log.w("ProcessingActivity", "Attempted to delete non-existent file: " + selectedItem.getRecordedFileUri());
                        selectedItem.clearRecordedFile(); // Clear reference even if file not found
                        sentenceAdapter.notifyItemChanged(currentSentenceIndex);
                        updateProgressBar(); // Update progress bar even if file not found but reference cleared
                    }
                } catch (SecurityException e) {
                    Log.e("ProcessingActivity", "Permission denied to delete file: " + e.getMessage(), e);
                    Toast.makeText(this, "Permission denied to delete recording.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e("ProcessingActivity", "Error deleting recording: " + e.getMessage(), e);
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
