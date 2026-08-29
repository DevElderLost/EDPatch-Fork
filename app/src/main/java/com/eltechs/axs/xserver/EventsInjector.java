package com.eltechs.axs.xserver;

public interface EventsInjector {
    void injectKeyPress(byte b, int i);

    void injectKeyRelease(byte b, int i);

    void injectPointerButtonPress(int i);

    void injectPointerButtonRelease(int i);

    void injectPointerMove(int x, int y);

    /**
     * Reposisi pointer TANPA memicu event apa pun ke guest (tidak ada motion event yang
     * dikirim sama sekali -- beda dengan {@link #injectPointerMove}). Hanya mengubah posisi
     * internal yang jadi basis perhitungan {@code injectPointerMove}/delta berikutnya.
     * Dipakai untuk force-center-cursor pada mode joystick-mouse, supaya recenter ini tidak
     * dibaca guest/game sebagai gerakan mouse asli.
     */
    void injectPointerWarp(int x, int y);
}
