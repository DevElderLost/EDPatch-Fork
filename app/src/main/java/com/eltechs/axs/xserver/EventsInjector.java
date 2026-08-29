package com.eltechs.axs.xserver;

public interface EventsInjector {
    void injectKeyPress(byte b, int i);

    void injectKeyRelease(byte b, int i);

    void injectPointerButtonPress(int i);

    void injectPointerButtonRelease(int i);

    void injectPointerMove(int x, int y);

    /**
     * Sama seperti {@link #injectPointerMove(int, int)}, tapi lewat jalur "warp" (setara
     * XWarpPointer/SetCursorPos), bukan jalur motion biasa. Dipakai untuk force-center-cursor
     * pada mode joystick-mouse, supaya recenter ini dikenali sebagai reposisi programatik
     * (bukan gerakan asli), dan tidak dianggap sebagai delta gerakan kamera oleh game/guest.
     */
    void injectPointerWarp(int x, int y);
}
