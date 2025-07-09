package com.example.tts_tool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date; // Import Date class
import java.util.List;
import java.util.Locale;

public class LoadSessionAdapter extends RecyclerView.Adapter<LoadSessionAdapter.SessionViewHolder> {

    private List<ProcessingActivity.SessionState> sessionList;
    private OnSessionSelectedListener listener;

    public interface OnSessionSelectedListener {
        void onSessionSelected(ProcessingActivity.SessionState sessionState);
    }

    public LoadSessionAdapter(List<ProcessingActivity.SessionState> sessionList, OnSessionSelectedListener listener) {
        this.sessionList = sessionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_load_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ProcessingActivity.SessionState session = sessionList.get(position);
        holder.sessionNameTextView.setText(session.getSessionId()); // Display the session ID as the name

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String lastModifiedDate = sdf.format(new Date(session.getLastModified()));
        holder.lastModifiedTextView.setText("Last Modified: " + lastModifiedDate);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionSelected(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView sessionNameTextView;
        TextView lastModifiedTextView;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            sessionNameTextView = itemView.findViewById(R.id.text_session_name);
            lastModifiedTextView = itemView.findViewById(R.id.text_last_modified);
        }
    }
}
