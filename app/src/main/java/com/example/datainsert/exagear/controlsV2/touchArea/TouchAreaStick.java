package com.example.datainsert.exagear.controlsV2.touchArea;

import static com.example.datainsert.exagear.controlsV2.Const.dp8;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.TestHelper;
import com.example.datainsert.exagear.controlsV2.TouchAdapter;
import com.example.datainsert.exagear.controlsV2.TouchArea;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneStick;
import com.example.datainsert.exagear.controlsV2.touchAdapter.ButtonStickPressAdapter;
import com.example.datainsert.exagear.QH;

import java.io.File;
import java.util.Objects;

public class TouchAreaStick extends TouchArea<OneStick> {

    private Drawable mOuterDrawable;
    private Drawable mInnerDrawable;

    private boolean isMouseMode = false;

    // Resource ID fallback khusus untuk mode mouse/trackpad
    // GANTI ANGKA INI SESUAI RESOURCE ID SEBENARNYA DI PROJECT ANDA
    private static final int TRACKPAD_OUTER_RES_ID = 2131231296;  // ← Ubah ke ID yang benar!

    // Menyimpan nama profile yang sedang aktif untuk load icon
    private String currentProfileName = null;

    public TouchAreaStick(@NonNull OneStick data, @Nullable TouchAdapter adapter) {
        super(data, adapter != null ? adapter : new ButtonStickPressAdapter(data));
        
        // Ambil nama profile awal
        updateCurrentProfileName();
        
        initDrawables();
    }

    /**
     * Update nama profile yang sedang aktif dan reload drawable jika profile berubah
     */
    private void updateCurrentProfileName() {
        String newProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (newProfileName != null && !newProfileName.equals(currentProfileName)) {
            String oldProfile = currentProfileName;
            currentProfileName = newProfileName;

            if (!Objects.equals(oldProfile, currentProfileName)) {
                // Profile berubah → reload semua drawable
                initDrawables();
            }
        }
    }

    private void initDrawables() {
        Context ctx = Globals.getAppContext();
        if (ctx == null) {
            return;
        }

        Resources res = ctx.getResources();

        // Deteksi mode mouse/trackpad
        isMouseMode = (mModel != null && mModel.getDirection() == OneStick.WAY_MOUSE);

        // ─────────────────────────────────────────────
        //   CUSTOM dari folder edpatch/controls/[profile]/icon/stick/ (prioritas tertinggi)
        // ─────────────────────────────────────────────

        Drawable customOuter = null;

        if (isMouseMode) {
            // Mode trackpad: prioritas nama yang relevan
            customOuter = loadPngFromIconDir("trackpad", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("mousepad", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("touchpad", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("track_pad", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("mouse_area", false);
        } else {
            // Mode joystick normal
            customOuter = loadPngFromIconDir("stick_outer", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("outer", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("base", false);
            if (customOuter == null) customOuter = loadPngFromIconDir("circle_outer", false);
        }

        if (customOuter != null) {
            mOuterDrawable = customOuter.mutate();
        } else {
            // Fallback resource bawaan – berbeda tergantung mode
            int fallbackResId = isMouseMode ? TRACKPAD_OUTER_RES_ID : 2131231064;
            mOuterDrawable = ResourcesCompat.getDrawable(res, fallbackResId, null);
            if (mOuterDrawable != null) {
                mOuterDrawable = mOuterDrawable.mutate();
            }
        }

        // Inner hanya di-load jika BUKAN mode mouse
        if (!isMouseMode) {
            Drawable customInner = loadPngFromIconDir("stick_inner", false);
            if (customInner == null) customInner = loadPngFromIconDir("inner", false);
            if (customInner == null) customInner = loadPngFromIconDir("knob", false);
            if (customInner == null) customInner = loadPngFromIconDir("stick", false);

            if (customInner != null) {
                mInnerDrawable = customInner.mutate();
            } else {
                mInnerDrawable = ResourcesCompat.getDrawable(res, 2131231065, null);
                if (mInnerDrawable != null) {
                    mInnerDrawable = mInnerDrawable.mutate();
                }
            }
        } else {
            mInnerDrawable = null;
        }

        updateDrawablesTint();
    }

    @Nullable
    private File getIconDir() {
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            return null;
        }

        File patchDir = QH.Files.edPatchDir();
        File iconDir = new File(patchDir, "controls/" + currentProfileName + "/stick");

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
    private Drawable loadPngFromIconDir(String baseNamePart, boolean lookForPressed) {
        try {
            File iconDir = getIconDir();
            if (iconDir == null || !iconDir.exists() || !iconDir.isDirectory()) {
                return null;
            }

            String fileNameFound = findFileContaining(iconDir, baseNamePart, null, ".png");
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
                    return f.getName();
                }
            }
        }
        return null;
    }

    private void updateDrawablesTint() {
        if (mModel == null) return;

        int mainColor = mModel.getMainColor();

        // Outer: sama untuk joystick maupun trackpad mode
        if (mOuterDrawable != null) {
            mOuterDrawable.setColorFilter(mainColor, PorterDuff.Mode.SRC_IN);
        }

        // Inner: hanya ada di mode joystick
        if (mInnerDrawable != null) {
            mInnerDrawable.setColorFilter(mainColor, PorterDuff.Mode.SRC_IN);
        }
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
        ButtonStickPressAdapter runtimeAdapter = mAdapter instanceof ButtonStickPressAdapter
                ? (ButtonStickPressAdapter) mAdapter : null;

        if (runtimeAdapter != null) {
            runtimeAdapter.updatePressPos();
        }

        // Pastikan profile name masih sinkron sebelum draw
        updateCurrentProfileName();

        updateDrawablesTint();

        float outerRadius = mModel.getOuterRadius();
        float innerRadiusScale = Const.stickInnerOuterRatio;

        float outerCenterX, outerCenterY;
        float innerCenterX, innerCenterY;

        if (runtimeAdapter != null) {
            innerCenterX = runtimeAdapter.getInnerCenterX();
            innerCenterY = runtimeAdapter.getInnerCenterY();
            outerCenterX = runtimeAdapter.getOuterCenterX();
            outerCenterY = runtimeAdapter.getOuterCenterY();
        } else {
            outerCenterX = innerCenterX = mModel.getLeft() + mModel.getSize() / 2f;
            outerCenterY = innerCenterY = mModel.getTop() + mModel.getSize() / 2f;
        }

        // 1. Outer selalu digambar (base joystick atau area trackpad)
        if (mOuterDrawable != null) {
            int outerLeft   = Math.round(outerCenterX - outerRadius);
            int outerTop    = Math.round(outerCenterY - outerRadius);
            int outerRight  = Math.round(outerCenterX + outerRadius);
            int outerBottom = Math.round(outerCenterY + outerRadius);

            mOuterDrawable.setBounds(outerLeft, outerTop, outerRight, outerBottom);
            mOuterDrawable.draw(canvas);
        }

        // 2. Inner hanya digambar jika BUKAN mode mouse
        if (!isMouseMode && mInnerDrawable != null) {
            float innerRadius = outerRadius * innerRadiusScale;

            int innerLeft   = Math.round(innerCenterX - innerRadius);
            int innerTop    = Math.round(innerCenterY - innerRadius);
            int innerRight  = Math.round(innerCenterX + innerRadius);
            int innerBottom = Math.round(innerCenterY + innerRadius);

            mInnerDrawable.setBounds(innerLeft, innerTop, innerRight, innerBottom);
            mInnerDrawable.draw(canvas);
        }
    }
}