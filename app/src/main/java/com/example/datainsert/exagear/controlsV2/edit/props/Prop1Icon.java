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
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneButton;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Prop1Icon extends Prop<TouchAreaModel> {

    private LinearLayout iconContainer;
    private Context context = Globals.getAppContext();

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
            // Lewati file yang error
        }
    }

    private void handleIconSelected(File selectedFile) {
        OneButton button = getOneButton();
        if (button == null) {
            showToast("Model bukan tombol");
            return;
        }

        String buttonName = button.getName();
        if (buttonName == null || buttonName.trim().isEmpty()) {
            showToast("Nama tombol kosong");
            return;
        }

        // Ambil nama profile yang sedang aktif saat ini
        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            showToast("Tidak ada profile yang dipilih");
            return;
        }

        String newFileName = buttonName.trim() + ".png";

        // Folder tujuan sekarang mengikuti nama profile
        File targetDir = getProfileIconDir(currentProfileName);
        if (targetDir == null) {
            showToast("Gagal membuat folder icon untuk profile");
            return;
        }

        File targetFile = new File(targetDir, newFileName);

        // Hapus file lama jika sudah ada (overwrite)
        if (targetFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            targetFile.delete();
        }

        boolean success = copyFile(selectedFile, targetFile);

        if (success) {
            showToast("Icon disalin ke profile \"" + currentProfileName + "\": " + newFileName);
            // Optional: refresh preview tombol jika diinginkan
            //notifyModelChanged();
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

    private OneButton getOneButton() {
        TouchAreaModel model = mHost.getModel();
        return (model instanceof OneButton) ? (OneButton) model : null;
    }

    private File getTmpIconDir() {
        File patchDir = QH.Files.edPatchDir();
        if (patchDir == null) return null;
        return new File(patchDir, "controls/tmp/icon");
    }

    /**
     * Folder icon tujuan sekarang berdasarkan nama profile yang aktif
     */
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