package com.example.datainsert.exagear.controlsV2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.eltechs.ed.activities.EDMainActivity;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneProfile;
import com.example.datainsert.exagear.QH;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ControlsFragment extends Fragment {
    private static final String TAG = "ControlsFragment";

    public static final String ARGV_START_EDIT_ON_SHOW = "ARGV_START_EDIT_ON_SHOW";
    private static final int REQUEST_IMPORT_PROFILE = 124;
    private static final int REQUEST_EXPORT_PROFILE = 125;
    private static final int REQUEST_IMPORT_ICON    = 126;
    private static final int REQUEST_IMPORT_PACK    = 127;
    // REQUEST_EXPORT_PACK dihapus karena tidak berfungsi

    private final SparseArray<IntentResultCallback> intentCallbackList = new SparseArray<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private TouchAreaView touchAreaView;

    public ControlsFragment() {
    }

    public static Fragment newInstance(Fragment ref) {
        ControlsFragment fragment = new ControlsFragment();
        if (ref instanceof ControlsFragment) {
            ControlsFragment old = (ControlsFragment) ref;
            fragment.touchAreaView = old.touchAreaView;

            for (int i = 0; i < old.intentCallbackList.size(); i++) {
                int key = old.intentCallbackList.keyAt(i);
                IntentResultCallback value = old.intentCallbackList.valueAt(i);
                fragment.intentCallbackList.put(key, value);
            }
        }
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: ");
        return new FrameLayout(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, this + " onResume: ");

        Context c = requireContext();

        if (touchAreaView == null) {
            touchAreaView = new TouchAreaView(c);
            Const.setTouchView(touchAreaView);

            OneProfile profile = ModelProvider.readCurrentProfile();
            touchAreaView.setProfile(profile);

            if (getArguments() != null && getArguments().getBoolean(ARGV_START_EDIT_ON_SHOW)) {
                touchAreaView.startEdit();
            }

            if (requireActivity() instanceof EDMainActivity) {
                touchAreaView.setBackgroundColor(0xff8D6F64);
            }
        }

        ViewGroup root = getRootView();
        root.removeAllViews();
        if (touchAreaView.getParent() != null) {
            ((ViewGroup) touchAreaView.getParent()).removeView(touchAreaView);
        }
        root.addView(touchAreaView);

        if (Const.getTouchView() != touchAreaView) {
            Const.setTouchView(touchAreaView);
        }

        touchAreaView.requestFocus();
        touchAreaView.invalidate();
    }

    private ViewGroup getRootView() {
        return (ViewGroup) getView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResultCallback callback = intentCallbackList.get(requestCode);
        if (callback == null) return;

        if (data == null || data.getData() == null || resultCode != Activity.RESULT_OK) {
            callback.onReceive(requestCode, resultCode, data);
            return;
        }

        if (requestCode == REQUEST_IMPORT_PROFILE || requestCode == REQUEST_EXPORT_PROFILE) {
            handleRequestAfterContextHasBeenSet(() -> callback.onReceive(requestCode, resultCode, data));
        }
        else if (requestCode == REQUEST_IMPORT_ICON) {
            handleRequestAfterContextHasBeenSet(() -> {
                boolean success = extractIconZip(data);
                callback.onReceive(requestCode, resultCode, data);
                if (success) {
                    uiHandler.post(() -> Toast.makeText(requireContext(), "Ikon berhasil diimpor", Toast.LENGTH_SHORT).show());
                }
            });
        }
        else if (requestCode == REQUEST_IMPORT_PACK) {
            handleRequestAfterContextHasBeenSet(() -> {
                boolean success = extractPackZip(data);
                callback.onReceive(requestCode, resultCode, data);
                if (success) {
                    uiHandler.post(() -> {
                        Toast.makeText(requireContext(), "Pack berhasil diimpor", Toast.LENGTH_SHORT).show();
                        if (touchAreaView != null) touchAreaView.invalidate();
                    });
                } else {
                    uiHandler.post(() -> Toast.makeText(requireContext(), "Gagal mengimpor pack", Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────
    //                  IMPORT PACK - deteksi file non-image sebagai profile
    // ─────────────────────────────────────────────────────────────

    private boolean extractPackZip(Intent data) {
        Uri uri = data.getData();
        if (uri == null) return false;

        File baseDir     = QH.Files.edPatchDir();
        File iconBaseDir = new File(baseDir, "controls");
        File profileDir  = new File(baseDir, "customcontrols2/profiles");

        if ((!iconBaseDir.exists() && !iconBaseDir.mkdirs()) ||
            (!profileDir.exists()  && !profileDir.mkdirs())) {
            Log.e(TAG, "Gagal membuat folder tujuan");
            return false;
        }

        String profileName = null;
        int countProfile = 0;
        int countOther = 0;

        // PASS 1: Cari file pertama yang BUKAN gambar sebagai profile
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             ZipArchiveInputStream zipIn = new ZipArchiveInputStream(is)) {

            ArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String nameLower = entry.getName().toLowerCase();

                // Lewati file gambar
                if (nameLower.endsWith(".png") ||
                    nameLower.endsWith(".jpg") ||
                    nameLower.endsWith(".jpeg") ||
                    nameLower.endsWith(".webp") ||
                    nameLower.endsWith(".gif") ||
                    nameLower.endsWith(".bmp")) {
                    continue;
                }

                // File pertama yang bukan gambar → jadikan profile
                String entryName = entry.getName();
                File temp = new File(entryName);
                String fileName = temp.getName();

                int dotIndex = fileName.lastIndexOf('.');
                String candidate = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;

                if (candidate.trim().isEmpty()) continue;

                profileName = candidate;
                countProfile++;
                Log.d(TAG, "File profile terdeteksi (non-image): " + entryName + " → nama: " + profileName);
                break;  // cukup satu saja
            }
        } catch (IOException ignored) {}

        if (profileName == null || profileName.trim().isEmpty()) {
            uiHandler.post(() -> Toast.makeText(requireContext(),
                    "Tidak ditemukan file konfigurasi (non-gambar) di dalam pack", Toast.LENGTH_LONG).show());
            return false;
        }

        Log.d(TAG, "Nama profile yang terdeteksi: " + profileName);

        // PASS 2: Ekstraksi
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             ZipArchiveInputStream zipIn = new ZipArchiveInputStream(is)) {

            ArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {

                String entryName = entry.getName();
                String nameLower = entryName.toLowerCase();

                File targetFile = null;

                if (entry.isDirectory()) {
                    String targetPath = entryName;
                    if (targetPath.startsWith(profileName + "/")) {
                        targetPath = targetPath.substring(profileName.length() + 1);
                    }
                    File targetDir = new File(iconBaseDir, profileName + "/" + targetPath);
                    if (!targetDir.exists()) {
                        FileUtils.forceMkdir(targetDir);
                    }
                    continue;
                }

                boolean isImage = nameLower.endsWith(".png") ||
                                  nameLower.endsWith(".jpg") ||
                                  nameLower.endsWith(".jpeg") ||
                                  nameLower.endsWith(".webp") ||
                                  nameLower.endsWith(".gif") ||
                                  nameLower.endsWith(".bmp");

                String relative = entryName;
                if (relative.startsWith(profileName + "/")) {
                    relative = relative.substring(profileName.length() + 1);
                }

                if (!isImage) {
                    // File non-image → simpan sebagai file profile
                    String fileName = new File(entryName).getName();
                    targetFile = new File(profileDir, fileName);
                    countProfile++;
                } else {
                    // File gambar atau aset lain → simpan ke folder controls/profileName/
                    targetFile = new File(iconBaseDir, profileName + "/" + relative);
                    countOther++;
                }

                if (targetFile != null) {
                    FileUtils.forceMkdir(targetFile.getParentFile());

                    try (OutputStream out = FileUtils.openOutputStream(targetFile)) {
                        IOUtils.copy(zipIn, out);
                    }

                    Log.d(TAG, "Extracted: " + targetFile.getAbsolutePath());
                }
            }

            Log.d(TAG, "Pack selesai → Profile files: " + countProfile + ", Gambar & lainnya: " + countOther);

            if (touchAreaView != null) {
                touchAreaView.invalidate();
            }

            return countProfile >= 1 || countOther > 0;

        } catch (IOException e) {
            Log.e(TAG, "Gagal ekstrak pack", e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //                  IMPORT ICON ZIP (standalone) - tetap sama
    // ─────────────────────────────────────────────────────────────

    private boolean extractIconZip(Intent data) {
        Uri uri = data.getData();
        if (uri == null) return false;

        File iconDir = new File(QH.Files.edPatchDir(), "controls/tmp/icon");
        if (!iconDir.exists() && !iconDir.mkdirs()) {
            Log.e(TAG, "Gagal membuat folder tmp/icon");
            return false;
        }

        int count = 0;

        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             ZipArchiveInputStream zipIn = new ZipArchiveInputStream(is)) {

            ArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String nameLower = entry.getName().toLowerCase();
                if (!nameLower.endsWith(".png") && !nameLower.endsWith(".jpg") && !nameLower.endsWith(".webp")) {
                    continue;
                }

                File outFile = new File(iconDir, entry.getName());
                FileUtils.forceMkdir(outFile.getParentFile());

                try (OutputStream out = FileUtils.openOutputStream(outFile)) {
                    IOUtils.copy(zipIn, out);
                }

                Log.d(TAG, "Extracted icon: " + outFile);
                count++;
            }

            if (count > 0 && touchAreaView != null) {
                touchAreaView.invalidate();
            }

            return count > 0;

        } catch (IOException e) {
            Log.e(TAG, "Gagal ekstrak ZIP ikon", e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //                          UTILITY
    // ─────────────────────────────────────────────────────────────

    private void handleRequestAfterContextHasBeenSet(Runnable action) {
        if (Const.isInitiated()) {
            action.run();
        } else {
            uiHandler.postDelayed(() -> handleRequestAfterContextHasBeenSet(action), 300);
        }
    }

    public void requestImportProfile(IntentResultCallback callback) {
        intentCallbackList.put(REQUEST_IMPORT_PROFILE, callback);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_PROFILE);
    }

    public void requestExportProfile(@NonNull String profileName, IntentResultCallback callback) {
        intentCallbackList.put(REQUEST_EXPORT_PROFILE, callback);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, profileName + ".json");
        startActivityForResult(intent, REQUEST_EXPORT_PROFILE);
    }

    public void requestImportIcon(IntentResultCallback callback) {
        intentCallbackList.put(REQUEST_IMPORT_ICON, callback);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_IMPORT_ICON);
    }

    public void requestImportPack(IntentResultCallback callback) {
        intentCallbackList.put(REQUEST_IMPORT_PACK, callback);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_IMPORT_PACK);
    }

    // requestExportProfileWithIcons dihapus karena tidak berfungsi

    public interface IntentResultCallback {
        void onReceive(int requestCode, int resultCode, Intent data);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, this + " onDestroy: ");
        touchAreaView = null;
        intentCallbackList.clear();
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, this + " onPause: ");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, this + " onDetach: ");
    }
}