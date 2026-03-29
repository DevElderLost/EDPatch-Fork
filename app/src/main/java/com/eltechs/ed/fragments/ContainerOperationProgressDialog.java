package com.eltechs.ed.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.eltechs.ed.R;

public class ContainerOperationProgressDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "initial_message";
    private static final String ARG_TITLE = "title";

    private ProgressBar progressBar;
    private TextView tvMessage;

    public static ContainerOperationProgressDialog newInstance(String title, String initialMessage) {
        ContainerOperationProgressDialog frag = new ContainerOperationProgressDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, initialMessage);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments() != null ? getArguments().getString(ARG_TITLE) : "Operasi Container";
        String msg = getArguments() != null ? getArguments().getString(ARG_MESSAGE) : "Sedang memproses...";

        View view = LayoutInflater.from(getActivity()).inflate(2131427433, null);

        progressBar = view.findViewById(2131296500);
        tvMessage = view.findViewById(2131296452);

        if (progressBar != null) {
            progressBar.setIndeterminate(true);
        }
        if (tvMessage != null) {
            tvMessage.setText(msg);
        }

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setCancelable(false)
                .create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    // ─────────────────────────────────────────────
    // Method publik untuk mengontrol dialog dari luar
    // ─────────────────────────────────────────────

    public void updateMessage(final String message) {
        if (getActivity() == null || tvMessage == null) return;
        getActivity().runOnUiThread(() -> {
            if (tvMessage != null) {
                tvMessage.setText(message);
            }
        });
    }

    public void setDeterminate(int max) {
        if (getActivity() == null || progressBar == null) return;
        getActivity().runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setMax(max);
                progressBar.setProgress(0);
            }
        });
    }

    public void setProgress(int value) {
        if (getActivity() == null || progressBar == null) return;
        getActivity().runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setProgress(value);
            }
        });
    }

    /**
     * Menutup dialog ini (harus dipanggil dari Activity/Fragment ketika proses selesai)
     */
    public void finishOperation() {
        finishOperation("Selesai");
    }

    /**
     * Menutup dialog dengan pesan terakhir sebelum dismiss
     */
    public void finishOperation(final String finalMessage) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (tvMessage != null) {
                tvMessage.setText(finalMessage);
            }
            // Delay kecil agar user sempat melihat pesan "Selesai" (opsional)
            tvMessage.postDelayed(this::dismissSafely, 600);
        });
    }

    /**
     * Dismiss yang lebih aman untuk DialogFragment
     */
    private void dismissSafely() {
        if (isAdded() && !isDetached() && !isRemoving()) {
            try {
                dismissAllowingStateLoss();
            } catch (Exception e) {
                // sangat jarang terjadi, tapi aman saja
                if (isVisible()) {
                    dismiss();
                }
            }
        }
    }

    // Opsional: bisa dipanggil jika proses gagal
    public void finishWithError(final String errorMessage) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (tvMessage != null) {
                tvMessage.setText(errorMessage);
            }
            tvMessage.postDelayed(this::dismissSafely, 1200);
        });
    }
}