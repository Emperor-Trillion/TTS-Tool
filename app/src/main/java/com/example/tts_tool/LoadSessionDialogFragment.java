// app/src/main/java/com/example/tts_tool/LoadSessionDialogFragment.java
package com.example.tts_tool;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoadSessionDialogFragment extends DialogFragment {

    public interface OnSessionSelectedListener {
        void onSessionSelected(ProcessingActivity.SessionState sessionState);
    }

    private OnSessionSelectedListener listener;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;

    private static final String TAG = "LoadSessionDialog";
    private static final String FIRESTORE_COLLECTION_SESSIONS = "sessions";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnSessionSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnSessionSelectedListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Load Session");

        View viewInflated = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_load_session, null, false);
        RecyclerView loadSessionRecyclerView = viewInflated.findViewById(R.id.load_session_recycler_view);
        loadSessionRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        builder.setView(viewInflated);

        // Initialize Firebase (ensure it's initialized if not already in Application class)
        if (FirebaseApp.getApps(requireContext()).isEmpty()) {
            FirebaseApp.initializeApp(requireContext());
        }
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Get current user ID, ensuring authentication has happened
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
            fetchAndDisplaySessions(loadSessionRecyclerView);
        } else {
            // Attempt anonymous sign-in if not already authenticated
            mAuth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                    currentUserId = mAuth.getCurrentUser().getUid();
                    Log.d(TAG, "Anonymous auth successful for loading sessions: " + currentUserId);
                    fetchAndDisplaySessions(loadSessionRecyclerView);
                } else {
                    Log.e(TAG, "Anonymous auth failed for loading sessions.", task.getException());
                    Toast.makeText(requireContext(), "Authentication required to load sessions.", Toast.LENGTH_SHORT).show();
                    dismiss(); // Dismiss dialog if authentication fails
                }
            });
        }

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        return builder.create();
    }

    private void fetchAndDisplaySessions(RecyclerView recyclerView) {
        if (currentUserId == null) {
            Log.e(TAG, "Current user ID is null. Cannot fetch sessions.");
            Toast.makeText(requireContext(), "User not authenticated. Cannot load sessions.", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        List<ProcessingActivity.SessionState> savedSessions = new ArrayList<>();
        LoadSessionAdapter loadSessionAdapter = new LoadSessionAdapter(savedSessions, sessionState -> {
            if (listener != null) {
                listener.onSessionSelected(sessionState);
            }
            dismiss(); // Dismiss the dialog after selection
        });
        recyclerView.setAdapter(loadSessionAdapter);

        String appId = requireContext().getPackageName();
        db.collection("artifacts")
                .document(appId)
                .collection("users")
                .document(currentUserId)
                .collection(FIRESTORE_COLLECTION_SESSIONS)
                .orderBy("lastModified", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        savedSessions.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            ProcessingActivity.SessionState session = document.toObject(ProcessingActivity.SessionState.class);
                            savedSessions.add(session);
                        }
                        // Sorting locally just in case, though Firestore orderBy should handle it
                        Collections.sort(savedSessions, (s1, s2) -> Long.compare(s2.getLastModified(), s1.getLastModified()));
                        loadSessionAdapter.notifyDataSetChanged();

                        if (savedSessions.isEmpty()) {
                            Toast.makeText(requireContext(), "No saved sessions found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Error fetching saved sessions: " + task.getException());
                        Toast.makeText(requireContext(), "Error loading sessions: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
