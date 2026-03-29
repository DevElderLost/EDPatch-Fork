package com.eltechs.ed.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.axs.helpers.AndroidHelpers;
import com.eltechs.ed.R;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CncDdrawInstallDialogFragment extends DialogFragment {

    private static final String ARG_EXE_PATH = "exe_path";

    private ProgressBar progressBar;
    private TextView stepDescription;

    public static CncDdrawInstallDialogFragment newInstance(String exePath) {
        CncDdrawInstallDialogFragment frag = new CncDdrawInstallDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXE_PATH, exePath);
        frag.setArguments(args);           // ini sekarang OK
        return frag;
    }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    String exePath = getArguments() != null ? getArguments().getString(ARG_EXE_PATH) : null;
    if (exePath == null) {
        dismiss();
        return new AlertDialog.Builder(getActivity()).create();
    }

    File targetExe = new File(exePath);
    final File destFolder = targetExe.getParentFile();

    if (destFolder == null) {
        dismiss();
        return new AlertDialog.Builder(getActivity()).create();
    }

    View view = LayoutInflater.from(getActivity()).inflate(
            2131427433,   // ganti dengan layout XML yang benar, jangan pakai ID numerik
            null
    );

    progressBar = view.findViewById(2131296500);     // ganti dengan ID yang sesuai
    stepDescription = view.findViewById(2131296452);

    if (progressBar != null) {
        progressBar.setIndeterminate(true);
    }
    if (stepDescription != null) {
        stepDescription.setText("Menyiapkan cnc-ddraw...");
    }

    AlertDialog dialog = new AlertDialog.Builder(getActivity())
            .setView(view)
            .setCancelable(false)
            .create();

    // ← Bagian penting: hilangkan title bar
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

    // Mulai ekstraksi setelah dialog siap
    startExtraction(destFolder);

    return dialog;
  }

    private void startExtraction(final File destFolder) {
        // GuestContainersManager.getHostPath() bukan static → butuh instance
        // Di sini kita ambil context dari activity atau fragment
        GuestContainersManager manager = new GuestContainersManager(getActivity());
        File zipSource = new File(manager.getHostPath("/opt/packages/cnc-ddraw.zip"));

        if (!zipSource.exists() || !zipSource.isFile()) {
            showErrorAndDismiss("File cnc-ddraw.zip tidak ditemukan");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    updateUI("Mengekstrak file...");
                    extractZip(zipSource, destFolder);
                    updateUI("Selesai!");

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getActivity(), "cnc-ddraw berhasil diinstall", Toast.LENGTH_LONG).show();
                                dismiss();

                                // Refresh pemanggil (ChooseFileFragment)
                                Fragment target = getTargetFragment();
                                if (target instanceof ChooseFileFragment) {
                                    // Jika reloadDirectory() private → buat public atau gunakan interface/callback
                                    ((ChooseFileFragment) target).reloadDirectory();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    showErrorAndDismiss("Gagal mengekstrak: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateUI(final String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (stepDescription != null) {
                        stepDescription.setText(message);
                    }
                }
            });
        }
    }

    private void showErrorAndDismiss(final String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                    dismiss();
                }
            });
        }
    }

    private void extractZip(File zipFile, File destinationDir) throws IOException {
        byte[] buffer = new byte[8192];

        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File newFile = new File(destinationDir, zipEntry.getName());

                String canonicalPath = newFile.getCanonicalPath();
                String canonicalDest = destinationDir.getCanonicalPath();
                if (!canonicalPath.startsWith(canonicalDest + File.separator)) {
                    throw new IOException("Entry berada di luar folder tujuan: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Gagal membuat folder: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Gagal membuat folder parent: " + parent);
                    }

                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(newFile);
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    } finally {
                        if (fos != null) fos.close();
                    }
                }
                zis.closeEntry();
            }
        } finally {
            if (zis != null) zis.close();
        }
    }
}