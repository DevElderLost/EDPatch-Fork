package com.example.datainsert.exagear.controlsV2.touchArea;

import static android.graphics.Color.TRANSPARENT;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.FILL;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.STROKE;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.FILL_STROKE;
import static com.example.datainsert.exagear.controlsV2.Const.BtnColorStyle.ICON_ONLY;
import static com.example.datainsert.exagear.controlsV2.Const.TOUCH_AREA_ROUND_CORNER_RADIUS;
import static com.example.datainsert.exagear.controlsV2.Const.TOUCH_AREA_STROKE_WIDTH;
import static com.example.datainsert.exagear.controlsV2.Const.NAME_DISPLAY_HIDE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextPaint;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.TestHelper;
import com.example.datainsert.exagear.controlsV2.TouchAdapter;
import com.example.datainsert.exagear.controlsV2.TouchArea;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.controlsV2.model.OneButton;
import com.example.datainsert.exagear.controlsV2.touchAdapter.ButtonPressAdapter;
import com.example.datainsert.exagear.QH;

import java.io.File;
import java.util.Objects;

public class TouchAreaButton extends TouchArea<OneButton> {

    private final TextPaint mTextPaint = new TextPaint();

    private Drawable mShapeDrawable;
    private Drawable mIconDrawable;

    private String lastText = "";
    private String renderText = "";
    private int[] lastWH = {0, 0};

    // Menyimpan nama profile yang sedang digunakan untuk load icon
    private String currentProfileName = null;

    public TouchAreaButton(@NonNull OneButton data) {
        this(data, new ButtonPressAdapter(data.getKeycodes()));
    }

    public TouchAreaButton(@NonNull OneButton data, @Nullable TouchAdapter adapter) {
        super(data, adapter != null ? adapter : new ButtonPressAdapter(data.getKeycodes()));
        
        // Ambil nama profile awal
        updateCurrentProfileName();
        
        initBackgroundDrawable();
        updatePaint();
    }

    /**
     * Update nama profile yang sedang aktif dan cek apakah perlu reload icon
     */
    private void updateCurrentProfileName() {
        String newProfileName = ModelProvider.getCurrentProfileCanonicalName();
        if (newProfileName != null && !newProfileName.equals(currentProfileName)) {
            String oldProfile = currentProfileName;
            currentProfileName = newProfileName;
            
            // Jika profile berubah → reload icon
            if (!Objects.equals(oldProfile, currentProfileName)) {
                mIconDrawable = loadCustomButtonPng();
            }
        }
    }

    private void initBackgroundDrawable() {
        Context ctx = Globals.getAppContext();
        if (ctx == null) return;

        mShapeDrawable = new GradientDrawable();
        ((GradientDrawable) mShapeDrawable).setCornerRadius(
                mModel.getShape() == Const.BtnShape.RECT
                        ? TOUCH_AREA_ROUND_CORNER_RADIUS
                        : 400f
        );

        mIconDrawable = loadCustomButtonPng();
    }

    @Nullable
    private Drawable loadCustomButtonPng() {
        // Jika belum ada profile name → tidak load icon
        if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
            return null;
        }

        try {
            File iconDir = getProfileIconDir(currentProfileName);
            if (!iconDir.exists() || !iconDir.isDirectory()) {
                return null;
            }

            String buttonName = mModel.getName();
            if (buttonName == null || buttonName.trim().isEmpty()) {
                return null;
            }

            String targetFileName = findExactNameFile(iconDir, buttonName, ".png");
            if (targetFileName == null) {
                return null;
            }

            File pngFile = new File(iconDir, targetFileName);
            if (!pngFile.isFile() || !pngFile.canRead()) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(pngFile.getAbsolutePath());
            if (bitmap == null) {
                return null;
            }

            return new BitmapDrawable(Globals.getAppContext().getResources(), bitmap);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lokasi folder icon sekarang mengikuti nama profile
     * Contoh: edPatchDir/controls/NamaProfile/icon/
     */
    private File getProfileIconDir(String profileName) {
        File patchDir = QH.Files.edPatchDir();
        File profileIconDir = new File(patchDir, "controls/" + profileName);

        // Buat folder jika belum ada (opsional)
        if (!profileIconDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            profileIconDir.mkdirs();
        }

        return profileIconDir;
    }

    @Nullable
    private String findExactNameFile(File dir, String buttonName, String extension) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return null;

        String targetLower = (buttonName.trim() + extension).toLowerCase();

        for (File f : files) {
            if (!f.isFile()) continue;

            String nameLower = f.getName().toLowerCase();

            if (nameLower.equals(targetLower)) {
                return f.getName();
            }
        }
        return null;
    }

    /**
     * Method publik yang dipanggil ketika profile berganti
     * (biasanya dari TouchAreaView atau ControlsFragment setelah ganti profile)
     */
    public void onProfileChanged() {
        updateCurrentProfileName();
        // Icon sudah di-reload di dalam updateCurrentProfileName jika berubah
        //invalidate();  // paksa redraw
    }

    private void updatePaint() {
        // Sebelum update paint, pastikan profile name masih sinkron
        updateCurrentProfileName();

        int style = mModel.getColorStyle();
        int ColorPressed = 0xffff420c;
        boolean isPressed = mModel.isPressed();

        int baseColor = mModel.getMainColor();
        int currentMainColor = isPressed ? ColorPressed : baseColor;

        if (mShapeDrawable instanceof GradientDrawable) {

            GradientDrawable gd = (GradientDrawable) mShapeDrawable;

            int fillColor;
            int strokeColor;
            int strokeWidth;

            if (style == STROKE) {
                fillColor = TRANSPARENT;
                strokeColor = currentMainColor;
                strokeWidth = TOUCH_AREA_STROKE_WIDTH;
            } else if (style == FILL) {
                fillColor = currentMainColor;
                strokeColor = TRANSPARENT;
                strokeWidth = 0;
            } else if (style == FILL_STROKE) {
                fillColor = currentMainColor;
                strokeColor = TestHelper.darkenColorFloat(baseColor, isPressed ? 0.6f : 0.65f);
                strokeWidth = TOUCH_AREA_STROKE_WIDTH;
            } else if (style == ICON_ONLY) {
                fillColor = TRANSPARENT;
                strokeColor = TRANSPARENT;
                strokeWidth = 0;
            } else {
                fillColor = TRANSPARENT;
                strokeColor = currentMainColor;
                strokeWidth = 0;
            }

            gd.setStroke(strokeWidth, strokeColor);
            gd.setColor(fillColor);

            gd.setBounds(
                    mModel.getLeft(),
                    mModel.getTop(),
                    mModel.getLeft() + mModel.getWidth(),
                    mModel.getTop() + mModel.getHeight()
            );
        }

        if (mIconDrawable != null) {

            int iconColor;

            if (style == STROKE) {
                iconColor = currentMainColor;
            } else if (style == FILL || style == FILL_STROKE) {
                iconColor = TestHelper.getContrastColor(currentMainColor);
            } else {
                iconColor = currentMainColor;
            }

            mIconDrawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);

            int btnLeft = mModel.getLeft();
            int btnTop = mModel.getTop();
            int btnWidth = mModel.getWidth();
            int btnHeight = mModel.getHeight();

            int centerX = btnLeft + btnWidth / 2;
            int centerY = btnTop + btnHeight / 2;

            int iconWidth;
            int iconHeight;

            if (style == ICON_ONLY) {
                iconWidth = btnWidth;
                iconHeight = btnHeight;
            } else {
                iconWidth = (int) (btnWidth * 0.7f);
                iconHeight = (int) (btnHeight * 0.7f);
            }

            int halfW = iconWidth / 2;
            int halfH = iconHeight / 2;

            int left = centerX - halfW;
            int top = centerY - halfH;
            int right = centerX + halfW;
            int bottom = centerY + halfH;

            mIconDrawable.setBounds(left, top, right, bottom);
        }

        int textColor = (style == STROKE)
                ? currentMainColor
                : TestHelper.getContrastColor(currentMainColor);

        mTextPaint.setColor(textColor);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setStyle(Paint.Style.FILL);

        boolean shouldShowName = mModel.getNameDisplayMode() != NAME_DISPLAY_HIDE;

        if (shouldShowName &&
                (!lastText.equals(mModel.getName()) ||
                        lastWH[0] != mModel.getWidth() ||
                        lastWH[1] != mModel.getHeight())) {

            renderText = lastText = mModel.getName();

            lastWH = new int[]{
                    mModel.getWidth(),
                    mModel.getHeight()
            };

            setPaintTextSize(
                    renderText,
                    mTextPaint,
                    mModel.getWidth() - TOUCH_AREA_STROKE_WIDTH * 2
            );

        } else if (!shouldShowName) {
            renderText = "";
        }
    }

    static void setPaintTextSize(String str, Paint paint, int maxTotalWidth) {
        if (str == null || str.isEmpty())
            return;

        float size = paint.measureText(str);

        size = paint.getTextSize() * maxTotalWidth / size;

        size = Math.min(size, Const.TOUCH_AREA_MAX_TEXT_SIZE);

        size = Math.max(Const.TOUCH_AREA_MIN_TEXT_SIZE, size);

        paint.setTextSize(size);
    }

    @Override
    public void onDraw(Canvas canvas) {
        // Pastikan data profile masih up-to-date sebelum draw
        updateCurrentProfileName();
        updatePaint();

        canvas.save();

        float centerX = mModel.getLeft() + mModel.getWidth() / 2f;
        float centerY = mModel.getTop() + mModel.getHeight() / 2f;

        float left = mModel.getLeft();
        float top = mModel.getTop();

        float right = left + mModel.getWidth();
        float bottom = top + mModel.getHeight();

        if (mModel.isPressed()) {
            canvas.scale(
                    1.2f,
                    1.2f,
                    centerX,
                    centerY
            );
        }

        canvas.clipRect(left, top, right, bottom);

        if (mShapeDrawable != null) {
            mShapeDrawable.draw(canvas);
        }

        if (mIconDrawable != null) {
            mIconDrawable.draw(canvas);
        }

        boolean shouldShowName = mModel.getNameDisplayMode() != NAME_DISPLAY_HIDE;

        if (shouldShowName && !renderText.isEmpty()) {

            canvas.clipRect(
                    mModel.getLeft() + TOUCH_AREA_STROKE_WIDTH,
                    mModel.getTop(),
                    mModel.getLeft() + mModel.getWidth() - TOUCH_AREA_STROKE_WIDTH,
                    mModel.getTop() + mModel.getHeight()
            );

            canvas.drawText(
                    renderText,
                    centerX,
                    TestHelper.adjustTextPaintCenterY(centerY, mTextPaint),
                    mTextPaint
            );
        }

        canvas.restore();
    }
}