package com.eltechs.axs.requestHandlers.dri3;

/**
 * DRI3 request opcodes - mirrors upstream X.org dri3proto.h (X_DRI3QueryVersion..).
 * Alasan kenapa DXVK butuh ini: DXVK (lewat Mesa "loader_dri3") mensyaratkan
 * extension DRI3 + Present tersedia untuk direct rendering / zero-copy buffer
 * handoff. Server ini sebelumnya cuma expose DRI2 sebagai stub (QueryVersion doang),
 * sehingga probe DRI3 milik Mesa gagal dan DXVK/Wine jatuh ke path lama (indirect GLX
 * lewat GLXRequests) yang jauh lebih lambat / kadang tidak didukung DXVK sama sekali.
 *
 * Ikuti pola persis com.eltechs.axs.requestHandlers.dri2.Opcodes.
 */
public final class Opcodes {
    private Opcodes() {}

    public static final int QueryVersion        = 0;
    public static final int Open                = 1;
    public static final int PixmapFromBuffer     = 2;
    public static final int BufferFromPixmap     = 3;
    public static final int FenceFromFD          = 4;
    public static final int FDFromFence          = 5;
    public static final int GetSupportedModifiers = 6;
    public static final int PixmapFromBuffers    = 7; // DRI3 v1.2, multi-plane (YUV dsb) - opsional
    public static final int BuffersFromPixmap    = 8; // DRI3 v1.2 - opsional
}
