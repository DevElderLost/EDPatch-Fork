package com.example.datainsert.exagear.controlsV2.touchAdapter;

import static com.example.datainsert.exagear.controlsV2.Const.stickMouse_interval;

import android.graphics.PointF;
import android.os.CountDownTimer;
import android.support.annotation.IntDef;

import com.eltechs.axs.geom.Point;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.Finger;
import com.example.datainsert.exagear.controlsV2.TouchAdapter;
import com.example.datainsert.exagear.controlsV2.XServerViewHolder;
import com.example.datainsert.exagear.controlsV2.model.OneStick;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public class ButtonStickPressAdapter implements TouchAdapter {

    public final static int FINGER_AT_LEFT = 1 << 1;
    public final static int FINGER_AT_RIGHT = 1 << 2;
    public final static int FINGER_AT_TOP = 1 << 3;
    public final static int FINGER_AT_BOTTOM = 1 << 4;
    public final static int FINGER_AT_CENTER = 0;

    private static final float tan35d = 0.70020753f;
    private static final float cot35d = 1.42814800f;

    @FingerAt
    public int nowFingerAt = FINGER_AT_CENTER;

    @FingerAt
    protected int lastFingerAt = FINGER_AT_CENTER;

    protected PointF fingerFirstDown = new PointF();
    protected PointF outerCenter = new PointF();
    protected PointF innerCenter = new PointF();

    protected final OneStick mModel;
    protected Finger mFinger;

    private final JoyStickMouseMoveInjector mMouseMoveInjector = new JoyStickMouseMoveInjector();

    // posisi terakhir jari (untuk trackpad delta)
    private float lastFingerX;
    private float lastFingerY;

    public ButtonStickPressAdapter(OneStick model) {
        mModel = model;
        updateRealOuterCenterXYAndFingerDownXY(false);
    }

    public void updatePressPos() {

        boolean isTouching = mFinger != null;

        nowFingerAt = FINGER_AT_CENTER;

        if (!isTouching) {
            innerCenter.x = outerCenter.x;
            innerCenter.y = outerCenter.y;
        } else {

            float xDiffUnlimited = mFinger.getX() - outerCenter.x;
            float yDiffUnlimited = mFinger.getY() - outerCenter.y;

            double unlimitedDist = Math.hypot(xDiffUnlimited, yDiffUnlimited);

            double maxAndUnlimitedDistRatio = mModel.getInnerMaxOffsetFromOuterCenter() / unlimitedDist;

            float xDiffLimited = (float) (xDiffUnlimited * (maxAndUnlimitedDistRatio < 1 ? maxAndUnlimitedDistRatio : 1));
            float yDiffLimited = (float) (yDiffUnlimited * (maxAndUnlimitedDistRatio < 1 ? maxAndUnlimitedDistRatio : 1));

            innerCenter.x = outerCenter.x + xDiffLimited;
            innerCenter.y = outerCenter.y + yDiffLimited;

            float tanCurrent = Math.abs(xDiffLimited / yDiffLimited);

            if (unlimitedDist < Const.stickMoveThreshold) {
                nowFingerAt = FINGER_AT_CENTER;
            } else {

                float tanInVertical = mModel.direction == OneStick.WAY_4 ? 1 : cot35d;
                float tanInHorizontal = mModel.direction == OneStick.WAY_4 ? 1 : tan35d;

                if (tanCurrent <= tanInVertical && yDiffLimited < 0)
                    nowFingerAt |= FINGER_AT_TOP;
                else if (tanCurrent <= tanInVertical && yDiffLimited > 0)
                    nowFingerAt |= FINGER_AT_BOTTOM;

                if (tanCurrent > tanInHorizontal && xDiffLimited < 0)
                    nowFingerAt |= FINGER_AT_LEFT;
                else if (tanCurrent > tanInHorizontal && xDiffLimited > 0)
                    nowFingerAt |= FINGER_AT_RIGHT;
            }
        }
    }

    protected void updateRealOuterCenterXYAndFingerDownXY(boolean isTouching) {

        float centerX = mModel.getLeft() + mModel.getSize() / 2f;
        float centerY = mModel.getTop() + mModel.getSize() / 2f;

        fingerFirstDown.x = isTouching ? mFinger.getXWhenFirstTouched() : centerX;
        fingerFirstDown.y = isTouching ? mFinger.getYWhenFirstTouched() : centerY;

        float xOffFromCenter = fingerFirstDown.x - centerX;
        float yOffFromCenter = fingerFirstDown.y - centerY;

        double maxAndCurrentRadio = mModel.getInnerRadius() / Math.hypot(xOffFromCenter, yOffFromCenter);

        outerCenter.x = (float) (centerX + xOffFromCenter * (maxAndCurrentRadio >= 1 ? 1 : maxAndCurrentRadio));
        outerCenter.y = (float) (centerY + yOffFromCenter * (maxAndCurrentRadio >= 1 ? 1 : maxAndCurrentRadio));
    }

    private void sendKeys() {

        if (mModel.getDirection() == OneStick.WAY_MOUSE) {
            return;
        }

        if ((FINGER_AT_LEFT & lastFingerAt) > 0 && (FINGER_AT_LEFT & nowFingerAt) == 0)
            Const.getXServerHolder().releaseKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_LEFT));
        else if ((FINGER_AT_LEFT & lastFingerAt) == 0 && (FINGER_AT_LEFT & nowFingerAt) > 0)
            Const.getXServerHolder().pressKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_LEFT));

        if ((FINGER_AT_RIGHT & lastFingerAt) > 0 && (FINGER_AT_RIGHT & nowFingerAt) == 0)
            Const.getXServerHolder().releaseKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_RIGHT));
        else if ((FINGER_AT_RIGHT & lastFingerAt) == 0 && (FINGER_AT_RIGHT & nowFingerAt) > 0)
            Const.getXServerHolder().pressKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_RIGHT));

        if ((FINGER_AT_TOP & lastFingerAt) > 0 && (FINGER_AT_TOP & nowFingerAt) == 0)
            Const.getXServerHolder().releaseKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_TOP));
        else if ((FINGER_AT_TOP & lastFingerAt) == 0 && (FINGER_AT_TOP & nowFingerAt) > 0)
            Const.getXServerHolder().pressKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_TOP));

        if ((FINGER_AT_BOTTOM & lastFingerAt) > 0 && (FINGER_AT_BOTTOM & nowFingerAt) == 0)
            Const.getXServerHolder().releaseKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_BOTTOM));
        else if ((FINGER_AT_BOTTOM & lastFingerAt) == 0 && (FINGER_AT_BOTTOM & nowFingerAt) > 0)
            Const.getXServerHolder().pressKeyOrPointer(mModel.getKeycodeAt(OneStick.KEY_BOTTOM));

        lastFingerAt = nowFingerAt;
    }

    @Override
    public void notifyMoved(Finger finger, List<Finger> list) {

        if (mModel.getDirection() == OneStick.WAY_MOUSE) {

            float currentX = finger.getX();
            float currentY = finger.getY();

            float dx = currentX - lastFingerX;
            float dy = currentY - lastFingerY;

            lastFingerX = currentX;
            lastFingerY = currentY;

            mMouseMoveInjector.setDeltaFragment(dx, dy);
            return;
        }

        updatePressPos();
        sendKeys();
    }

    @Override
    public void notifyReleased(Finger finger, List<Finger> list) {

        mFinger = null;

        updateRealOuterCenterXYAndFingerDownXY(false);
        updatePressPos();
        sendKeys();

        if (mModel.getDirection() == OneStick.WAY_MOUSE && mMouseMoveInjector.isRunning)
            mMouseMoveInjector.doStop();

        nowFingerAt = FINGER_AT_CENTER;
        lastFingerAt = FINGER_AT_CENTER;
    }

    @Override
    public void notifyTouched(Finger finger, List<Finger> list) {

        if (mFinger != null)
            return;

        mFinger = finger;

        lastFingerX = finger.getX();
        lastFingerY = finger.getY();

        nowFingerAt = FINGER_AT_CENTER;
        lastFingerAt = FINGER_AT_CENTER;

        updateRealOuterCenterXYAndFingerDownXY(false);
        updatePressPos();

        if (mModel.getDirection() == OneStick.WAY_MOUSE && !mMouseMoveInjector.isRunning)
            mMouseMoveInjector.doStart();

        sendKeys();
    }

    public float getOuterCenterX() {
        return outerCenter.x;
    }

    public float getOuterCenterY() {
        return outerCenter.y;
    }

    public float getInnerCenterX() {
        return innerCenter.x;
    }

    public float getInnerCenterY() {
        return innerCenter.y;
    }

    @IntDef(flag = true,
            value = {FINGER_AT_LEFT, FINGER_AT_RIGHT, FINGER_AT_TOP, FINGER_AT_BOTTOM, FINGER_AT_CENTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FingerAt {}

    private static class JoyStickMouseMoveInjector extends CountDownTimer {

        /**
         * Seberapa dekat (dalam persentase lebar/tinggi layar x-server) pointer boleh mendekati
         * tepi sebelum di-recenter paksa. Dibuat berbasis persentase (bukan px tetap) supaya
         * perilakunya konsisten di berbagai resolusi x-server.
         */
        private static final float EDGE_MARGIN_RATIO = 0.15f;

        boolean isRunning = false;
        PointF deltaXY = new PointF();

        /**
         * Titik acuan tempat pointer di-recenter, DIAMBIL dari posisi pointer x-server saat
         * sesi drag joystick-mouse ini baru mulai (lihat {@link #doStart()}) -- BUKAN titik
         * tengah geometris layar x-server ({@code screenWidth/2, screenHeight/2}).
         * <br/><br/>
         * Kenapa: titik tengah geometris layar x-server belum tentu sama dengan titik yang
         * dianggap "netral"/tengah oleh game (mis. kalau window game tidak full-screen, atau
         * posisi window/viewport game punya offset terhadap layar x-server). Kalau kita warp ke
         * titik yang salah, setiap recenter mengirim delta besar yang konstan ke game -> kamera
         * selalu tersentak ke arah yang sama (mis. selalu melihat ke atas) walau jari digeser ke
         * arah lain. Pakai posisi pointer SAAT drag baru dimulai sebagai acuan aman, karena
         * posisi itu pasti valid/sudah "dikenal" oleh game (baik dari recenter game sendiri
         * ataupun sisa posisi terakhir yang sudah stabil), bukan asumsi kita sendiri.
         */
        private int anchorX = -1;
        private int anchorY = -1;

        public JoyStickMouseMoveInjector() {
            super(10000000, stickMouse_interval);
        }

        public void doStart() {
            isRunning = true;
            captureAnchor();
            start();
        }

        public void doStop() {
            cancel();
            isRunning = false;
            anchorX = -1;
            anchorY = -1;
        }

        /** Simpan posisi pointer x-server saat ini sebagai titik acuan recenter untuk sesi ini. */
        private void captureAnchor() {
            Point pointer = Const.getXServerHolder().getPointerLocation();
            if (pointer != null) {
                anchorX = pointer.x;
                anchorY = pointer.y;
            } else {
                anchorX = -1;
                anchorY = -1;
            }
        }

        public void setDeltaFragment(float x, float y) {
            float smooth = 0.6f;
            deltaXY.set(x * smooth, y * smooth);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            if (deltaXY.x != 0 || deltaXY.y != 0)
                Const.getXServerHolder().injectPointerDelta(deltaXY.x, deltaXY.y);

            // Force-center-cursor: cegah pointer mentok di tepi x-server (lihat javadoc method).
            forceCenterCursorIfNearEdge();
        }

        /**
         * Bug lama: begitu pointer x-server sampai batas layar (mis. tepi kanan), posisi pointer
         * akan "clamp"/mentok di titik itu terus. Karena {@code injectPointerDelta} selalu
         * menghitung posisi baru dari posisi pointer SAAT INI (yang sudah mentok), delta
         * berikutnya ke arah yang sama jadi tidak berpengaruh apa-apa -> kamera game (mode
         * relative-mouse-look) jadi stuck, tidak bisa muter 360 derajat penuh.
         * <br/><br/>
         * Solusi: begitu pointer mendekati tepi (dalam {@link #EDGE_MARGIN_RATIO} dari lebar/
         * tinggi layar), langsung "warp" (teleport) pointer balik ke {@link #anchorX}/
         * {@link #anchorY} -- posisi pointer saat sesi drag ini dimulai, BUKAN titik tengah
         * layar x-server -- lewat {@link XServerViewHolder#injectPointerWarp(float, float)}.
         * Jalur warp ini TERPISAH dari jalur gerakan mouse biasa (injectPointerMove/Delta),
         * persis seperti XWarpPointer/SetCursorPos yang dipakai game FPS sendiri untuk recenter
         * cursor tiap frame pada mode relative-mouse-look mereka.
         * <br/><br/>
         * Kenapa kamera TIDAK ikut mental balik ke posisi awal saat recenter ini terjadi:
         * delta yang dikirim ke kamera dihitung murni dari pergeseran jari di area
         * joystick/touchpad ({@link #setDeltaFragment}, berbasis lastFingerX/Y di
         * {@link ButtonStickPressAdapter#notifyMoved}), BUKAN dari posisi pointer x-server.
         * Warp ini hanya mengubah posisi pointer x-server, tidak pernah dibaca balik untuk
         * menghitung delta berikutnya, jadi tidak menambah/mengurangi delta kamera sama sekali
         * -- SELAMA titik acuannya sendiri valid (lihat javadoc {@link #anchorX}).
         */
        private void forceCenterCursorIfNearEdge() {
            if (anchorX < 0 || anchorY < 0)
                return;

            XServerViewHolder holder = Const.getXServerHolder();

            int[] screen = holder.getXScreenPixels();
            if (screen == null || screen.length < 2 || screen[0] <= 0 || screen[1] <= 0)
                return;

            Point pointer = holder.getPointerLocation();
            if (pointer == null)
                return;

            int marginX = Math.round(screen[0] * EDGE_MARGIN_RATIO);
            int marginY = Math.round(screen[1] * EDGE_MARGIN_RATIO);

            boolean nearEdge =
                    pointer.x <= marginX || pointer.x >= screen[0] - marginX ||
                    pointer.y <= marginY || pointer.y >= screen[1] - marginY;

            if (nearEdge) {
                holder.injectPointerWarp(anchorX, anchorY);
            }
        }

        @Override
        public void onFinish() {
            start();
        }
    }
}