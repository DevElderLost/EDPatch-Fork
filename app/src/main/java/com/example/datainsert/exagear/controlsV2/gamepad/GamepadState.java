package com.example.datainsert.exagear.controlsV2.gamepad;

import java.nio.ByteBuffer;

/**
 * Representasi state gamepad virtual (analog thumbstick, trigger, dpad, tombol).
 * <br/> Struktur & urutan field (khususnya {@link #writeTo(ByteBuffer)}) SENGAJA dibuat
 * identik dengan com.winlator.cmod.inputcontrols.GamepadState milik Winlator, supaya
 * DLL xinput1_x/dinput8 hasil patch ala-Winlator bisa langsung dipakai tanpa perlu
 * diubah lagi protokolnya di sisi guest/Wine.
 */
public class GamepadState {
    // Indeks bit tombol (harus sama dengan Winlator ExternalController.IDX_BUTTON_*)
    public static final byte IDX_BUTTON_A = 0;
    public static final byte IDX_BUTTON_B = 1;
    public static final byte IDX_BUTTON_X = 2;
    public static final byte IDX_BUTTON_Y = 3;
    public static final byte IDX_BUTTON_L1 = 4;
    public static final byte IDX_BUTTON_R1 = 5;
    public static final byte IDX_BUTTON_SELECT = 6;
    public static final byte IDX_BUTTON_START = 7;
    public static final byte IDX_BUTTON_L3 = 8; // klik thumbstick kiri
    public static final byte IDX_BUTTON_R3 = 9; // klik thumbstick kanan
    public static final byte IDX_BUTTON_L2 = 10; // trigger kiri sbg tombol digital (jika triggerType bukan axis)
    public static final byte IDX_BUTTON_R2 = 11; // trigger kanan sbg tombol digital

    public float thumbLX = 0;
    public float thumbLY = 0;
    public float thumbRX = 0;
    public float thumbRY = 0;
    public float triggerL = 0;
    public float triggerR = 0;
    /** dpad[0]=up, dpad[1]=right, dpad[2]=down, dpad[3]=left */
    public final boolean[] dpad = new boolean[4];
    public short buttons = 0;

    public byte getPovHat() {
        byte povHat = -1;
        if (dpad[0] && dpad[1]) povHat = 1;
        else if (dpad[1] && dpad[2]) povHat = 3;
        else if (dpad[2] && dpad[3]) povHat = 5;
        else if (dpad[3] && dpad[0]) povHat = 7;
        else if (dpad[0]) povHat = 0;
        else if (dpad[1]) povHat = 2;
        else if (dpad[2]) povHat = 4;
        else if (dpad[3]) povHat = 6;
        return povHat;
    }

    /** Serialisasi ke ByteBuffer, format & urutan HARUS sama dengan yang dibaca sisi guest. */
    public void writeTo(ByteBuffer buffer) {
        buffer.putShort(buttons);
        buffer.put(getPovHat());
        buffer.putShort((short) (thumbLX * Short.MAX_VALUE));
        buffer.putShort((short) (thumbLY * Short.MAX_VALUE));
        buffer.putShort((short) (thumbRX * Short.MAX_VALUE));
        buffer.putShort((short) (thumbRY * Short.MAX_VALUE));
        buffer.put((byte) (triggerL * 255));
        buffer.put((byte) (triggerR * 255));
    }

    public void setPressed(int buttonIdx, boolean pressed) {
        int flag = 1 << buttonIdx;
        if (pressed) buttons |= flag;
        else buttons &= ~flag;
    }

    public boolean isPressed(int buttonIdx) {
        return (buttons & (1 << buttonIdx)) != 0;
    }

    public void reset() {
        thumbLX = thumbLY = thumbRX = thumbRY = 0;
        triggerL = triggerR = 0;
        dpad[0] = dpad[1] = dpad[2] = dpad[3] = false;
        buttons = 0;
    }
}
