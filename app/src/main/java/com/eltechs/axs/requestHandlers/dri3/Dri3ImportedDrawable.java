package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.xserver.Drawable;
import com.eltechs.axs.xserver.Painter;
import com.eltechs.axs.xserver.Window;
import com.eltechs.axs.xserver.impl.drawables.Visual;

/**
 * TIDAK extends PersistentGLDrawable (lihat catatan arsitektur - content-nya
 * PersistentGLDrawable itu CPU malloc buffer, sedangkan ini EGLImage/GPU
 * handle - beda semantik total, gabung keduanya bakal crash native code yg
 * expect content sbg CPU pointer).
 *
 * CONSTRAINT PENTING (ketemu di bytecode WindowImpl.replaceBackingStores -
 * lihat analysis/05): getVisual() WAJIB return objek Visual yg SAMA PERSIS
 * (identity, bukan cuma equals) dgn window.getFrontBuffer().getVisual() lama.
 * Makanya constructor WAJIB terima Visual dari caller (PresentRequests, yg
 * sudah punya window lama-nya), BUKAN bikin/lookup Visual baru sendiri.
 */
public final class Dri3ImportedDrawable implements Drawable {

    private final int id;
    private final Window root;
    private final int width;
    private final int height;
    private final Visual visual;      // WAJIB reference yg sama dgn window lama
    private final long eglImageHandle; // dari Dri3BufferAllocator.importDmaBufFd()

    public Dri3ImportedDrawable(int id, Window root, int width, int height,
                                 Visual visual, long eglImageHandle) {
        this.id = id;
        this.root = root;
        this.width = width;
        this.height = height;
        this.visual = visual;
        this.eglImageHandle = eglImageHandle;
    }

    public long getEglImageHandle() {
        return eglImageHandle;
    }

    @Override public int getId() { return id; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public Window getRoot() { return root; }
    @Override public Visual getVisual() { return visual; }

    @Override
    public Painter getPainter() {
        // SENGAJA tidak diimplementasi. Klien DRI3 selalu render ke buffer ini
        // lewat GPU driver-nya sendiri (di luar X server sepenuhnya) - tidak
        // ada jalur wajar di mana X server perlu Painter utk pixmap ini. Kalau
        // guest ternyata coba PutImage/CopyArea ke sini (klien "aneh" yg
        // campur DRI3 dgn core drawing), ini akan throw - lebih baik gagal
        // eksplisit drpada silent-corrupt buffer yg lagi dipegang GPU.
        throw new UnsupportedOperationException(
            "Dri3ImportedDrawable tidak support core X11 drawing operations - " +
            "buffer ini dikelola guest lewat GPU driver-nya sendiri.");
    }

    @Override
    public void installModificationListener(ModificationListener listener) {
        // No-op SENGAJA. Alasan: setiap frame baru dari guest datang lewat
        // PresentPixmap request BARU (pixmap ID baru tiap kali, biasanya -
        // Mesa loader_dri3 umumnya rotate beberapa buffer), yg masing2 trigger
        // Window.replaceBackingStores() -> sendFrontBufferReplaced() SENDIRI.
        // Compositor sudah dapat notify dari jalur itu, TIDAK butuh notify
        // per-Drawable tambahan spt ini. Kalau ternyata ada kode lain yg
        // BERGANTUNG listener ini benar2 terpasang (belum saya verifikasi
        // exhaustif), perlu diinvestigasi lebih lanjut - flag ini sbg asumsi.
    }
}
