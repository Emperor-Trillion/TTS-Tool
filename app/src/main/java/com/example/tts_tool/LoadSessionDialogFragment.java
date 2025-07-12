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
import com.google.firebase.firestore.FirebaseFirestoreException; // Import for Firestore exceptions

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
    private static final String ARG_USER_ID = "userId";

    /**
     * Factory method to create a new instance of LoadSessionDialogFragment
     * and pass the authenticated user ID.
     * @param userId The authenticated Firebase User ID.
     * @return A new instance of LoadSessionDialogFragment.
     */
    public static LoadSessionDialogFragment newInstance(String userId) {
        LoadSessionDialogFragment fragment = new LoadSessionDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnSessionSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnSessionSelectedListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retrieve userId from arguments
        if (getArguments() != null) {
            currentUserId = getArguments().getString(ARG_USER_ID);
            Log.d(TAG, "onCreate: Received userId: " + (currentUserId != null ? currentUserId : "null"));
        } else {
            Log.e(TAG, "onCreate: No arguments bundle found for userId.");
        }

        // Initialize Firebase (ensure this is done only once per app lifecycle, typically in Application class)
        // This check is for robustness in case it's not.
        if (FirebaseApp.getApps(requireContext()).isEmpty()) {
            try {
                FirebaseApp.initializeApp(requireContext());
                Log.d(TAG, "onCreate: FirebaseApp initialized within dialog.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "onCreate: FirebaseApp already initialized or cannot be initialized here.", e);
            }
        }
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        Log.d(TAG, "onCreate: Firebase instances obtained.");
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

        Log.d(TAG, "onCreateDialog: currentUserId at dialog creation: " + (currentUserId != null ? currentUserId : "null"));
        if (currentUserId != null) {
            fetchAndDisplaySessions(loadSessionRecyclerView);
        } else {
            Log.e(TAG, "onCreateDialog: User ID is null. Cannot fetch sessions. Authentication likely failed or not passed from calling activity.");
            Toast.makeText(requireContext(), "User not authenticated. Cannot load sessions.", Toast.LENGTH_LONG).show();
            dismiss(); // Dismiss dialog if userId is not available
        }

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        return builder.create();
    }

    private void fetchAndDisplaySessions(RecyclerView recyclerView) {
        if (currentUserId == null) {
            Log.e(TAG, "fetchAndDisplaySessions: Current user ID is null. Cannot fetch sessions. Dismissing dialog. (Redundant check but good for safety)");
            Toast.makeText(requireContext(), "User not authenticated. Cannot load sessions.", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }
        Log.d(TAG, "fetchAndDisplaySessions: Attempting to fetch sessions for userId: " + currentUserId);

        List<ProcessingActivity.SessionState> savedSessions = new ArrayList<>();
        LoadSessionAdapter loadSessionAdapter = new LoadSessionAdapter(savedSessions, sessionState -> {
            Log.d(TAG, "Session selected: " + (sessionState != null ? sessionState.getSessionId() : "null"));
            if (listener != null) {
                listener.onSessionSelected(sessionState);
            }
            dismiss();
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
                        Log.d(TAG, "fetchAndDisplaySessions: Fetch successful. Documents found: " + task.getResult().size());
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                // Attempt to deserialize the document
                                ProcessingActivity.SessionState session = document.toObject(ProcessingActivity.SessionState.class);
                                if (session != null) {
                                    savedSessions.add(session);
                                    Log.d(TAG, "Fetched session: " + session.getSessionId() + " (Last Modified: " + session.getLastModified() + ")");
                                } else {
                                    Log.w(TAG, "Skipping null session object from Firestore document (deserialization failed or data incomplete): " + document.getId());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error deserializing session document " + document.getId() + ": " + e.getMessage(), e);
                                Toast.makeText(requireContext(), "Error processing session data: " + document.getId(), Toast.LENGTH_SHORT).show();
                            }
                        }
                        Collections.sort(savedSessions, (s1, s2) -> Long.compare(s2.getLastModified(), s1.getLastModified()));
                        loadSessionAdapter.notifyDataSetChanged();

                        if (savedSessions.isEmpty()) {
                            Toast.makeText(requireContext(), "No saved sessions found for this user.", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "No saved sessions found for userId: " + currentUserId);
                        } else {
                            Log.d(TAG, "Displaying " + savedSessions.size() + " saved sessions.");
                        }
                    } else {
                        // Handle Firestore specific errors
                        if (task.getException() instanceof FirebaseFirestoreException) {
                            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) task.getException();
                            Log.e(TAG, "FirebaseFirestoreException fetching saved sessions: " + firestoreException.getCode() + " - " + firestoreException.getMessage(), firestoreException);
                            Toast.makeText(requireContext(), "Firestore error: " + firestoreException.getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            Log.e(TAG, "General error fetching saved sessions: " + task.getException().getMessage(), task.getException());
                            Toast.makeText(requireContext(), "Error loading sessions: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
