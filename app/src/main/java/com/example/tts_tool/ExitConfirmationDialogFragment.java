package com.example.tts_tool;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class ExitConfirmationDialogFragment extends DialogFragment {

    public interface ExitConfirmationListener {
        void onSaveAndExit();
        void onContinueRecording();
        void onExitWithoutSaving();
    }

    private ExitConfirmationListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (ExitConfirmationListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement ExitConfirmationListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_exit_confirmation, null);

        Button btnSaveExit = view.findViewById(R.id.btn_dialog_save_exit);
        Button btnContinue = view.findViewById(R.id.btn_dialog_continue);
        Button btnExitWithoutSaving = view.findViewById(R.id.btn_dialog_exit_without_saving);

        btnSaveExit.setOnClickListener(v -> {
            listener.onSaveAndExit();
            dismiss();
        });

        btnContinue.setOnClickListener(v -> {
            listener.onContinueRecording();
            dismiss();
        });

        btnExitWithoutSaving.setOnClickListener(v -> {
            listener.onExitWithoutSaving();
            dismiss();
        });

        builder.setView(view);
        return builder.create();
    }
}
