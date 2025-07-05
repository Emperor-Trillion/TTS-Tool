package com.example.tts_tool;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Import ImageView
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<FileItem> fileItems;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }

    public FileListAdapter(List<FileItem> fileItems, OnItemClickListener listener) {
        this.fileItems = fileItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout for each list item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_file, parent, false); // Use your new custom layout
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem fileItem = fileItems.get(position);
        holder.fileNameTextView.setText(fileItem.getName()); // No longer prefixing here, icon will distinguish

        // Set the appropriate icon based on whether it's a directory or a file
        if (fileItem.isDirectory()) {
            holder.fileIcon.setImageResource(R.drawable.iconn); // Built-in folder icon (you can replace this)
        } else {
            holder.fileIcon.setImageResource(android.R.drawable.ic_menu_agenda); // Built-in generic file icon (you can replace this)
        }

        // Set the click listener for each item
        holder.itemView.setOnClickListener(v -> {
            Log.d("FileListAdapter", "Item view clicked: " + fileItem.getName());
            if (listener != null) {
                listener.onItemClick(fileItem);
            }
        });
        Log.d("FileListAdapter", "Listener set for item: " + fileItem.getName() + " at position: " + position);
    }

    @Override
    public int getItemCount() {
        return fileItems.size();
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        ImageView fileIcon; // Reference to the ImageView

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.file_name_text_view); // Reference your TextView from custom layout
            fileIcon = itemView.findViewById(R.id.file_icon); // Reference your ImageView from custom layout
        }
    }
}
