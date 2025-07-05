package com.example.tts_tool;

import androidx.documentfile.provider.DocumentFile;

/**
 * A data class to hold information about a file or folder for display in the RecyclerView.
 */
public class FileItem {
    private String name;
    private DocumentFile documentFile;
    private boolean isDirectory;

    public FileItem(String name, DocumentFile documentFile, boolean isDirectory) {
        this.name = name;
        this.documentFile = documentFile;
        this.isDirectory = isDirectory;
    }

    public String getName() {
        return name;
    }

    public DocumentFile getDocumentFile() {
        return documentFile;
    }

    public boolean isDirectory() {
        return isDirectory;
    }
}