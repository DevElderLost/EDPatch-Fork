package com.eltechs.ed.fragments;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.eltechs.ed.ContainerPackage;
import com.eltechs.ed.R;
import com.eltechs.axs.applicationState.ExagearImageAware;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChoosePackagesDFragment extends DialogFragment {

    private OnPackagesSelectedListener mListener;
    private final List<ContainerPackage> mSelectedItems = new ArrayList<>();
    private List<ContainerPackage> mPackageList;
    private ArrayAdapter<ContainerPackage> mAdapter;

    private static final String RELATIVE_PATH = "opt/rcp/morepackages";

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnPackagesSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnPackagesSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reloadPackageList();
    }

    private void reloadPackageList() {
        mPackageList = ContainerPackage.getAllPackages();

        if (mPackageList.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Tidak ada paket ditemukan di morepackages",
                    Toast.LENGTH_LONG).show();
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LinearLayout titleLayout = new LinearLayout(requireContext());
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(24, 16, 24, 12);

        TextView titleText = new TextView(requireContext());
        titleText.setText("Pilih Paket");
        titleText.setTextSize(20);
        titleText.setTextColor(Color.WHITE);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton addButton = new ImageButton(requireContext());
        addButton.setImageResource(2131230875);
        addButton.setBackground(null);
        addButton.setPadding(16, 16, 16, 16);
        addButton.setOnClickListener(v -> showAddPackageDialog());

        titleLayout.addView(titleText);
        titleLayout.addView(addButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setCustomTitle(titleLayout);

        mAdapter = new ArrayAdapter<ContainerPackage>(
                requireContext(), android.R.layout.simple_list_item_1, mPackageList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                ContainerPackage pkg = getItem(position);

                LinearLayout root = new LinearLayout(requireContext());
                root.setOrientation(LinearLayout.HORIZONTAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                root.setPadding(24, 16, 24, 16);

                TextView textView = new TextView(requireContext());
                textView.setText(pkg.mDisplayName);
                textView.setTextSize(17);
                textView.setTextColor(Color.WHITE);
                textView.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                Switch switchWidget = new Switch(requireContext());
                switchWidget.setChecked(mSelectedItems.contains(pkg));
                switchWidget.setTag(position);

                switchWidget.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int pos = (Integer) buttonView.getTag();
                    ContainerPackage p = getItem(pos);

                    if (isChecked) {
                        if (!mSelectedItems.contains(p)) mSelectedItems.add(p);
                    } else {
                        mSelectedItems.remove(p);
                    }

                    AlertDialog dialog = (AlertDialog) getDialog();
                    if (dialog != null && dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                              .setEnabled(!mSelectedItems.isEmpty());
                    }
                });

                root.addView(textView);
                root.addView(switchWidget);
                return root;
            }
        };

        builder.setAdapter(mAdapter, null);

        builder.setPositiveButton("OK", (dialog, which) -> {
            mListener.onPackagesSelected(new ArrayList<>(mSelectedItems));
            dismiss();
        });

        builder.setNegativeButton("Batal", (dialog, which) -> dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!mSelectedItems.isEmpty());
        });

        return dialog;
    }

    private void showAddPackageDialog() {
        LinearLayout mainLayout = new LinearLayout(requireContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 20, 24, 24);

        EditText etDisplay = createEditText("Nama Tampilan", InputType.TYPE_CLASS_TEXT);
        EditText etInternal = createEditText("Nama Internal ($2)", InputType.TYPE_CLASS_TEXT);
        EditText etProgress = createEditText("Teks Progress", InputType.TYPE_CLASS_TEXT);
        EditText etSleep = createEditText("Sleep (detik)", InputType.TYPE_CLASS_NUMBER);

        mainLayout.addView(etDisplay);
        mainLayout.addView(etInternal);
        mainLayout.addView(etProgress);
        mainLayout.addView(etSleep);

        LinearLayout commandsContainer = new LinearLayout(requireContext());
        commandsContainer.setOrientation(LinearLayout.VERTICAL);
        commandsContainer.setPadding(0, 20, 0, 0);
        commandsContainer.addView(createCommandRow(commandsContainer));

        mainLayout.addView(commandsContainer);

        new AlertDialog.Builder(requireContext())
                .setTitle("Tambah Paket Baru")
                .setView(mainLayout)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String display = etDisplay.getText().toString().trim();
                    String internal = etInternal.getText().toString().trim();
                    String progress = etProgress.getText().toString().trim();
                    String sleepStr = etSleep.getText().toString().trim();

                    if (display.isEmpty() || internal.isEmpty() || progress.isEmpty() || sleepStr.isEmpty()) {
                        Toast.makeText(requireContext(), "Semua field wajib diisi", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int sleep;
                    try {
                        sleep = Integer.parseInt(sleepStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Sleep harus angka", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> commands = new ArrayList<>();
                    for (int i = 0; i < commandsContainer.getChildCount(); i++) {
                        LinearLayout row = (LinearLayout) commandsContainer.getChildAt(i);
                        EditText et = (EditText) row.getChildAt(0);
                        String cmd = et.getText().toString().trim();
                        if (!cmd.isEmpty()) commands.add(cmd);
                    }

                    if (commands.isEmpty()) {
                        Toast.makeText(requireContext(), "Minimal satu command diperlukan", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    appendNewIfBlock(internal, display, progress, commands, sleep);

                    reloadPackageList();

                    Toast.makeText(requireContext(), "Paket ditambahkan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private EditText createEditText(String hint, int inputType) {
        EditText et = new EditText(requireContext());
        et.setHint(hint);
        et.setInputType(inputType);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 12, 0, 0);
        et.setLayoutParams(params);
        return et;
    }

    private LinearLayout createCommandRow(LinearLayout container) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 0);

        EditText etCommand = createEditText("Command (contoh: wget ...)", InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        etCommand.setLayoutParams(etParams);

        ImageButton btnAdd = new ImageButton(requireContext());
        btnAdd.setImageResource(2131230875);
        btnAdd.setBackground(null);
        btnAdd.setPadding(16, 16, 16, 16);
        btnAdd.setOnClickListener(v -> container.addView(createCommandRow(container)));

        row.addView(etCommand);
        row.addView(btnAdd);

        return row;
    }

    private void appendNewIfBlock(String internalName, String displayName,
                                  String progressText, List<String> commands, int sleepSeconds) {
        ExagearImageAware imageAware;
        try {
            imageAware = (ExagearImageAware) Globals.getApplicationState();
        } catch (Exception e) {
            Log.e("PkgFrag", "Gagal mendapatkan ExagearImageAware", e);
            Toast.makeText(requireContext(), "Gagal mengakses container image", Toast.LENGTH_LONG).show();
            return;
        }

        File exagearImageDir = imageAware.getExagearImage().getPath();
        File file = new File(exagearImageDir, RELATIVE_PATH);

        Log.d("PkgFrag", "Menulis ke: " + file.getAbsolutePath());

        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                Log.e("PkgFrag", "Gagal membuat direktori parent");
                Toast.makeText(requireContext(), "Gagal membuat direktori paket", Toast.LENGTH_LONG).show();
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean foundPopd = false;
        boolean alreadyExists = false;

        // Baca isi lama jika file sudah ada
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();

                    if (trimmed.startsWith("if [ \"$2\" = \"" + internalName + "\"")) {
                        alreadyExists = true;
                    }

                    sb.append(line).append("\n");

                    if (trimmed.equals("popd")) {
                        foundPopd = true;
                        sb.append("\n");
                        sb.append("# Package: ").append(displayName).append(" | ").append(internalName).append("\n");
                        sb.append("if [ \"$2\" = \"").append(internalName).append("\" ]; then\n");
                        sb.append("  progress \"-1\" \"").append(progressText).append("\"\n");
                        for (String cmd : commands) {
                            sb.append("  ").append(cmd).append("\n");
                        }
                        sb.append("  sleep ").append(sleepSeconds).append("\n");
                        sb.append("fi\n\n");
                    }
                }
            } catch (IOException e) {
                Log.e("PkgFrag", "Gagal membaca file lama", e);
            }
        }

        if (alreadyExists) {
            Toast.makeText(requireContext(), "Nama internal sudah ada", Toast.LENGTH_LONG).show();
            return;
        }

        if (!foundPopd) {
            sb.append("\n# Package: ").append(displayName).append(" | ").append(internalName).append("\n");
            sb.append("if [ \"$2\" = \"").append(internalName).append("\" ]; then\n");
            sb.append("  progress \"-1\" \"").append(progressText).append("\"\n");
            for (String cmd : commands) {
                sb.append("  ").append(cmd).append("\n");
            }
            sb.append("  sleep ").append(sleepSeconds).append("\n");
            sb.append("fi\n");
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(sb.toString());
            Log.d("PkgFrag", "Berhasil menulis file morepackages");
        } catch (IOException e) {
            Log.e("PkgFrag", "Gagal menulis file", e);
            Toast.makeText(requireContext(), "Gagal menyimpan paket", Toast.LENGTH_LONG).show();
        }
    }

    public interface OnPackagesSelectedListener {
        void onPackagesSelected(List<ContainerPackage> selected);
    }
}