// app/src/main/java/com/example/tts_tool/FileAdapter.java
package com.example.tts_tool;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<DocumentFile> fileList;
    private OnItemClickListener clickListener; // Changed name to avoid conflict
    private OnItemLongClickListener longClickListener; // New long click listener
    private List<Integer> selectedPositions; // To keep track of selected items for deletion

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemLongClickListener { // New interface for long click
        void onItemLongClick(int position);
    }

    public FileAdapter(List<DocumentFile> fileList, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        this.fileList = fileList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.selectedPositions = new ArrayList<>();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        DocumentFile currentFile = fileList.get(position);
        holder.fileNameTextView.setText(currentFile.getName());

        if (currentFile.isDirectory()) {
            holder.fileIconImageView.setImageResource(R.drawable.ic_folder); // Assuming you have an ic_folder drawable
        } else {
            holder.fileIconImageView.setImageResource(R.drawable.ic_file); // Assuming you have an ic_file drawable
        }

        // Highlight selected items
        if (selectedPositions.contains(position)) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.selected_item_background));
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent));
        }
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(Integer.valueOf(position));
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position); // Only update the changed item
    }

    public void clearSelection() {
        List<Integer> oldSelectedPositions = new ArrayList<>(selectedPositions);
        selectedPositions.clear();
        for (Integer position : oldSelectedPositions) {
            notifyItemChanged(position); // Update all previously selected items
        }
    }

    public List<DocumentFile> getSelectedItems() {
        List<DocumentFile> selectedItems = new ArrayList<>();
        for (Integer position : selectedPositions) {
            if (position < fileList.size()) { // Ensure position is still valid
                selectedItems.add(fileList.get(position));
            }
        }
        return selectedItems;
    }

    public class FileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        public ImageView fileIconImageView;
        public TextView fileNameTextView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIconImageView = itemView.findViewById(R.id.file_icon_image_view);
            fileNameTextView = itemView.findViewById(R.id.file_name_text_view);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this); // Set long click listener
        }

        @Override
        public void onClick(View v) {
            if (clickListener != null) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    clickListener.onItemClick(position);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (longClickListener != null) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    longClickListener.onItemLongClick(position);
                    return true; // Consume the long click event
                }
            }
            return false;
        }
    }
}
