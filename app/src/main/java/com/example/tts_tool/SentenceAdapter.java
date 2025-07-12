// app/src/main/java/com/example/tts_tool/SentenceAdapter.java
package com.example.tts_tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SentenceAdapter extends RecyclerView.Adapter<SentenceAdapter.SentenceViewHolder> {

    private List<ProcessingActivity.SentenceItem> sentenceList;
    private OnItemClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public SentenceAdapter(List<ProcessingActivity.SentenceItem> sentenceList, OnItemClickListener listener) {
        this.sentenceList = sentenceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SentenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sentence, parent, false);
        return new SentenceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SentenceViewHolder holder, int position) {
        ProcessingActivity.SentenceItem currentItem = sentenceList.get(position);
        holder.sentenceTextView.setText(currentItem.getText());

        // Highlight selected item
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.selected_item_background));
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent));
        }

        // Indicate if recorded and display file name
        if (currentItem.getRecordedFileUri() != null && currentItem.getRecordedFileName() != null) {
            holder.sentenceTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.recorded_sentence_text));
            holder.recordedFileNameTextView.setText("Recorded: " + currentItem.getRecordedFileName());
            holder.recordedFileNameTextView.setVisibility(View.VISIBLE);
        } else {
            holder.sentenceTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.unrecorded_sentence_text));
            holder.recordedFileNameTextView.setVisibility(View.GONE);
            holder.recordedFileNameTextView.setText(""); // Clear text when not visible
        }
    }

    @Override
    public int getItemCount() {
        return sentenceList.size();
    }

    public void updateData(List<ProcessingActivity.SentenceItem> newSentenceList) {
        this.sentenceList.clear();
        this.sentenceList.addAll(newSentenceList);
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition);
        }
        notifyItemChanged(selectedPosition);
    }

    public class SentenceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView sentenceTextView;
        public TextView recordedFileNameTextView; // Declare the new TextView

        public SentenceViewHolder(@NonNull View itemView) {
            super(itemView);
            sentenceTextView = itemView.findViewById(R.id.text_view_sentence);
            recordedFileNameTextView = itemView.findViewById(R.id.text_view_recorded_file_name); // Find the new TextView by ID
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(position);
                }
            }
        }
    }
}