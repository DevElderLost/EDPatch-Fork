package com.eltechs.axs.xserver.events;

import com.eltechs.axs.xserver.Window;

/**
 * BARU: sebelumnya proyek ini tidak punya satu pun kelas event Present.
 * Mengikuti pola ResizeRequest.java (Event konkret paling sederhana) SEBAGAI
 * TEMPLATE, tapi dengan satu perbedaan semantik penting: event klasik lewat
 * super(id) menyimpan KODE EVENT CORE X11 (mis. ResizeRequest=25) yang
 * langsung dipakai sebagai byte pertama di wire oleh XResponse.sendEvent().
 * Present adalah XGE (GenericEvent) - byte pertama di wire SELALU 35
 * (hardcoded di XResponse.sendGenericEvent()), sehingga id di sini dipakai
 * untuk menyimpan EVTYPE (ExtensionEventCodes.PRESENT_EVTYPE_COMPLETE_NOTIFY
 * = 1), bukan type. Wire format lengkap diverifikasi terhadap presentproto.txt
 * Appendix A.3 "PresentCompleteNotify"
 * (https://cgit.freedesktop.org/xorg/proto/presentproto/tree/presentproto.txt).
 *
 * 'window' di sini adalah window TUJUAN pengiriman event (bisa window utama
 * dari PresentPixmap ATAU salah satu entri di 'notifies' list-nya - lihat
 * PresentRequests.handlePixmap) - BUKAN selalu window yang sama dengan
 * window yang menerima presentasi.
 */
public class PresentCompleteNotify extends Event {
    private final int eventId;
    private final Window window;
    private final int kind;
    private final int mode;
    private final int serial;
    private final long ust;
    private final long msc;

    public PresentCompleteNotify(int eventId, Window window, int kind, int mode,
                                  int serial, long ust, long msc) {
        super(com.eltechs.axs.xserver.events.ExtensionEventCodes.PRESENT_EVTYPE_COMPLETE_NOTIFY);
        this.eventId = eventId;
        this.window = window;
        this.kind = kind;
        this.mode = mode;
        this.serial = serial;
        this.ust = ust;
        this.msc = msc;
    }

    public int getEventId() {
        return this.eventId;
    }

    public Window getWindow() {
        return this.window;
    }

    public int getKind() {
        return this.kind;
    }

    public int getMode() {
        return this.mode;
    }

    public int getSerial() {
        return this.serial;
    }

    public long getUst() {
        return this.ust;
    }

    public long getMsc() {
        return this.msc;
    }
}
