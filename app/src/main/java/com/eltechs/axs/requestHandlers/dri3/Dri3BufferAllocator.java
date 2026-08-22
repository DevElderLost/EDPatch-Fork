package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.xserver.XServer;

/**
 * STATUS: implementasi native (dri3-helpers) BELUM ADA di project ini -
 * dicek langsung: tidak ada src/main/cpp/dri3-*.c ATAUPUN prebuilt .so
 * di src/main/00jniLibs manapun untuk nama "dri3-helpers". Kelas ini
 * SEBELUMNYA punya System.loadLibrary("dri3-helpers") + 3 method native
 * tanpa implementasi apapun - itu artinya baris pertama yang menyentuh
 * kelas ini (class-loading, lewat static initializer) akan langsung
 * UnsatisfiedLinkError dan meng-crash SELURUH XServer, bukan cuma
 * DRI3/Present (karena RootXRequestHandlerConfigurer sekarang membuat
 * instance Dri3BufferAllocator di jalur isHWRenderingAvailable() yang
 * sama dengan DRI2/GLX).
 *
 * Untuk sementara (sampai dri3-helpers.so beneran dibangun dan
 * dibundel), 3 method yang tadinya native diubah jadi stub Java biasa
 * yang SELALU gagal dengan aman (return -1 / 0 / no-op) - bukan native,
 * jadi tidak butuh .so apapun untuk di-load. Efeknya di protokol X11:
 * DRI3.Open akan membalas BadAlloc (bukan crash), DRI3.PixmapFromBuffer
 * diam-diam tidak melakukan apapun (createPixmapFromFd early-return
 * sebelum menyentuh Dri3ImportedDrawable sama sekali - lihat komentar
 * di createPixmapFromFd di bawah). Client DRI3 akan menganggap server
 * kehabisan resource dan mundur ke jalur lama, bukan disconnect paksa.
 *
 * TODO (langkah selanjutnya, terpisah dari perubahan ini): implementasi
 * dri3-helpers.so yang sesungguhnya (openRenderNodeFd via /dev/dri,
 * importDmaBufFd via EGL_EXT_image_dma_buf_import) + CMakeLists.txt
 * yang saat ini juga masih di-comment-out untuk semua lib selain
 * some-helper.
 */
public final class Dri3BufferAllocator {

    public static final class BufferInfo {
        public int fd;
        public int size;
        public int width, height, stride;
        public int depth, bpp;
    }

    // Bukan native lagi - lihat Javadoc kelas di atas. Selalu gagal
    // dengan aman (fd < 0) sampai dri3-helpers.so benar-benar ada.
    public int openRenderNodeFd() {
        return -1;
    }

    // Bukan native lagi - no-op aman. Tidak ada apapun untuk dilepas
    // selama importDmaBufFd() di bawah tidak pernah berhasil (selalu 0).
    public void releaseBuffer(long bufferHandle) {
    }

    // Bukan native lagi - selalu gagal (0 = tidak ada EGLImage yang
    // diimport), konsisten dengan kontrak lama (caller di
    // createPixmapFromFd() sudah menangani imported == 0 sebagai "gagal").
    public long importDmaBufFd(int fd, int width, int height, int stride, int format) {
        return 0;
    }

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
            // Jalur ini SELALU dieksekusi untuk sekarang (importDmaBufFd
            // di atas selalu return 0) - artinya baris di bawah yang
            // memakai Visual=null TIDAK PERNAH tereksekusi. Itu sengaja
            // dibiarkan seperti semula (bukan diperbaiki di sini) karena
            // memperbaikinya butuh Visual asli dari window pemanggil
            // (lihat catatan arsitektur di Dri3ImportedDrawable.java),
            // yang berarti mengubah signature method ini - di luar
            // cakupan perbaikan wiring/build kali ini.
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
