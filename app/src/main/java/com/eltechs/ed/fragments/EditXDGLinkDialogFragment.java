package com.eltechs.ed.fragments;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;

import com.eltechs.ed.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditXDGLinkDialogFragment extends DialogFragment {

    private static final String ARG_DESKTOP_PATH = "arg_desktop_path";

    private File mDesktopFile;
    private EditText[] mEditTexts;

    public interface OnEditSavedListener {
        void onXDGLinkEdited();
    }

    private OnEditSavedListener mListener;

    public void setOnEditSavedListener(OnEditSavedListener listener) {
        this.mListener = listener;
    }

    public static EditXDGLinkDialogFragment createDialog(String desktopPath) {
        EditXDGLinkDialogFragment fragment = new EditXDGLinkDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DESKTOP_PATH, desktopPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String path = getArguments().getString(ARG_DESKTOP_PATH);
            if (path != null) {
                mDesktopFile = new File(path);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (mDesktopFile == null || !mDesktopFile.exists() || !mDesktopFile.isFile()) {
            Toast.makeText(getActivity(), "File .desktop tidak valid", Toast.LENGTH_SHORT).show();

            return new AlertDialog.Builder(getActivity())
                    .setMessage("Tidak dapat membuka file")
                    .setPositiveButton("OK", null)
                    .create();
        }

        // Root layout (vertical)
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 20);

        // ScrollView sebagai pembungkus konten agar bisa discroll
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Container di dalam ScrollView
        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 16);

        Map<String, String> values = parseDesktopFile(mDesktopFile);

        String[] keys = {"Name", "Exec", "Type", "StartupNotify", "Path", "Icon", "StartupWMClass"};
        mEditTexts = new EditText[keys.length];

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String value = values.getOrDefault(key, "");

            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);

            TextView label = new TextView(getActivity());
            label.setText(key + ": ");
            label.setTextSize(16);
            label.setTextColor(Color.BLACK);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            label.setPadding(0, 0, 16, 0);

            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f);
            label.setLayoutParams(labelParams);

            EditText edit = new EditText(getActivity());
            edit.setText(value);
            edit.setHint("Masukkan " + key);
            edit.setTextSize(15);

            // ─── Perubahan utama: single line ───
            edit.setSingleLine(true);
            edit.setMaxLines(1);
            edit.setEllipsize(TextUtils.TruncateAt.END);

            edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            // Agar navigasi antar field lebih nyaman di keyboard
            edit.setImeOptions(EditorInfo.IME_ACTION_NEXT);

            edit.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            edit.setBackgroundResource(android.R.drawable.edit_text);

            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 7f);
            edit.setLayoutParams(editParams);

            mEditTexts[i] = edit;

            row.addView(label);
            row.addView(edit);
            content.addView(row);
        }

        scrollView.addView(content);
        root.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle("Edit Shortcut")
                .setView(root)
                .setPositiveButton("Simpan", (d, which) -> saveChanges(keys))
                .setNegativeButton("Batal", null)
                .create();

        // Memastikan dialog memanfaatkan lebar layar dengan baik
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
        });

        return dialog;
    }

    private Map<String, String> parseDesktopFile(File file) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inEntry = false;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.equals("[Desktop Entry]")) {
                    inEntry = true;
                    continue;
                }
                if (inEntry && line.startsWith("[")) {
                    inEntry = false;
                }
                if (inEntry && line.contains("=") && !line.startsWith("#")) {
                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim();
                        String val = line.substring(eqIndex + 1).trim();
                        map.put(key, val);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private void saveChanges(String[] keys) {
        try {
            List<String> lines = Files.readAllLines(mDesktopFile.toPath(), StandardCharsets.UTF_8);
            Map<String, String> newValues = new HashMap<>();

            for (int i = 0; i < keys.length; i++) {
                String val = mEditTexts[i].getText().toString().trim();
                if (!val.isEmpty()) {
                    newValues.put(keys[i], val);
                }
            }

            List<String> updatedLines = new ArrayList<>();
            boolean inSection = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equals("[Desktop Entry]")) {
                    inSection = true;
                    updatedLines.add(line);
                    continue;
                }
                if (inSection && trimmed.startsWith("[")) {
                    inSection = false;
                }
                if (inSection && trimmed.contains("=")) {
                    String key = trimmed.split("=", 2)[0].trim();
                    if (newValues.containsKey(key)) {
                        updatedLines.add(key + "=" + newValues.remove(key));
                        continue;
                    }
                }
                updatedLines.add(line);
            }

            // Tambahkan field baru yang belum ada
            for (Map.Entry<String, String> entry : newValues.entrySet()) {
                updatedLines.add(entry.getKey() + "=" + entry.getValue());
            }

            Files.write(mDesktopFile.toPath(), updatedLines, StandardCharsets.UTF_8);

            if (mListener != null) {
                mListener.onXDGLinkEdited();
            }

            dismiss();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}