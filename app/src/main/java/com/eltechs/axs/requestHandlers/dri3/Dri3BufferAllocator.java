package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.xserver.XServer;

/**
 * REVISI - createPixmapFromFd sekarang terima XServer sbg parameter pertama
 * (bug sebelumnya: DRI3Requests.java manggil dgn getXServer() sbg arg pertama
 * tapi method ini cuma declare 7 param tanpa XServer - "actual and formal
 * argument lists differ in length" di build log Anda). XServer memang WAJIB
 * di sini krn method ini perlu akses xServer.getPixmapsManager().createPixmap()
 * utk daftarkan Dri3ImportedDrawable yg baru dibuat - bukan optional.
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
    public native long allocateBuffer(int width, int height, int format, long usage);
    public native int exportBufferFd(long bufferHandle);
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
            // import gagal (EGL_NO_IMAGE) - jangan daftarkan pixmap rusak
            return;
        }
        // TODO: root Window dan Visual HARUS diambil dari window yg valid
        // (constraint object-identity Visual - lihat catatan Present sebelumnya),
        // BUKAN null. Ini masih placeholder krn PixmapFromBuffer sendiri belum
        // tentu punya window context langsung (constructor Dri3ImportedDrawable
        // saat ini expect root+visual eksplisit) - perlu XServer punya root
        // window default yg bisa dipakai di sini, atau constructor perlu
        // direvisi terima null sementara & diisi belakangan saat PresentPixmap
        // beneran assign ke window (lebih match kapan Visual constraint itu
        // sebenarnya dicek, yaitu di replaceBackingStores, bukan di sini).
        com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable drawable =
                new com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable(
                        pixmapId, xServer.getWindowsManager().getRootWindow(),
                        width, height, null, imported);
        xServer.getPixmapsManager().createPixmap(drawable);
    }

    public BufferInfo exportPixmapAsFd(int pixmapId) {
        return null; // stub - lihat catatan sebelumnya (butuh eglExportDMABUFImageMESA)
    }
}
