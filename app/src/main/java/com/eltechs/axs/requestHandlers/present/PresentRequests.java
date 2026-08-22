package com.eltechs.axs.requestHandlers.present;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.requestHandlers.dri3.Dri3BufferAllocator;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;
import com.eltechs.axs.xserver.events.ExtensionEventCodes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REVISI - perbaikan sama spt DRI3Requests: BadWindow(int)/BadPixmap(int)
 * butuh argumen id, bukan constructor kosong.
 *
 * BARU: handleSelectInput/handleNotifyMSC sebelumnya stub kosong
 * (cuma skipRequest()). Sekarang benar-benar mengirim PresentCompleteNotify
 * lewat XEventSender.sendEvent(Event) - jalur yang sama dipakai semua
 * event lain di proyek ini (lihat XEventSender.java, yang sudah
 * ditambahkan entry EventWriter utk PresentCompleteNotify yang memanggil
 * XResponse.sendGenericEvent() - infrastruktur XGE baru). Wire format
 * setiap event diverifikasi byte-per-byte terhadap spesifikasi resmi
 * presentproto.txt Appendix A "Protocol Encoding"
 * (https://cgit.freedesktop.org/xorg/proto/presentproto/tree/presentproto.txt).
 *
 * KETERBATASAN YANG DISENGAJA (baca sebelum menganggap ini Present lengkap):
 * - Tidak ada infrastruktur vblank/timer di proyek ini. 'target_msc',
 *   'divisor', 'remainder' pada PresentPixmap dan PresentNotifyMSC DIBACA
 *   dari wire (supaya parsing request tetap benar) tapi TIDAK DIPAKAI untuk
 *   menjadwalkan apa pun - completion dikirim SEGERA setelah operasi
 *   selesai secara sinkron, bukan disinkronkan ke vertical blank yang
 *   sesungguhnya. Ini artinya perilaku "presentasi tepat di frame X"
 *   yang dijanjikan spek TIDAK terpenuhi - klien akan menerima completion
 *   lebih cepat dari yang seharusnya, bukan lebih lambat, jadi ini aman
 *   dari sisi "klien menunggu selamanya" tapi tidak akurat secara timing.
 * - 'msc' (media stream counter / frame count) TIDAK dihitung dari vblank
 *   counter GPU sungguhan (tidak ada aksesnya di proyek ini) - dipakai
 *   counter increment sederhana per window, jadi nilainya monoton naik
 *   tapi TIDAK berkorelasi dengan refresh rate layar sungguhan.
 * - 'ust' dipakai System.nanoTime() (Java monotonic clock) - ini cocok
 *   secara semantik dengan definisi resmi "unadjusted system time, pada
 *   Linux adalah CLOCK_MONOTONIC", bukan sekadar tebakan.
 * - PresentConfigureNotify (window resize) TIDAK diimplementasikan di sini -
 *   di luar cakupan perbaikan DRI3/Present kali ini.
 * - PresentOptionAsync/Copy/UST (field 'options' pada PresentPixmap) DIBACA
 *   tapi diabaikan isinya - semua presentasi diperlakukan sebagai
 *   PresentCompleteModeCopy (mode paling sederhana dan paling aman: pixmap
 *   sumber selalu idle segera setelah operasi, konsisten dengan
 *   implementasi handlePixmap yang sudah ada yaitu copy sinkron via
 *   replaceBackingStores(), bukan flip).
 */
public final class PresentRequests extends HandlerObjectBase {

    private final Dri3BufferAllocator bufferAllocator;

    // BARU: bookkeeping listener PresentSelectInput per window. Disimpan
    // di sini (bukan di interface Window - lihat catatan arsitektur) karena
    // Window adalah interface dengan kemungkinan banyak implementasi
    // konkret; menambah state Present ke sana berisiko regresi luas di
    // luar cakupan perbaikan ini. Struktur: windowId -> daftar listener
    // yang PresentSelectInput untuk window itu.
    private final Map<Integer, List<PresentListener>> listenersByWindow = new HashMap<>();

    // BARU: pengganti sederhana utk 'msc' (frame counter) karena tidak ada
    // akses vblank counter GPU di proyek ini - lihat catatan keterbatasan
    // di Javadoc kelas. Satu counter per window, monoton naik.
    private final Map<Integer, AtomicLong> mscCounterByWindow = new HashMap<>();

    private static final class PresentListener {
        final XClient client;
        final int eventId;
        int eventMask;

        PresentListener(XClient client, int eventId, int eventMask) {
            this.client = client;
            this.eventId = eventId;
            this.eventMask = eventMask;
        }
    }

    public PresentRequests(XServer xServer, Dri3BufferAllocator bufferAllocator) {
        super(xServer);
        this.bufferAllocator = bufferAllocator;
    }

    public void installInto(ConfigurableRequestsDispatcher dispatcher) {
        dispatcher.installRequestHandler(Opcodes.QueryVersion, this::handleQueryVersion);
        dispatcher.installRequestHandler(Opcodes.Pixmap, this::handlePixmap);
        dispatcher.installRequestHandler(Opcodes.NotifyMSC, this::handleNotifyMSC);
        dispatcher.installRequestHandler(Opcodes.SelectInput, this::handleSelectInput);
        dispatcher.installRequestHandler(Opcodes.QueryCapabilities, this::handleQueryCapabilities);
    }

    private void handleQueryVersion(XClient client, int seq, byte minorOpcode,
                                     XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        request.readInt();
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    private void handlePixmap(XClient client, int seq, byte minorOpcode,
                               XRequest request, XResponse response) throws java.io.IOException {
        int window = request.readInt();
        int pixmap = request.readInt();
        int serial = request.readInt();   // serial - BARU: sekarang dipakai (dulu diabaikan), diteruskan ke PresentCompleteNotify
        request.readInt();   // valid-area region
        request.readInt();   // update-area region
        request.readShort(); // x-off
        request.readShort(); // y-off
        request.readInt();   // target-crtc
        request.readInt();   // wait-fence
        request.readInt();   // idle-fence
        request.readInt();   // options - dibaca tapi diabaikan, lihat catatan keterbatasan di Javadoc kelas
        request.readInt();   // pad
        request.readAsByteBuffer(8); // target-msc - dibaca tapi diabaikan (tidak ada infrastruktur vblank)
        request.readAsByteBuffer(8); // divisor
        request.readAsByteBuffer(8); // remainder

        // BARU: sebelumnya sisa request (LISTofPRESENTNOTIFY) langsung
        // dibuang oleh skipRequest() tanpa dibaca. Sekarang di-parse
        // supaya window/serial tambahan di daftar 'notifies' juga
        // menerima PresentCompleteNotify sesuai spek ("delivered both to
        // the PresentPixmap window/serial parameter as well as each of
        // the entries in the list of PRESENTNOTIFY parameter").
        // getRemainingBytesCount() di titik ini = persis n*8 byte (n
        // entri xPresentNotify @ 8 byte/entri: window CARD32 + serial
        // CARD32) - dibuktikan seluruh 68 byte field tetap xPresentPixmapReq
        // sudah terbaca lengkap oleh baris-baris di atas.
        int notifyListBytes = request.getRemainingBytesCount();
        List<int[]> extraNotifies = new ArrayList<>(); // masing2 elemen: {window, serial}
        if (notifyListBytes % 8 == 0) {
            int count = notifyListBytes / 8;
            for (int i = 0; i < count; i++) {
                int notifyWindow = request.readInt();
                int notifySerial = request.readInt();
                extraNotifies.add(new int[]{notifyWindow, notifySerial});
            }
        } else {
            // Ukuran tidak kelipatan 8 - request malformed atau asumsi
            // parsing di atas keliru untuk kasus ini. Jangan coba baca
            // parsial (bisa desync stream byte berikutnya); buang sisanya
            // dengan aman lewat skipRequest() seperti perilaku lama.
            request.skipRequest();
        }
        request.skipRequest(); // no-op kalau sudah pas habis, aman dipanggil dua kali (nBytesRemaining sudah 0)

        com.eltechs.axs.xserver.Window win = getXServer().getWindowsManager().getWindow(window);
        com.eltechs.axs.xserver.Pixmap pix = getXServer().getPixmapsManager().getPixmap(pixmap);
        if (win == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadWindow(window));
            return;
        }
        if (pix == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadPixmap(pixmap));
            return;
        }
        com.eltechs.axs.xserver.Drawable oldFront = win.getFrontBuffer();
        com.eltechs.axs.xserver.Drawable newFront = pix.getBackingStore();
        win.replaceBackingStores(oldFront, newFront);

        // BARU: kirim PresentCompleteNotify setelah swap berhasil - lihat
        // catatan keterbatasan timing (segera, bukan tersinkron vblank)
        // di Javadoc kelas. kind=PresentCompleteKindPixmap(0),
        // mode=PresentCompleteModeCopy(0).
        long ust = System.nanoTime();
        long msc = nextMscFor(window);
        sendCompleteNotifyToWindow(win, /*kind=*/0, /*mode=*/0, serial, ust, msc);
        for (int[] notify : extraNotifies) {
            com.eltechs.axs.xserver.Window notifyWin = getXServer().getWindowsManager().getWindow(notify[0]);
            if (notifyWin != null) {
                sendCompleteNotifyToWindow(notifyWin, /*kind=*/0, /*mode=*/0, notify[1], ust, msc);
            }
            // notifyWin == null: window id di entri 'notifies' tidak valid.
            // Diabaikan diam-diam - ini daftar tambahan opsional, bukan
            // window utama presentasi (yang sudah berhasil swap di atas),
            // jadi tidak wajar membatalkan seluruh operasi karena satu
            // entri tambahan tidak valid.
        }
    }

    private void handleNotifyMSC(XClient client, int seq, byte minorOpcode,
                                  XRequest request, XResponse response) throws IOException {
        int window = request.readInt();
        int serial = request.readInt();
        request.readInt();          // pad0
        request.readAsByteBuffer(8); // target-msc - dibaca tapi diabaikan, lihat catatan keterbatasan
        request.readAsByteBuffer(8); // divisor
        request.readAsByteBuffer(8); // remainder
        request.skipRequest();

        com.eltechs.axs.xserver.Window win = getXServer().getWindowsManager().getWindow(window);
        if (win == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadWindow(window));
            return;
        }

        // kind=PresentCompleteKindMSCNotify(1), mode diabaikan spek untuk
        // kind ini (dikirim 0/PresentCompleteModeCopy demi konsistensi).
        long ust = System.nanoTime();
        long msc = nextMscFor(window);
        sendCompleteNotifyToWindow(win, /*kind=*/1, /*mode=*/0, serial, ust, msc);
    }

    private void handleSelectInput(XClient client, int seq, byte minorOpcode,
                                    XRequest request, XResponse response) throws IOException {
        int eid = request.readInt();
        int window = request.readInt();
        int eventMask = request.readInt();
        request.skipRequest();

        com.eltechs.axs.xserver.Window win = getXServer().getWindowsManager().getWindow(window);
        if (win == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadWindow(window));
            return;
        }

        List<PresentListener> listeners = listenersByWindow.get(window);
        PresentListener existing = null;
        if (listeners != null) {
            for (PresentListener l : listeners) {
                if (l.eventId == eid && l.client == client) {
                    existing = l;
                    break;
                }
            }
        }

        if (eventMask == 0) {
            // Spek: "if eventMask is empty, PresentSelectInput deletes the
            // specified context"
            if (existing != null) {
                listeners.remove(existing);
            }
            return;
        }

        if (existing != null) {
            existing.eventMask = eventMask;
        } else {
            if (listeners == null) {
                listeners = new ArrayList<>();
                listenersByWindow.put(window, listeners);
            }
            listeners.add(new PresentListener(client, eid, eventMask));
        }
    }

    private void handleQueryCapabilities(XClient client, int seq, byte minorOpcode,
                                          XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        response.sendSuccessReply((byte) 1, 0);
    }

    private long nextMscFor(int window) {
        AtomicLong counter = mscCounterByWindow.get(window);
        if (counter == null) {
            counter = new AtomicLong(0);
            mscCounterByWindow.put(window, counter);
        }
        return counter.incrementAndGet();
    }

    /**
     * Mengirim PresentCompleteNotify ke setiap listener yang
     * PresentSelectInput dengan PRESENT_EVENTMASK_COMPLETE_NOTIFY untuk
     * window ini, lewat XEventSender.sendEvent(Event) - jalur yang sama
     * dipakai semua event lain di proyek ini (Expose, ConfigureNotify,
     * dst - lihat XEventSender.java). PresentCompleteNotify sendiri
     * ditulis ke wire lewat XResponse.sendGenericEvent() (dipanggil dari
     * dalam EventWriter yang didaftarkan di XEventSender static block -
     * bukan dipanggil manual di sini), jadi method ini TIDAK menyentuh
     * XResponse/wire format sama sekali - murni bookkeeping listener +
     * konstruksi objek Event.
     *
     * KETERBATASAN YANG DISENGAJA: XEventSender.sendEvent(Event) tidak
     * melempar ulang IOException (ditelan lewat e.printStackTrace() -
     * lihat XEventSender.java baris ~326), jadi TIDAK ADA cara bagi kode
     * di sini untuk tahu kapan pengiriman ke satu client gagal (mis.
     * karena client sudah disconnect). Akibatnya: listener untuk client
     * yang sudah mati TIDAK dibersihkan otomatis di sini - akan tetap
     * di listenersByWindow sampai window itu sendiri dihapus (di luar
     * cakupan perbaikan ini) atau PresentSelectInput dgn eventMask=0
     * dipanggil eksplisit oleh client (yang tidak akan terjadi kalau
     * client sudah mati). Ini technical debt yang nyata - dampaknya
     * memory leak kecil per client yang disconnect tanpa
     * PresentSelectInput(eventMask=0) dulu, BUKAN crash atau salah
     * kirim (percobaan kirim ke client mati cuma menghasilkan
     * printStackTrace() yang dibuang XEventSender, tidak melempar apa
     * pun ke pemanggil). Memperbaikinya dengan benar butuh mengubah
     * XEventSender.sendEvent() supaya melempar ulang IOException, yang
     * berdampak ke SEMUA pemanggil event lain di seluruh proyek - di
     * luar cakupan perbaikan DRI3/Present kali ini.
     */
    private void sendCompleteNotifyToWindow(com.eltechs.axs.xserver.Window win, final int kind, final int mode,
                                             final int serial, final long ust, final long msc) {
        int windowId = win.getId();
        List<PresentListener> listeners = listenersByWindow.get(windowId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (PresentListener listener : listeners) {
            if ((listener.eventMask & ExtensionEventCodes.PRESENT_EVENTMASK_COMPLETE_NOTIFY) == 0) {
                continue;
            }
            listener.client.createEventSender().sendEvent(
                    new com.eltechs.axs.xserver.events.PresentCompleteNotify(
                            listener.eventId, win, kind, mode, serial, ust, msc));
        }
    }
}
