package com.eltechs.axs.requestHandlers.dri3;

/**
 * TIDAK ADA padanan kelas ini di codebase asli - saya cek seluruh 22 .so,
 * nol referensi AHardwareBuffer_*/ASurfaceControl_*/ASurfaceTransaction_*.
 * Ini benar-benar subsistem baru, native side harus ditulis dari nol
 * (lib baru, misal libdri3-helpers.so, di-load via System.loadLibrary di <clinit>).
 *
 * Prinsip: setiap "buffer" DRI3 dibackingi AHardwareBuffer (NDK,
 * android/hardware_buffer.h, tersedia sejak API 26 - cek minSdk project Anda
 * dulu). AHardwareBuffer dipilih (bukan raw GBM/DRM) karena ini SATU-SATUNYA
 * cara resmi Android untuk dapat buffer GPU yang bisa diekspor jadi fd tanpa
 * root/vendor driver access langsung.
 */
public final class Dri3BufferAllocator {

    static {
        System.loadLibrary("dri3-helpers"); // lib baru, belum ada di zip Anda
    }

    public static final class BufferInfo {
        public int fd;
        public int size;
        public int width, height, stride;
        public int depth, bpp;
    }

    /**
     * DRI3Open: server perlu kasih klien sebuah "device fd". Di Mesa asli ini fd
     * ke /dev/dri/renderD1xx. Android generik nggak selalu expose itu ke app
     * biasa (butuh app punya akses node render, tergantung SELinux policy vendor).
     *
     * Kalau /dev/dri/renderD128 dst tidak bisa dibuka (kemungkinan besar di
     * banyak device non-rooted), fallback realistis: buka fd dummy/memfd
     * (lihat implementasi native openRenderNodeFdImpl) - klien Mesa modern
     * (loader_dri3) tetap jalan asal fd valid dan ioctl DRM yang benar-benar
     * dipakai (buffer alloc) lewat AHardwareBuffer, BUKAN lewat fd device ini.
     * fd device DRI3Open kebanyakan cuma dipakai buat authentifikasi/versioning,
     * bukan jalur alokasi buffer utama di implementasi non-Mesa-native seperti ini.
     */
    public native int openRenderNodeFd();

    /** Alokasi AHardwareBuffer baru & ekspor sebagai dma-buf-compatible fd. */
    public native long allocateBuffer(int width, int height, int format, long usage);
    public native int exportBufferFd(long bufferHandle);
    public native void releaseBuffer(long bufferHandle);

    /**
     * PixmapFromBuffer: klien SUDAH ngirim fd (lewat SCM_RIGHTS, sudah di-recv
     * di DRI3Requests.handlePixmapFromBuffer). Native side wrap fd itu balik jadi
     * AHardwareBuffer via AHardwareBuffer_recvHandleFromUnixSocket (INI YANG
     * PALING RISKY - lihat catatan di bawah), lalu associate ke Pixmap ID X server
     * (harus nyambung ke PersistentGLDrawable/Pixmap manager yang sudah ada supaya
     * GLXRequests & compositor tetap bisa gambar ke pixmap yang sama).
     *
     * CATATAN RISIKO: AHardwareBuffer_recvHandleFromUnixSocket() itu API resmi NDK,
     * TAPI ia expect fd yang dikirim lewat AHardwareBuffer_sendHandleToUnixSocket()
     * di sisi pengirim - protokolnya proprietary Android (bukan dma-buf mentah).
     * Kalau fd yang dikirim guest itu dma-buf fd MENTAH dari Mesa (bukan hasil
     * AHardwareBuffer_send...), fungsi ini KEMUNGKINAN BESAR GAGAL PARSE.
     * Guest Mesa (loader_dri3) ngirim raw dma-buf fd standar Linux, bukan format
     * Android. Jadi jalur "aman"-nya: JANGAN pakai AHardwareBuffer_recvHandle...
     * untuk terima dari guest - terima fd mentah lewat recvmsg/SCM_RIGHTS biasa
     * (generic, lihat SocketWrapperFdExt), lalu import fd itu ke GL context lokal
     * pakai EGL_EXT_image_dma_buf_import (eglCreateImageKHR dengan target
     * EGL_LINUX_DMA_BUF_EXT) - BUKAN lewat AHardwareBuffer sama sekali untuk arah
     * guest->host. AHardwareBuffer baru relevan untuk arah host->SurfaceFlinger
     * (presentasi akhir ke layar), bukan untuk terima buffer dari guest.
     */
    public native long importDmaBufFd(int fd, int width, int height, int stride, int format);

    public int openRenderNodeFdOrThrow() {
        int fd = openRenderNodeFd();
        if (fd < 0) throw new IllegalStateException("DRI3: gagal buka device fd");
        return fd;
    }

    public void createPixmapFromFd(int pixmapId, int fd, int width, int height,
                                    int stride, int depth, int bpp) {
        long imported = importDmaBufFd(fd, width, height, stride, depth == 32 ? /*ARGB8888*/ 1 : /*RGB565*/ 4);
        // TODO: daftarkan `imported` ke Pixmap manager X server yang sudah ada
        // (com.eltechs.axs.xserver.impl... - belum saya map exact class-nya,
        // perlu telusur lanjut kalau mau sambung ke compositor asli).
    }

    public BufferInfo exportPixmapAsFd(int pixmapId) {
        // TODO: ambil AHardwareBuffer/EGLImage yang terasosiasi pixmapId,
        // lalu export ulang jadi dma-buf fd via EGL_EXT_image_dma_buf_export
        // (eglExportDMABUFImageMESA / eglExportDMABUFImageQueryMESA - cek
        // apakah driver GPU device target expose extension ini; Turnip/Mali
        // umumnya ya, tapi WAJIB dicek runtime, bukan diasumsikan).
        return null; // stub
    }
}
