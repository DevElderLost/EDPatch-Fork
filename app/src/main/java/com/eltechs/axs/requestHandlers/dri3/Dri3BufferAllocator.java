package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.xserver.XServer;

/**
 * REVISI KE-3 - REVERT ke dma-buf. openRenderNodeFd() KEMBALI ADA (dihapus
 * sementara di revisi Winlator, sekarang perlu lagi). Native-nya (dri3_egl_import.c)
 * sudah pakai versi multi-node-scan (renderD128..136) + logging ke
 * image/opt/edpatch/dri3_debug.log - BELUM PERNAH benar2 dites di device,
 * ini test pertama yang sebenarnya utk pertanyaan "apakah /dev/dri terblokir".
 */
public final class Dri3BufferAllocator {

    static {
        System.loadLibrary("dri3-helpers");
    }

    public static final class BufferInfo {
        public int fd;
        public int size;
        public int width, height, stride;
        public int depth, bpp;
    }

    public native int openRenderNodeFd();
    public native void releaseBuffer(long bufferHandle);
    public native long importDmaBufFd(int fd, int width, int height, int stride, int format);

    public int openRenderNodeFdOrThrow() {
        int fd = openRenderNodeFd();
        if (fd < 0) throw new IllegalStateException("DRI3: gagal buka device fd");
        return fd;
    }

    public void createPixmapFromFd(XServer xServer, int pixmapId, int fd, int width, int height,
                                    int stride, int depth, int bpp) {
        int drmFormat = (depth == 32) ? 1 /*ARGB8888*/ : 4 /*RGB565*/;
        long imported = importDmaBufFd(fd, width, height, stride, drmFormat);
        if (imported == 0) {
            return;
        }
        // TODO: root Window dan Visual HARUS diambil dari window yg valid
        // (constraint object-identity Visual - lihat catatan Present sebelumnya),
        // BUKAN null. Masih placeholder.
        com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable drawable =
                new com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable(
                        pixmapId, xServer.getWindowsManager().getRootWindow(),
                        width, height, null, imported);
        xServer.getPixmapsManager().createPixmap(drawable);
    }

    public BufferInfo exportPixmapAsFd(int pixmapId) {
        return null; // stub - belum diimplementasikan
    }
}
