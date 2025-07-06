package com.example.tts_tool;

import android.net.Uri; // Import Uri for recordedFilePath

/**
 * Data class to represent a single sentence from the input text file.
 */
public class SentenceItem {
    private int index; // Unique index of the sentence
    private String text; // The actual sentence text
    private String recordedFileName; // Name of the recorded audio file for this sentence (e.g., "audio_12345.mp3")
    private Uri recordedFileUri; // Full URI of the recorded audio file for this sentence
    private boolean isSelected; // To manage selection state in the RecyclerView

    public SentenceItem(int index, String text) {
        this.index = index;
        this.text = text;
        this.recordedFileName = null; // Initially no recording
        this.recordedFileUri = null;
        this.isSelected = false; // Not selected by default
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }

    public String getRecordedFileName() {
        return recordedFileName;
    }

    public Uri getRecordedFileUri() {
        return recordedFileUri;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setRecordedFile(String fileName, Uri fileUri) {
        this.recordedFileName = fileName;
        this.recordedFileUri = fileUri;
    }

    public void clearRecordedFile() {
        this.recordedFileName = null;
        this.recordedFileUri = null;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
