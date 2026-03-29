package com.example.datainsert.exagear.controlsV2.touchArea;

import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.FILL;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.STROKE;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.FILL_STROKE;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;

import com.eltechs.axs.Globals;
import com.eltechs.ed.R;
import com.example.datainsert.exagear.controlsV2.TouchAdapter;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneDpad;
import com.example.datainsert.exagear.controlsV2.touchAdapter.ButtonDpadPressAdapter;
import com.example.datainsert.exagear.QH;

import com.example.datainsert.exagear.controlsV2.touchAdapter.ButtonStickPressAdapter;
import java.io.File;
import java.util.Objects;

public class TouchAreaDpad extends TouchAreaStick {

    private final Paint mArrowPaint = new Paint(); // fallback jika drawable gagal

    // Cross (satu drawable, diwarnai mainColor)
    private Drawable mCrossDrawable;
    private Drawable mCrossBack;

    // Netral: SATU drawable saja yang menampilkan 4 arah sekaligus (abu-abu / desaturated)
    private Drawable mNeutralArrowsDrawable;

    // Pressed: 4 drawable terpisah (akan diwarnai saat ditekan)
    private Drawable mArrowLeftPressed;
    private Drawable mArrowRightPressed;
    private Drawable mArrowUpPressed;
    private Drawable mArrowDownPressed;

    // Menyimpan nama profile yang sedang aktif untuk load icon
    private String currentProfileName = null;

    public TouchAreaDpad(@NonNull OneDpad data) {
        this(data, null);
    }

    public TouchAreaDpad(@NonNull OneDpad data, @Nullable TouchAdapter adapter) {
        super(data, adapter != null ? adapter : new ButtonDpadPressAdapter(data));
        
        // Ambil nama profile awal
        updateCurrentProfileName();
        
        initDrawables();
    }

    /**
     * Update nama profile yang sedang aktif dan reload icon jika berubah
     */
    private void updateCurrentProfileName() {
        String newProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (newProfileName != null && !newProfileName.equals(currentProfileName)) {
            String oldProfile = currentProfileName;
            currentProfileName = newProfileName;

            if (!Objects.equals(oldProfile, currentProfileName)) {
                // Profile berubah → reload semua drawable icon
                initDrawables();
            }
        }
    }

    private void initDrawables() {
        mArrowPaint.setAntiAlias(true);
        mArrowPaint.setStyle(Paint.Style.FILL);

        int style = mModel.getColorStyle();
        Context ctx = Globals.getAppContext();
        if (ctx == null) return;

        Resources res = ctx.getResources();

        // ─────────────────────────────────────────────
        //   CUSTOM dari folder edpatch/controls/[profile]/icon/dpad/ (prioritas tertinggi)
        // ─────────────────────────────────────────────

        // Cross background / base
        Drawable customCrossBack = loadPngFromIconDir("cross_back", false);
        if (customCrossBack != null) {
            mCrossBack = customCrossBack.mutate();
        } else if (style == FILL || style == FILL_STROKE) {
            mCrossBack = ResourcesCompat.getDrawable(res, 2131231097, null);
            if (mCrossBack != null) mCrossBack = mCrossBack.mutate();
        }

        // Cross utama (garis / outline)
        Drawable customCross = loadPngFromIconDir("cross_drawable", false);
        if (customCross == null) customCross = loadPngFromIconDir("cross_front", false);
        if (customCross != null) {
            mCrossDrawable = customCross.mutate();
        } else if (style == STROKE || style == FILL_STROKE) {
            mCrossDrawable = ResourcesCompat.getDrawable(res, 2131231073, null);
            if (mCrossDrawable != null) mCrossDrawable = mCrossDrawable.mutate();
        }

        // Neutral arrows (semua arah kelihatan samar)
        Drawable customNeutral = loadPngFromIconDir("neutral", false);
        if (customNeutral == null) customNeutral = loadPngFromIconDir("cross_neutral", false);
        if (customNeutral != null) {
            mNeutralArrowsDrawable = customNeutral.mutate();
        } else {
            mNeutralArrowsDrawable = ResourcesCompat.getDrawable(res, 2131231074, null);
            if (mNeutralArrowsDrawable != null) mNeutralArrowsDrawable = mNeutralArrowsDrawable.mutate();
        }

        // ─────────────────────────────────────────────
        //   Arrow PRESSED – cari file yang mengandung _PRESSED
        // ─────────────────────────────────────────────
        mArrowUpPressed = loadPngFromIconDir("arrow_up", true);
        if (mArrowUpPressed == null) {
            mArrowUpPressed = ResourcesCompat.getDrawable(res, 2131231078, null);
        }
        if (mArrowUpPressed != null) mArrowUpPressed = mArrowUpPressed.mutate();

        mArrowDownPressed = loadPngFromIconDir("arrow_down", true);
        if (mArrowDownPressed == null) {
            mArrowDownPressed = ResourcesCompat.getDrawable(res, 2131231075, null);
        }
        if (mArrowDownPressed != null) mArrowDownPressed = mArrowDownPressed.mutate();

        mArrowLeftPressed = loadPngFromIconDir("arrow_left", true);
        if (mArrowLeftPressed == null) {
            mArrowLeftPressed = ResourcesCompat.getDrawable(res, 2131231076, null);
        }
        if (mArrowLeftPressed != null) mArrowLeftPressed = mArrowLeftPressed.mutate();

        mArrowRightPressed = loadPngFromIconDir("arrow_right", true);
        if (mArrowRightPressed == null) {
            mArrowRightPressed = ResourcesCompat.getDrawable(res, 2131231077, null);
        }
        if (mArrowRightPressed != null) mArrowRightPressed = mArrowRightPressed.mutate();

        updateTints();
    }

    @Nullable
    private File getIconDir() {
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            return null;
        }

        File patchDir = QH.Files.edPatchDir();
        File iconDir = new File(patchDir, "controls/" + currentProfileName + "/dpad");

        if (!iconDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            iconDir.mkdirs();
        }

        if (iconDir.exists() && !iconDir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            iconDir.delete();
            //noinspection ResultOfMethodCallIgnored
            iconDir.mkdirs();
        }

        return iconDir;
    }

    @Nullable
    private Drawable loadPngFromIconDir(String baseName, boolean lookForPressed) {
        try {
            File iconDir = getIconDir();
            if (iconDir == null || !iconDir.exists() || !iconDir.isDirectory()) {
                return null;
            }

            String fileNameFound;
            if (lookForPressed) {
                fileNameFound = findFileContaining(iconDir, baseName, "_PRESSED", ".png");
            } else {
                fileNameFound = findFileContaining(iconDir, baseName, null, ".png");
            }

            if (fileNameFound == null) {
                return null;
            }

            File pngFile = new File(iconDir, fileNameFound);
            if (!pngFile.isFile() || !pngFile.canRead()) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(pngFile.getAbsolutePath());
            if (bitmap == null) {
                return null;
            }

            return new BitmapDrawable(Globals.getAppContext().getResources(), bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String findFileContaining(File dir, String baseNamePart, @Nullable String mustContainExtra, String extension) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return null;

        String lowerBase = baseNamePart.toLowerCase();
        String lowerExtra = mustContainExtra != null ? mustContainExtra.toLowerCase() : null;

        for (File f : files) {
            if (!f.isFile()) continue;
            String nameLower = f.getName().toLowerCase();

            if (nameLower.endsWith(extension.toLowerCase()) && nameLower.contains(lowerBase)) {
                if (lowerExtra == null || nameLower.contains(lowerExtra)) {
                    return f.getName(); // kembalikan nama asli (case-sensitive)
                }
            }
        }
        return null;
    }

    private void updateTints() {
        int pressedColor = 0xffff420c; // warna pressed
        int mainColor = mModel.getMainColor();

        if (mCrossBack != null) {
            mCrossBack.setColorFilter(mainColor, PorterDuff.Mode.SRC_IN);
        }

        if (mCrossDrawable != null) {
            mCrossDrawable.setColorFilter(mainColor, PorterDuff.Mode.SRC_IN);
        }

        if (mNeutralArrowsDrawable != null) {
            // Pilihan: biarkan original jika custom, atau ikut tema
            // mNeutralArrowsDrawable.clearColorFilter();   // <-- uncomment jika ingin warna asli custom
            mNeutralArrowsDrawable.setColorFilter(mainColor, PorterDuff.Mode.SRC_IN);
        }

        if (mArrowUpPressed    != null) mArrowUpPressed   .setColorFilter(pressedColor, PorterDuff.Mode.SRC_IN);
        if (mArrowDownPressed  != null) mArrowDownPressed .setColorFilter(pressedColor, PorterDuff.Mode.SRC_IN);
        if (mArrowLeftPressed  != null) mArrowLeftPressed .setColorFilter(pressedColor, PorterDuff.Mode.SRC_IN);
        if (mArrowRightPressed != null) mArrowRightPressed.setColorFilter(pressedColor, PorterDuff.Mode.SRC_IN);

        mArrowPaint.setColor(mainColor);
    }

    /**
     * Method publik yang dipanggil ketika profile berganti
     */
    public void onProfileChanged() {
        updateCurrentProfileName();
        // initDrawables() sudah dipanggil di dalam updateCurrentProfileName jika berubah
        //invalidate();  // paksa redraw
    }

    @Override
    public void onDraw(Canvas canvas) {
        ButtonDpadPressAdapter runtimeAdapter = mAdapter instanceof ButtonDpadPressAdapter
                ? (ButtonDpadPressAdapter) mAdapter : null;

        if (runtimeAdapter != null) {
            runtimeAdapter.updatePressPos();
        }

        // Pastikan profile name masih sinkron sebelum draw
        updateCurrentProfileName();

        float cx = mModel.getLeft() + mModel.getSize() / 2f;
        float cy = mModel.getTop() + mModel.getSize() / 2f;
        float size = mModel.getSize();
        int half = Math.round(size / 2f);

        updateTints();

        int nowFingerAt = runtimeAdapter != null
                ? runtimeAdapter.nowFingerAt
                : ButtonStickPressAdapter.FINGER_AT_CENTER;

        boolean leftPressed  = (nowFingerAt & ButtonStickPressAdapter.FINGER_AT_LEFT)   != 0;
        boolean rightPressed = (nowFingerAt & ButtonStickPressAdapter.FINGER_AT_RIGHT)  != 0;
        boolean upPressed    = (nowFingerAt & ButtonStickPressAdapter.FINGER_AT_TOP)    != 0;
        boolean downPressed  = (nowFingerAt & ButtonStickPressAdapter.FINGER_AT_BOTTOM) != 0;

        // 1. Background cross
        if (mCrossBack != null) {
            mCrossBack.setBounds(
                    Math.round(cx - half),
                    Math.round(cy - half),
                    Math.round(cx + half),
                    Math.round(cy + half)
            );
            mCrossBack.draw(canvas);
        }

        // 2. Neutral arrows
        if (mNeutralArrowsDrawable != null) {
            mNeutralArrowsDrawable.setBounds(
                    Math.round(cx - half),
                    Math.round(cy - half),
                    Math.round(cx + half),
                    Math.round(cy + half)
            );
            mNeutralArrowsDrawable.draw(canvas);
        }

        // 3. Cross utama
        if (mCrossDrawable != null) {
            mCrossDrawable.setBounds(
                    Math.round(cx - half),
                    Math.round(cy - half),
                    Math.round(cx + half),
                    Math.round(cy + half)
            );
            mCrossDrawable.draw(canvas);
        }

        // 4. Arrow pressed (hanya yang ditekan)
        if (leftPressed  && mArrowLeftPressed  != null) drawFullArrow(canvas, mArrowLeftPressed,  cx, cy, half);
        if (rightPressed && mArrowRightPressed != null) drawFullArrow(canvas, mArrowRightPressed, cx, cy, half);
        if (upPressed    && mArrowUpPressed    != null) drawFullArrow(canvas, mArrowUpPressed,    cx, cy, half);
        if (downPressed  && mArrowDownPressed  != null) drawFullArrow(canvas, mArrowDownPressed,  cx, cy, half);
    }

    private void drawFullArrow(Canvas canvas, Drawable arrow, float cx, float cy, int half) {
        arrow.setBounds(
                Math.round(cx - half),
                Math.round(cy - half),
                Math.round(cx + half),
                Math.round(cy + half)
        );
        arrow.draw(canvas);
    }
}