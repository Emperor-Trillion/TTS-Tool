package com.example.tts_tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SentenceAdapter extends RecyclerView.Adapter<SentenceAdapter.SentenceViewHolder> {

    private List<SentenceItem> sentenceList;
    private OnItemClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION; // Track selected item

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public SentenceAdapter(List<SentenceItem> sentenceList, OnItemClickListener listener) {
        this.sentenceList = sentenceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SentenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_sentence, parent, false);
        return new SentenceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SentenceViewHolder holder, int position) {
        SentenceItem sentenceItem = sentenceList.get(position);

        holder.sentenceTextView.setText(sentenceItem.getText());

        // Display recorded file name if available, otherwise "No audio yet"
        if (sentenceItem.getRecordedFileName() != null && !sentenceItem.getRecordedFileName().isEmpty()) {
            holder.recordedAudioPathTextView.setText("Recorded: " + sentenceItem.getRecordedFileName());
            holder.recordedAudioPathTextView.setVisibility(View.VISIBLE);
        } else {
            holder.recordedAudioPathTextView.setText("Recorded: No audio yet");
            holder.recordedAudioPathTextView.setVisibility(View.VISIBLE); // Keep visible, or GONE if you prefer
        }

        // Set selection state
        holder.itemView.setSelected(selectedPosition == position);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sentenceList.size();
    }

    /**
     * Updates the selected item position and notifies the adapter for UI refresh.
     * @param position The new selected position.
     */
    public void setSelectedPosition(int position) {
        if (selectedPosition != position) {
            int oldSelected = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(oldSelected); // Deselect old item
            notifyItemChanged(selectedPosition); // Select new item
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public static class SentenceViewHolder extends RecyclerView.ViewHolder {
        TextView sentenceTextView;
        TextView recordedAudioPathTextView;

        public SentenceViewHolder(@NonNull View itemView) {
            super(itemView);
            sentenceTextView = itemView.findViewById(R.id.sentence_text_view);
            recordedAudioPathTextView = itemView.findViewById(R.id.recorded_audio_path_text_view);
        }
    }
}
