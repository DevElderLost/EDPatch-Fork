package com.example.datainsert.exagear.controlsV2.edit.props;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneStick;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.eltechs.ed.R;

public class Prop1IconJoystick extends Prop<TouchAreaModel> {

    private LinearLayout iconContainer;
    private Context context = Globals.getAppContext();

    // Pilihan tipe joystick icon
    private String selectedType = "outer"; // default
    private final String[] TYPES = {"outer", "inner", "mousepad"};

    public Prop1IconJoystick(Host<TouchAreaModel> host, Context c) {
        super(host, c);
    }

    @Override
    public String getTitle() {
        return "Pilih Icon Joystick";
    }

    @Override
    protected View createMainEditView(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // ── Horizontal Scroll untuk icon ───────────────────────────────
        HorizontalScrollView scrollView = new HorizontalScrollView(c);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
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
        iconContainer.setBackgroundColor(0xff2a2a2a);

        scrollView.addView(iconContainer);

        // ── Area kanan: tombol popup menu ───────────────────────────────
        ImageView menuButton = new ImageView(c);
        menuButton.setImageResource(2131230877);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        menuParams.gravity = Gravity.CENTER_VERTICAL;
        menuButton.setLayoutParams(menuParams);
        menuButton.setPadding(16, 0, 16, 0);
        menuButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        menuButton.setOnClickListener(v -> showTypePopupMenu(v));

        // Gabungkan scroll + tombol menu
        LinearLayout horizontalContainer = new LinearLayout(c);
        horizontalContainer.setOrientation(LinearLayout.HORIZONTAL);
        horizontalContainer.addView(scrollView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        horizontalContainer.addView(menuButton);

        root.addView(horizontalContainer);

        loadIconsAsync();

        return root;
    }

    private void showTypePopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add(0, 1, 0, "Outer");
        popup.getMenu().add(0, 2, 0, "Inner");
        popup.getMenu().add(0, 3, 0, "Mousepad");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                selectedType = "outer";
                showToast("Dipilih: Outer");
            } else if (id == 2) {
                selectedType = "inner";
                showToast("Dipilih: Inner");
            } else if (id == 3) {
                selectedType = "mousepad";
                showToast("Dipilih: Mousepad");
            }
            return true;
        });

        popup.show();
    }

    private void loadIconsAsync() {
        new Thread(() -> {
            File tmpIconDir = getTmpJoystickIconDir();
            if (tmpIconDir == null || !tmpIconDir.isDirectory()) {
                postShowToast("Folder tmp/icon/stick tidak ditemukan");
                return;
            }

            File[] files = tmpIconDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".png"));

            if (files == null || files.length == 0) {
                postShowToast("Tidak ada icon di folder tmp/icon/stick");
                return;
            }

            List<File> iconFiles = new ArrayList<>();
            for (File f : files) {
                if (f.isFile()) iconFiles.add(f);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                iconContainer.removeAllViews();
                for (File iconFile : iconFiles) {
                    addIconThumbnail(iconFile);
                }
            });
        }).start();
    }

    private void addIconThumbnail(File iconFile) {
        try {
            Bitmap original = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            if (original == null) return;

            Bitmap thumb = ThumbnailUtils.extractThumbnail(original, 128, 128);
            original.recycle();

            ImageView iv = new ImageView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(128, 128);
            params.setMargins(3, 4, 3, 4);

            iv.setLayoutParams(params);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            iv.setBackground(null);

            iv.setClickable(true);
            iv.setFocusable(true);

            iv.setOnClickListener(v -> {
                v.animate().alpha(0.6f).setDuration(80).withEndAction(() -> {
                    v.animate().alpha(1f).setDuration(120).start();
                    handleIconSelected(iconFile);
                }).start();
            });

            iconContainer.addView(iv);

        } catch (Exception e) {
            // Lewati file error
        }
    }

    private void handleIconSelected(File selectedFile) {
        OneStick stick = getOneStick();
        if (stick == null) {
            showToast("Model bukan Joystick");
            return;
        }

        String originalFileName = selectedFile.getName().toLowerCase();
        if (!originalFileName.endsWith(".png")) {
            showToast("File bukan PNG");
            return;
        }

        // Ambil nama tanpa .png
        String baseName = originalFileName.substring(0, originalFileName.length() - 4);
        if (baseName.trim().isEmpty()) {
            showToast("Nama file tidak valid");
            return;
        }

        // Ambil nama profile yang sedang aktif
        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            showToast("Tidak ada profile yang dipilih");
            return;
        }

        File targetDir = getProfileJoystickIconDir(currentProfileName);
        if (targetDir == null) {
            showToast("Gagal membuat folder icon joystick untuk profile");
            return;
        }

        String savedFileName;
        File targetFile;

        if ("outer".equals(selectedType)) {
            targetFile = new File(targetDir, baseName + "_outer.png");
        } else if ("inner".equals(selectedType)) {
            targetFile = new File(targetDir, baseName + "_inner.png");
        } else if ("mousepad".equals(selectedType)) {
            targetFile = new File(targetDir, baseName + "_mousepad.png");
        } else {
            showToast("Tipe tidak valid");
            return;
        }

        savedFileName = targetFile.getName();

        // Hapus file lama jika sudah ada (overwrite)
        if (targetFile.exists()) {
            targetFile.delete();
        }

        boolean success = copyFile(selectedFile, targetFile);

        if (success) {
            showToast("Icon disimpan ke profile \"" + currentProfileName + "\":\n" + savedFileName);
            //notifyModelChanged();  // Uncomment jika ingin refresh preview joystick langsung
        } else {
            showToast("Gagal menyalin icon");
        }
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

    private OneStick getOneStick() {
        TouchAreaModel model = mHost.getModel();
        return (model instanceof OneStick) ? (OneStick) model : null;
    }

    private File getTmpJoystickIconDir() {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;
        return new File(patchDir, "controls/tmp/icon/stick");
    }

    /**
     * Folder tujuan icon Joystick sekarang mengikuti nama profile yang aktif
     */
    private File getProfileJoystickIconDir(String profileName) {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;

        File iconDir = new File(patchDir, "controls/" + profileName + "/stick");

        if (!iconDir.exists() && !iconDir.mkdirs()) {
            return null;
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
        // Tidak perlu update khusus di sini untuk saat ini
    }
}