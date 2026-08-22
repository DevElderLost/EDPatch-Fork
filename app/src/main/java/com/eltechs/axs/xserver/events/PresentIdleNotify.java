package com.eltechs.axs.xserver.events;

import com.eltechs.axs.xserver.Window;

/**
 * BARU: lihat Javadoc PresentCompleteNotify.java untuk penjelasan pola dan
 * perbedaan semantik super(id) pada event XGE. Wire format diverifikasi
 * terhadap presentproto.txt Appendix A.3 "PresentIdleNotify".
 *
 * PENTING - keterbatasan yang disengaja: 'pixmap' dan 'idleFence' di sini
 * BUKAN benar-benar diverifikasi idle oleh server (tidak ada tracking
 * pixmap-in-use yang solid di implementasi Present saat ini - lihat
 * catatan di PresentRequests.java). Kelas ini disiapkan sebagai
 * infrastruktur wire-format yang SUDAH BENAR, tapi PresentRequests.java
 * SAAT INI TIDAK memanggilnya sama sekali (tidak ada kode yang membuat
 * instance PresentIdleNotify) - itu di luar cakupan perbaikan kali ini,
 * karena idle notify yang benar butuh tracking buffer sinkron dengan
 * DRI3 (ketika pixmap benar2 tidak dipakai lagi), bukan cuma event
 * kosong yang dikirim segera setelah swap seperti PresentCompleteNotify.
 * Mengirim PresentIdleNotify palsu (tidak akurat) lebih berbahaya
 * daripada tidak mengirimnya sama sekali - client bisa menulis ulang
 * buffer yang server masih pakai.
 */
public class PresentIdleNotify extends Event {
    private final int eventId;
    private final Window window;
    private final int serial;
    private final int pixmapId;
    private final int idleFenceId;

    public PresentIdleNotify(int eventId, Window window, int serial, int pixmapId, int idleFenceId) {
        super(com.eltechs.axs.xserver.events.ExtensionEventCodes.PRESENT_EVTYPE_IDLE_NOTIFY);
        this.eventId = eventId;
        this.window = window;
        this.serial = serial;
        this.pixmapId = pixmapId;
        this.idleFenceId = idleFenceId;
    }

    public int getEventId() {
        return this.eventId;
    }

    public Window getWindow() {
        return this.window;
    }

    public int getSerial() {
        return this.serial;
    }

    public int getPixmapId() {
        return this.pixmapId;
    }

    public int getIdleFenceId() {
        return this.idleFenceId;
    }
}
