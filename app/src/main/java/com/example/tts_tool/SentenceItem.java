package com.example.tts_tool;

import android.net.Uri;

/**
 * A data class to hold information about a sentence, including its recording status.
 */
public class SentenceItem {
    private int index;
    private String text;
    private String recordedFileName; // Stores the name of the recorded audio file
    private String recordedFileUriString; // Stores the URI as a String for serialization
    private boolean isSelected; // To indicate if the sentence is currently selected in the UI

    // Default constructor for Firestore deserialization
    public SentenceItem() {
    }

    public SentenceItem(int index, String text) {
        this.index = index;
        this.text = text;
        this.recordedFileName = null;
        this.recordedFileUriString = null;
        this.isSelected = false;
    }

    // Method to set recorded file info, converting Uri to String
    public void setRecordedFile(String fileName, Uri fileUri) {
        this.recordedFileName = fileName;
        this.recordedFileUriString = fileUri != null ? fileUri.toString() : null; // Handle null Uri
    }

    // Method to clear recorded file info
    public void clearRecordedFile() {
        this.recordedFileName = null;
        this.recordedFileUriString = null;
    }

    // Getters
    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }

    public String getRecordedFileName() {
        return recordedFileName;
    }

    // New getter for recorded file URI as String (for Firestore)
    public String getRecordedFileUriString() {
        return recordedFileUriString;
    }

    public boolean isSelected() {
        return isSelected;
    }

    // Setters (needed for Firestore deserialization)
    public void setIndex(int index) {
        this.index = index;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setRecordedFileName(String recordedFileName) {
        this.recordedFileName = recordedFileName;
    }

    // New setter for recorded file URI as String (for Firestore)
    public void setRecordedFileUriString(String recordedFileUriString) {
        this.recordedFileUriString = recordedFileUriString;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    // Helper to get Uri object from String (for use in MediaPlayer, etc.)
    public Uri getRecordedFileUri() {
        return recordedFileUriString != null ? Uri.parse(recordedFileUriString) : null;
    }
}
