package com.example.datainsert.exagear.controlsV2.edit.props;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneButton;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picker icon untuk Button biasa — 1 slot saja (beda dari Joystick/Dpad yg multi-slot),
 * tapi tetap pakai pola Winlator: icon yang lagi aktif ditandai (badge "✓"), tap icon lain
 * = pindah pilihan, tap icon yang sama = lepas (revert ke default). Sebelumnya versi lama
 * TIDAK menandai icon yang sedang aktif sama sekali saat dialog dibuka lagi — sekarang match
 * kondisi file di disk persis seperti loadIcons()+imageView.setSelected() milik Winlator.
 */
public class Prop1Icon extends Prop<TouchAreaModel> {

    private LinearLayout iconContainer;
    private Context context = Globals.getAppContext();

    // File sumber yang lagi jadi icon aktif tombol ini, null = belum ada / pakai default
    private File selectedSourceFile = null;

    private final Map<File, TextView> badgeByFile = new HashMap<>();

    public Prop1Icon(Host<TouchAreaModel> host, Context c) {
        super(host, c);
    }

    @Override
    public String getTitle() {
        return "Pilih Icon";
    }

    @Override
    protected View createMainEditView(Context c) {
        HorizontalScrollView scrollView = new HorizontalScrollView(c);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        iconContainer = new LinearLayout(c);
        iconContainer.setOrientation(LinearLayout.HORIZONTAL);
        iconContainer.setGravity(Gravity.CENTER_VERTICAL);
        iconContainer.setPadding(8, 8, 8, 8);
        iconContainer.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        iconContainer.setBackgroundColor(0xff2a2a2a);

        scrollView.addView(iconContainer);

        loadIconsAsync();

        return scrollView;
    }

    private void loadIconsAsync() {
        new Thread(() -> {
            File tmpIconDir = getTmpIconDir();
            if (tmpIconDir == null || !tmpIconDir.isDirectory()) {
                postShowToast("Folder tmp/icon tidak ditemukan");
                return;
            }

            File[] files = tmpIconDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".png"));

            if (files == null || files.length == 0) {
                postShowToast("Tidak ada icon di folder tmp/icon");
                return;
            }

            List<File> iconFiles = new ArrayList<>();
            for (File f : files) {
                if (f.isFile()) iconFiles.add(f);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                iconContainer.removeAllViews();
                badgeByFile.clear();
                initSelection(iconFiles);
                for (File iconFile : iconFiles) {
                    addIconThumbnail(iconFile);
                }
            });
        }).start();
    }

    /** Cek file target tombol ini di disk sekarang, cocokkan (byte-for-byte) ke salah satu file sumber. */
    private void initSelection(List<File> sourceFiles) {
        selectedSourceFile = null;

        File targetFile = getCurrentTargetFile();
        if (targetFile == null || !targetFile.isFile()) return;

        for (File src : sourceFiles) {
            if (filesEqual(src, targetFile)) {
                selectedSourceFile = src;
                break;
            }
        }
    }

    private boolean filesEqual(File a, File b) {
        if (a.length() != b.length()) return false;
        try (FileInputStream ia = new FileInputStream(a); FileInputStream ib = new FileInputStream(b)) {
            byte[] bufA = new byte[8192];
            byte[] bufB = new byte[8192];
            int rA, rB;
            while (true) {
                rA = ia.read(bufA);
                rB = ib.read(bufB);
                if (rA != rB) return false;
                if (rA == -1) return true;
                for (int i = 0; i < rA; i++) if (bufA[i] != bufB[i]) return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void addIconThumbnail(File iconFile) {
        try {
            Bitmap original = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            if (original == null) return;

            Bitmap thumb = ThumbnailUtils.extractThumbnail(original, 128, 128);
            original.recycle();

            FrameLayout wrapper = new FrameLayout(context);
            LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(128, 128);
            wrapperParams.setMargins(3, 4, 3, 4);
            wrapper.setLayoutParams(wrapperParams);

            ImageView iv = new ImageView(context);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            iv.setBackground(null);
            iv.setClickable(true);
            iv.setFocusable(true);

            wrapper.addView(iv);

            TextView badge = new TextView(context);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.gravity = Gravity.TOP | Gravity.END;
            badgeParams.setMargins(0, 2, 2, 0);
            badge.setLayoutParams(badgeParams);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(9);
            badge.setPadding(10, 2, 10, 2);
            badge.setMinWidth(0);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(0xE643A047); // hijau, beda dari joystick(merah)/dpad(biru)
            badgeBg.setCornerRadius(20f);
            badge.setBackground(badgeBg);
            badge.setText("\u2713"); // centang
            wrapper.addView(badge);

            badgeByFile.put(iconFile, badge);

            iv.setOnClickListener(v -> {
                v.animate().alpha(0.6f).setDuration(80).withEndAction(() -> {
                    v.animate().alpha(1f).setDuration(120).start();
                    onIconTapped(iconFile);
                }).start();
            });

            iconContainer.addView(wrapper);
            updateBadge(iconFile);

        } catch (Exception e) {
            // Lewati file yang error
        }
    }

    /**
     * Tap icon yang sama dgn yg sekarang aktif -> lepas (hapus file custom, balik ke default).
     * Tap icon lain -> jadi pilihan baru (timpa yang lama).
     */
    private void onIconTapped(File iconFile) {
        OneButton button = getOneButton();
        if (button == null) {
            showToast("Model bukan tombol");
            return;
        }

        selectedSourceFile = iconFile.equals(selectedSourceFile) ? null : iconFile;

        persistToDisk();
        refreshAllBadges();
    }

    private void persistToDisk() {
        OneButton button = getOneButton();
        if (button == null) return;

        String buttonName = button.getName();
        if (buttonName == null || buttonName.trim().isEmpty()) {
            showToast("Nama tombol kosong");
            return;
        }

        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            showToast("Tidak ada profile yang dipilih");
            return;
        }

        File targetDir = getProfileIconDir(currentProfileName);
        if (targetDir == null) {
            showToast("Gagal membuat folder icon untuk profile");
            return;
        }

        File targetFile = new File(targetDir, buttonName.trim() + ".png");

        if (targetFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            targetFile.delete();
        }

        if (selectedSourceFile == null) {
            showToast("Icon dilepas, tombol \"" + buttonName + "\" balik ke default");
            return;
        }

        boolean success = copyFile(selectedSourceFile, targetFile);
        if (success) {
            showToast("Icon disalin ke profile \"" + currentProfileName + "\": " + targetFile.getName());
        } else {
            showToast("Gagal menyalin icon");
        }
    }

    private void refreshAllBadges() {
        for (File f : badgeByFile.keySet()) updateBadge(f);
    }

    private void updateBadge(File sourceIconFile) {
        TextView badge = badgeByFile.get(sourceIconFile);
        if (badge == null) return;
        badge.setVisibility(sourceIconFile.equals(selectedSourceFile) ? View.VISIBLE : View.GONE);
    }

    private boolean copyFile(File source, File destination) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(destination);

            byte[] buffer = new byte[1024 * 8];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException ignored) {}
        }
    }

    @android.support.annotation.Nullable
    private File getCurrentTargetFile() {
        OneButton button = getOneButton();
        if (button == null) return null;
        String buttonName = button.getName();
        if (buttonName == null || buttonName.trim().isEmpty()) return null;

        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) return null;

        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;
        File iconDir = new File(patchDir, "controls/" + currentProfileName);
        return new File(iconDir, buttonName.trim() + ".png");
    }

    private OneButton getOneButton() {
        TouchAreaModel model = mHost.getModel();
        return (model instanceof OneButton) ? (OneButton) model : null;
    }

    private File getTmpIconDir() {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;
        return new File(patchDir, "controls/tmp/icon");
    }

    /** Folder icon tujuan sekarang berdasarkan nama profile yang aktif */
    private File getProfileIconDir(String profileName) {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;

        File iconDir = new File(patchDir, "controls/" + profileName);

        if (!iconDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            iconDir.mkdirs();
        }

        return iconDir.exists() && iconDir.isDirectory() ? iconDir : null;
    }

    private void postShowToast(final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> showToast(msg));
    }

    private void showToast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected View createAltEditView(Context c) {
        return null;
    }

    @Override
    public void updateUIFromModel(TouchAreaModel model) {
        // Tidak perlu update khusus di sini
    }
}
