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
import com.example.datainsert.exagear.controlsV2.model.OneStick;
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
 * Picker icon Joystick — meniru pola slot milik Winlator (ControlsEditorActivity.loadSlotIconsUI):
 * TIDAK ada "pilih tipe dulu di popup lalu tap icon" (itu sumber bug lama: gampang ke-desync
 * antara popup & tap). Sebagai gantinya, tiap slot py nama file TETAP (bukan berbasis nama file
 * sumber), dan klik icon langsung mengisi slot kosong berikutnya secara berurutan:
 * <br/> - klik icon yang BELUM dipakai -> masuk slot kosong pertama (1=outer, 2=inner, M=mousepad)
 * <br/> - klik icon yang SUDAH dipakai -> dilepas dari slotnya (badge hilang)
 * <br/> Badge dihitung LANGSUNG dari array slot ini saat ini, tidak ada variable "mode aktif"
 * terpisah yang bisa lupa/ketuker.
 */
public class Prop1IconJoystick extends Prop<TouchAreaModel> {

    private LinearLayout iconContainer;
    private Context context = Globals.getAppContext();

    // Nama file TETAP per slot (bukan berbasis nama file sumber) — index tetap = slot tetap,
    // menghilangkan kelas bug "nama file sumber ketimpa/ketuker" yang lama.
    private static final String[] SLOT_FILE_NAMES = {"icon_outer.png", "icon_inner.png", "icon_mousepad.png"};
    private static final String[] SLOT_BADGES = {"1", "2", "M"};
    private static final int SLOT_COUNT = SLOT_FILE_NAMES.length;

    // File sumber (dari folder tmp/icon/stick) yang lagi menempati tiap slot, null = kosong.
    // Diisi ulang tiap loadIconsAsync() dari file yang ada di folder target profile.
    private final File[] slotSourceFiles = new File[SLOT_COUNT];

    // Referensi badge per file sumber, supaya bisa direfresh semua tanpa reload ulang daftar icon
    private final Map<File, TextView> badgeByFile = new HashMap<>();

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

        TextView hint = new TextView(c);
        hint.setText("Slot: 1=Outer  2=Inner  M=Mousepad. Tap icon = isi slot kosong berikutnya. Tap lagi = lepas.");
        hint.setTextSize(11);
        hint.setTextColor(0xffbbbbbb);
        hint.setPadding(4, 2, 4, 4);
        root.addView(hint);

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
        root.addView(scrollView);

        loadIconsAsync();

        return root;
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
                badgeByFile.clear();
                initSlotAssignments(iconFiles);
                for (File iconFile : iconFiles) {
                    addIconThumbnail(iconFile);
                }
            });
        }).start();
    }

    /**
     * Isi slotSourceFiles[] dari kondisi file yang SUDAH ada di folder target profile saat ini,
     * dengan mencocokkan isi bita file target (icon_outer.png dst) ke salah satu file sumber
     * di tmp/icon/stick yang isinya SAMA PERSIS (byte-for-byte). Ini supaya badge langsung
     * kelihatan benar kalau sebelumnya sudah pernah disimpan.
     */
    private void initSlotAssignments(List<File> sourceFiles) {
        for (int s = 0; s < SLOT_COUNT; s++) slotSourceFiles[s] = null;

        String currentProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) return;
        File targetDir = getProfileJoystickIconDir(currentProfileName);
        if (targetDir == null) return;

        for (int s = 0; s < SLOT_COUNT; s++) {
            File slotFile = new File(targetDir, SLOT_FILE_NAMES[s]);
            if (!slotFile.isFile()) continue;
            for (File src : sourceFiles) {
                if (filesEqual(src, slotFile)) {
                    slotSourceFiles[s] = src;
                    break;
                }
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
            badgeBg.setColor(0xE6E53935);
            badgeBg.setCornerRadius(20f);
            badge.setBackground(badgeBg);
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
            // Lewati file error
        }
    }

    /**
     * Logika inti — persis pola Winlator: cari apakah file ini sudah menempati salah satu slot;
     * kalau ya, lepas (geser slot sesudahnya maju). Kalau belum, taruh di slot kosong pertama;
     * kalau semua slot penuh, geser semua maju dan taruh di slot terakhir.
     */
    private void onIconTapped(File iconFile) {
        OneStick stick = getOneStick();
        if (stick == null) {
            showToast("Model bukan Joystick");
            return;
        }

        int existingSlot = -1;
        for (int s = 0; s < SLOT_COUNT; s++) {
            if (iconFile.equals(slotSourceFiles[s])) { existingSlot = s; break; }
        }

        if (existingSlot >= 0) {
            for (int s = existingSlot; s < SLOT_COUNT - 1; s++) slotSourceFiles[s] = slotSourceFiles[s + 1];
            slotSourceFiles[SLOT_COUNT - 1] = null;
        } else {
            boolean placed = false;
            for (int s = 0; s < SLOT_COUNT; s++) {
                if (slotSourceFiles[s] == null) { slotSourceFiles[s] = iconFile; placed = true; break; }
            }
            if (!placed) {
                System.arraycopy(slotSourceFiles, 1, slotSourceFiles, 0, SLOT_COUNT - 1);
                slotSourceFiles[SLOT_COUNT - 1] = iconFile;
            }
        }

        persistSlotsToDisk();
        refreshAllBadges();
    }

    /** Tulis ulang seluruh isi folder icon profile ini supaya PERSIS sesuai slotSourceFiles[] saat ini. */
    private void persistSlotsToDisk() {
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

        for (int s = 0; s < SLOT_COUNT; s++) {
            File targetFile = new File(targetDir, SLOT_FILE_NAMES[s]);
            if (slotSourceFiles[s] == null) {
                if (targetFile.exists()) //noinspection ResultOfMethodCallIgnored
                    targetFile.delete();
            } else {
                if (targetFile.exists()) //noinspection ResultOfMethodCallIgnored
                    targetFile.delete();
                copyFile(slotSourceFiles[s], targetFile);
            }
        }
        showToast("Icon tersimpan untuk profile \"" + currentProfileName + "\"");
    }

    private void refreshAllBadges() {
        for (File f : badgeByFile.keySet()) updateBadge(f);
    }

    private void updateBadge(File sourceIconFile) {
        TextView badge = badgeByFile.get(sourceIconFile);
        if (badge == null) return;
        int slot = -1;
        for (int s = 0; s < SLOT_COUNT; s++) {
            if (sourceIconFile.equals(slotSourceFiles[s])) { slot = s; break; }
        }
        if (slot < 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(SLOT_BADGES[slot]);
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

    /** Folder tujuan icon Joystick mengikuti nama profile yang aktif */
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
