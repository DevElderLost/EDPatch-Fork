package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.xserver.XServer;

/**
 * REVISI KE-2 - openRenderNodeFd() DIHAPUS TOTAL. Pendekatan lama (buka
 * /dev/dri/renderD1xx, kirim sbg device fd DRI3Open) TERBUKTI GAGAL di
 * device nyata (SELinux blokir /dev/dri, fallback /dev/null bikin Vulkan
 * surface creation gagal - lihat CHANGELOG native).
 *
 * SEKARANG mengikuti mekanisme Winlator (dikonfirmasi baca source
 * Winlator-Ludashi-test langsung): DRI3Open tidak kirim fd sama sekali,
 * buffer sharing lewat AHardwareBuffer (importDmaBufFd namanya TETAP SAMA
 * - cuma nama peninggalan revisi lama - tapi ISI implementasinya di C
 * SEKARANG AHardwareBuffer_recvHandleFromUnixSocket, BUKAN dma-buf).
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

    public native void releaseBuffer(long bufferHandle);

    // NAMA MENYESATKAN (peninggalan) - fd di sini socketpair fd protokol
    // Winlator, BUKAN dma-buf. width/height/stride/format TIDAK DIPAKAI lagi
    // di native (AHardwareBuffer bawa metadata sendiri) - tetap dipertahankan
    // di signature biar caller (createPixmapFromFd di bawah) tidak perlu diubah.
    public native long importDmaBufFd(int fd, int width, int height, int stride, int format);

    public void createPixmapFromFd(XServer xServer, int pixmapId, int fd, int width, int height,
                                    int stride, int depth, int bpp) {
        int drmFormat = (depth == 32) ? 1 /*ARGB8888*/ : 4 /*RGB565*/; // sudah tidak dipakai native, dibiarkan utk kompatibilitas signature
        long imported = importDmaBufFd(fd, width, height, stride, drmFormat);
        if (imported == 0) {
            // import gagal (AHardwareBuffer_recvHandleFromUnixSocket gagal, atau
            // eglCreateImageKHR gagal) - jangan daftarkan pixmap rusak
            return;
        }
        // TODO: root Window dan Visual HARUS diambil dari window yg valid
        // (constraint object-identity Visual - lihat catatan Present sebelumnya),
        // BUKAN null. Masih placeholder - lihat catatan lengkap di revisi
        // sebelumnya, belum berubah statusnya.
        com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable drawable =
                new com.eltechs.axs.requestHandlers.dri3.Dri3ImportedDrawable(
                        pixmapId, xServer.getWindowsManager().getRootWindow(),
                        width, height, null, imported);
        xServer.getPixmapsManager().createPixmap(drawable);
    }

    public BufferInfo exportPixmapAsFd(int pixmapId) {
        return null; // stub - belum diimplementasikan (jalur guest<-host, kasus lebih jarang)
    }
}
