package com.example.datainsert.exagear.controlsV2.edit.props;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneDpad;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Prop1IconDpad extends Prop<TouchAreaModel> {

    private LinearLayout iconContainer;
    private Context context = Globals.getAppContext();

    // Pilihan tipe utama
    private String selectedStyle = "cross"; // default
    private final String[] STYLES = {"cross", "neutral", "arrow_pressed"};

    // Untuk arrow_pressed → sub selection
    private String selectedArrowDirection = "up"; // default ketika memilih arrow_pressed

    public Prop1IconDpad(Host<TouchAreaModel> host, Context c) {
        super(host, c);
    }

    @Override
    public String getTitle() {
        return "Pilih Icon D-Pad";
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

        // ── Tombol popup menu di kanan ───────────────────────────────
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

        menuButton.setOnClickListener(v -> showMainStylePopupMenu(v));

        // Gabungkan scroll + menu button
        LinearLayout horizontalContainer = new LinearLayout(c);
        horizontalContainer.setOrientation(LinearLayout.HORIZONTAL);
        horizontalContainer.addView(scrollView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        horizontalContainer.addView(menuButton);

        root.addView(horizontalContainer);

        loadIconsAsync();

        return root;
    }

    private void showMainStylePopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add(0, 1, 0, "Cross (background & main)");
        popup.getMenu().add(0, 2, 0, "Neutral (semua arah samar)");
        popup.getMenu().add(0, 3, 0, "Arrow Pressed → pilih arah");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                selectedStyle = "cross";
                showToast("Dipilih: Cross style");
            } else if (id == 2) {
                selectedStyle = "neutral";
                showToast("Dipilih: Neutral style");
            } else if (id == 3) {
                selectedStyle = "arrow_pressed";
                showArrowDirectionSubMenu(anchor);
                return true; // biarkan sub-menu muncul
            }
            // updateThumbnailsIfNeeded(); // jika ingin refresh list icon saat ganti style
            return true;
        });

        popup.show();
    }

    private void showArrowDirectionSubMenu(View anchor) {
        PopupMenu subPopup = new PopupMenu(context, anchor);
        subPopup.getMenu().add(0, 1, 0, "↑ Arrow Up (atas)");
        subPopup.getMenu().add(0, 2, 0, "↓ Arrow Down (bawah)");
        subPopup.getMenu().add(0, 3, 0, "← Arrow Left (kiri)");
        subPopup.getMenu().add(0, 4, 0, "→ Arrow Right (kanan)");

        subPopup.setOnMenuItemClickListener(subItem -> {
            int subId = subItem.getItemId();
            if (subId == 1) selectedArrowDirection = "up";
            else if (subId == 2) selectedArrowDirection = "down";
            else if (subId == 3) selectedArrowDirection = "left";
            else if (subId == 4) selectedArrowDirection = "right";

            showToast("Arrow pressed: " + selectedArrowDirection + " dipilih");
            return true;
        });

        subPopup.show();
    }

    private void loadIconsAsync() {
        new Thread(() -> {
            File tmpIconDir = getTmpDpadIconDir();
            if (tmpIconDir == null || !tmpIconDir.isDirectory()) {
                postShowToast("Folder tmp/icon/dpad tidak ditemukan");
                return;
            }

            File[] files = tmpIconDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".png"));

            if (files == null || files.length == 0) {
                postShowToast("Tidak ada icon di folder tmp/icon/dpad");
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
        OneDpad dpad = getOneDpad();
        if (dpad == null) {
            showToast("Model bukan Dpad");
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

        // Ambil nama profile yang sedang aktif saat ini
        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            showToast("Tidak ada profile yang dipilih");
            return;
        }

        File targetDir = getProfileDpadIconDir(currentProfileName);
        if (targetDir == null) {
            showToast("Gagal membuat folder icon dpad untuk profile");
            return;
        }

        boolean success = false;
        String savedFileName = "";

        // Tanpa prefix underscore agar nama file lebih bersih
        // String prefix = "_";   // bisa diaktifkan kembali jika diperlukan

        if ("cross".equals(selectedStyle)) {
            File backFile = new File(targetDir, baseName + "cross_back.png");
            success = copyFile(selectedFile, backFile);
            // Jika ingin copy juga ke cross_drawable.png (opsional):
            // File drawableFile = new File(targetDir, baseName + "cross_drawable.png");
            // success &= copyFile(selectedFile, drawableFile);

            savedFileName = backFile.getName();
        } 
        else if ("neutral".equals(selectedStyle)) {
            File neutralFile = new File(targetDir, baseName + "neutral.png");
            success = copyFile(selectedFile, neutralFile);
            savedFileName = neutralFile.getName();
        } 
        else if ("arrow_pressed".equals(selectedStyle)) {
            String direction = selectedArrowDirection;
            File arrowFile = new File(targetDir, baseName + "_arrow_" + direction + "_PRESSED.png");
            success = copyFile(selectedFile, arrowFile);
            savedFileName = arrowFile.getName();
        }

        if (success) {
            showToast("Icon disimpan ke profile \"" + currentProfileName + "\":\n" + savedFileName);
            // notifyModelChanged();  // Uncomment jika ingin refresh preview D-Pad langsung
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

    private OneDpad getOneDpad() {
        TouchAreaModel model = mHost.getModel();
        return (model instanceof OneDpad) ? (OneDpad) model : null;
    }

    private File getTmpDpadIconDir() {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;
        return new File(patchDir, "controls/tmp/icon/dpad");
    }

    /**
     * Folder tujuan icon D-Pad sekarang mengikuti nama profile yang aktif
     */
    private File getProfileDpadIconDir(String profileName) {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;

        File iconDir = new File(patchDir, "controls/" + profileName + "/dpad");

        if (!iconDir.exists() && !iconDir.mkdirs()) {
            return null;
        }

        return iconDir.isDirectory() ? iconDir : null;
    }

    private void postShowToast(final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> showToast(msg));
    }

    private void showToast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    // Method ini bisa dipanggil jika ingin refresh thumbnail setelah ganti style (opsional)
    private void updateThumbnailsIfNeeded() {
        // iconContainer.removeAllViews();
        // loadIconsAsync();  // atau logic refresh lain
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