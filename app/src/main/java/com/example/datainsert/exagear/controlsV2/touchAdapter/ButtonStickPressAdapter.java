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
         * Kill-switch: set ke {@code false} untuk mematikan seluruh koreksi tepi (soft-wall),
         * tanpa mengubah apa pun di file lain. Kalau di-set false dan bug masih muncul di luar
         * kotak joystick ini, berarti sumbernya bukan di sini.
         */
        private static final boolean EDGE_CORRECTION_ENABLED = true;

        /**
         * Mulai koreksi begitu pointer masuk zona sejauh ini (persentase lebar/tinggi layar
         * x-server) dari tepi. Dibuat berbasis persentase supaya konsisten di berbagai resolusi.
         */
        private static final float SOFT_ZONE_RATIO = 0.30f;

        /**
         * Kekuatan dorongan koreksi maksimum (px per tick), dipakai saat pointer PERSIS di tepi
         * (penetrasi 100%). Nilai kecil di awal zona, membesar mendekati tepi (lihat kuadratik
         * di {@link #computeCorrection}), supaya tidak terasa sebagai tahanan tiba-tiba.
         */
        private static final float MAX_CORRECTION_PX_PER_TICK = 10f;

        boolean isRunning = false;
        PointF deltaXY = new PointF();

        public JoyStickMouseMoveInjector() {
            super(10000000, stickMouse_interval);
        }

        public void doStart() {
            isRunning = true;
            start();
        }

        public void doStop() {
            cancel();
            isRunning = false;
            // Reset delta juga, supaya kalau ada 1 tick "nyasar" yang sempat lolos tepat saat
            // cancel() dipanggil (celah kecil yang mungkin terjadi di CountDownTimer Android bila
            // pesan tick sudah terlanjur di-queue sebelum cancel() diproses), tick nyasar itu
            // tidak menggerakkan pointer sama sekali (deltaXY sudah nol).
            deltaXY.set(0, 0);
        }

        public void setDeltaFragment(float x, float y) {
            float smooth = 0.6f;
            deltaXY.set(x * smooth, y * smooth);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            // Guard eksplisit: cegah 1 tick "nyasar" yang mungkin lolos tepat saat cancel()
            // dipanggil dari thread lain/di titik waktu yang sama (lihat catatan di doStop()).
            if (!isRunning) return;

            float dx = deltaXY.x;
            float dy = deltaXY.y;

            // Koreksi tepi (soft-wall): lihat javadoc computeCorrection(). Dibungkus try/catch +
            // kill-switch supaya fitur ini TIDAK MUNGKIN mengganggu/memblokir input pointer dari
            // kontrol lain kalau ada error tak terduga di sini (mis. x-server belum siap saat
            // rotasi layar) -- lihat EDGE_CORRECTION_ENABLED.
            if (EDGE_CORRECTION_ENABLED) {
                try {
                    PointF correction = computeCorrection();
                    dx += correction.x;
                    dy += correction.y;
                } catch (Exception ignored) {
                    // Sengaja diabaikan: kegagalan di koreksi TIDAK BOLEH mengganggu input
                    // pointer dari kontrol lain.
                }
            }

            if (dx != 0 || dy != 0)
                Const.getXServerHolder().injectPointerDelta(dx, dy);
        }

        /**
         * Bug lama (round 1): begitu pointer x-server sampai batas layar, posisi pointer
         * "clamp"/mentok di titik itu terus -> kamera (mode relative-mouse-look) jadi stuck.
         * Game ini TERKONFIRMASI TIDAK punya auto-recenter sendiri (GetCursorPos absolut dibaca
         * tiap frame apa adanya, tanpa SetCursorPos balik ke tengah) -- jadi butuh bantuan dari
         * sisi kita, bukan cuma menunggu game menanganinya sendiri.
         * <br/><br/>
         * Pendekatan LAMA (hard-recenter/teleport sekali ke titik tertentu saat dekat tepi) TIDAK
         * BISA dipakai di sini: dikonfirmasi lewat pembacaan smali {@code LorieView.sendMouseEvent}
         * (native) bahwa satu-satunya jalur pengiriman posisi mouse ke guest HANYA mendukung
         * koordinat ABSOLUT -- tidak ada mode relative/delta, dan tidak ada cara membuat suatu
         * perubahan posisi "tidak terlihat" oleh guest. Teleport sekali besar SELALU muncul
         * sebagai satu lompatan tiba-tiba di posisi absolut yang dibaca game sebagai input asli
         * (baik lewat recenter "silent" di sisi Java maupun warp biasa -- keduanya ujungnya
         * menghasilkan satu lompatan diskontinu yang sama besar begitu delta normal berikutnya
         * dihitung dari titik hasil recenter).
         * <br/><br/>
         * Solusi di sini: JANGAN pernah kirim lompatan diskontinu sama sekali. Sebagai ganti,
         * begitu pointer masuk zona {@link #SOFT_ZONE_RATIO} dari tepi, tambahkan dorongan kecil
         * berlawanan arah tepi ke DELTA NORMAL yang sudah mau dikirim tiap tick (bukan event
         * terpisah) -- besarnya kuadratik terhadap seberapa dalam pointer masuk zona (kecil di
         * awal zona, membesar mendekati tepi asli). Karena dorongan ini MENYATU dengan delta
         * biasa (bukan lompatan absolut terpisah), game tidak melihat diskontinuitas -- yang
         * terasa cuma seperti sedikit "tahanan" saat mendekati tepi layar, bukan kamera
         * tersentak/lompat. Selama joystick tidak ditahan penuh ke satu arah terus-menerus,
         * pointer tidak akan pernah benar-benar mencapai tepi asli.
         */
        private PointF computeCorrection() {
            PointF correction = new PointF(0, 0);

            XServerViewHolder holder = Const.getXServerHolder();
            int[] screen = holder.getXScreenPixels();
            if (screen == null || screen.length < 2 || screen[0] <= 0 || screen[1] <= 0)
                return correction;

            Point pointer = holder.getPointerLocation();
            if (pointer == null)
                return correction;

            float softZoneX = screen[0] * SOFT_ZONE_RATIO;
            float softZoneY = screen[1] * SOFT_ZONE_RATIO;

            if (softZoneX > 0) {
                if (pointer.x < softZoneX) {
                    float penetration = (softZoneX - pointer.x) / softZoneX; // 0..1
                    correction.x += MAX_CORRECTION_PX_PER_TICK * penetration * penetration;
                } else if (pointer.x > screen[0] - softZoneX) {
                    float penetration = (pointer.x - (screen[0] - softZoneX)) / softZoneX;
                    correction.x -= MAX_CORRECTION_PX_PER_TICK * penetration * penetration;
                }
            }

            if (softZoneY > 0) {
                if (pointer.y < softZoneY) {
                    float penetration = (softZoneY - pointer.y) / softZoneY;
                    correction.y += MAX_CORRECTION_PX_PER_TICK * penetration * penetration;
                } else if (pointer.y > screen[1] - softZoneY) {
                    float penetration = (pointer.y - (screen[1] - softZoneY)) / softZoneY;
                    correction.y -= MAX_CORRECTION_PX_PER_TICK * penetration * penetration;
                }
            }

            return correction;
        }

        @Override
        public void onFinish() {
            start();
        }
    }
}