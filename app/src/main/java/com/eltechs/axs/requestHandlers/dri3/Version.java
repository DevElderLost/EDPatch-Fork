package com.eltechs.axs.requestHandlers.dri3;

public final class Version {
    private Version() {}
    // 1.0 cukup untuk Open+PixmapFromBuffer+BufferFromPixmap (yang DXVK/Mesa loader_dri3 butuhkan).
    // Naikkan ke 1.2 nanti kalau mau implement PixmapFromBuffers/BuffersFromPixmap (multi-plane).
    public static final int MAJOR = 1;
    public static final int MINOR = 0;
}
