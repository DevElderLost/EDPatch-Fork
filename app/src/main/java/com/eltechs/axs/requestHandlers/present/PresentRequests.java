package com.eltechs.axs.requestHandlers.present;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.requestHandlers.dri3.Dri3BufferAllocator;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * IMPLEMENTASI MINIMAL - cukup utk PresentPixmap (flip buffer) jalan, supaya
 * Vulkan WSI X11 (wsi_common_x11.c di Mesa) berhasil bikin surface & present
 * sekali. INI BUKAN implementasi Present penuh:
 *
 * YANG SUDAH: QueryVersion, Pixmap (flip - langsung tampilkan, sinkron,
 *   TANPA hormati target-msc/divisor/remainder/wait-fence dari request -
 *   semua di-skip/diabaikan, present terjadi SEGERA saat request diterima).
 *
 * YANG BELUM (stub, cuma acknowledge tanpa efek nyata):
 *   - NotifyMSC: return sukses tapi TIDAK benar2 jadwalkan notify di MSC target
 *   - SelectInput: return sukses tapi event PresentCompleteNotify/IdleNotify
 *     TIDAK PERNAH benar2 dikirim ke client (perlu XResponse.sendGenericEvent()
 *     variable-length BARU - method sendEvent() yg ada di XResponse HARDCODE
 *     32 byte, sementara PresentCompleteNotify butuh >32 byte. Ini pekerjaan
 *     terpisah, belum dikerjakan).
 *
 * RISIKO NYATA dari stub SelectInput/NotifyMSC: Mesa/DXVK yg nunggu
 * PresentCompleteNotify utk vsync pacing / frame-pacing BISA HANG atau
 * fallback ke behavior tak terduga kalau event itu gak pernah datang.
 * Sebagian implementasi WSI toleran (anggap present langsung selesai kalau
 * gak ada notify dalam waktu tertentu), sebagian TIDAK. INI HARUS DITEST
 * LANGSUNG - gak bisa dipastikan dari analisis statis doang.
 */
public final class PresentRequests extends HandlerObjectBase {

    private final Dri3BufferAllocator bufferAllocator;

    public PresentRequests(XServer xServer, Dri3BufferAllocator bufferAllocator) {
        super(xServer);
        this.bufferAllocator = bufferAllocator;
    }

    public void installInto(ConfigurableRequestsDispatcher dispatcher) {
        dispatcher.installRequestHandler(Opcodes.QueryVersion, this::handleQueryVersion);
        dispatcher.installRequestHandler(Opcodes.Pixmap, this::handlePixmap);
        dispatcher.installRequestHandler(Opcodes.NotifyMSC, this::handleNotifyMSCStub);
        dispatcher.installRequestHandler(Opcodes.SelectInput, this::handleSelectInputStub);
        dispatcher.installRequestHandler(Opcodes.QueryCapabilities, this::handleQueryCapabilities);
    }

    private void handleQueryVersion(XClient client, int seq, byte minorOpcode,
                                     XRequest request, XResponse response) {
        request.readInt(); // client major
        request.readInt(); // client minor
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    // ---- PresentPixmap ----
    // Field lengkap per spec (presentproto.h) jauh lebih banyak dari yg diparse
    // di sini - saya cuma ambil yg PERLU utk flip dasar, sisanya di-skip via
    // request.skip(). VERIFIKASI urutan field ini ke xpresentproto/Mesa loader
    // versi Anda sebelum ship - saya tulis dari spec publik, bukan hasil RE.
    private void handlePixmap(XClient client, int seq, byte minorOpcode,
                               XRequest request, XResponse response) {
        int window = request.readInt();
        int pixmap = request.readInt();
        request.readInt();   // serial
        request.readInt();   // valid-area region (XID, 0 = None biasanya)
        request.readInt();   // update-area region
        request.readShort(); // x-off
        request.readShort(); // y-off
        request.readInt();   // target-crtc
        request.readInt();   // wait-fence (DIABAIKAN - lihat catatan kelas)
        request.readInt();   // idle-fence (DIABAIKAN)
        request.readInt();   // options bitmask
        request.readInt();   // pad
        request.readAsByteBuffer(8); // target-msc (CARD64) - diabaikan, present segera
        request.readAsByteBuffer(8); // divisor (CARD64) - diabaikan
        request.readAsByteBuffer(8); // remainder (CARD64) - diabaikan
        request.skipRequest(); // sisa: notifies list (PRESENT_NOTIFY[]) - diabaikan

        // Void request (tidak ada reply). SUDAH TERVERIFIKASI lewat dex:
        // WindowsManager.getWindow(id), PixmapsManager.getPixmap(id) ->
        // Pixmap.getBackingStore() (Drawable), Window.getFrontBuffer() (Drawable
        // lama), Window.replaceBackingStores(old, new) - inilah mekanisme window
        // "redirect content" yg sudah ada, PERSIS yg dibutuhkan Present utk flip.
        com.eltechs.axs.xserver.Window win = getXServer().getWindowsManager().getWindow(window);
        com.eltechs.axs.xserver.Pixmap pix = getXServer().getPixmapsManager().getPixmap(pixmap);
        if (win == null || pix == null) {
            // PresentPixmap void request, X11 core protocol tetap izinkan kirim
            // error BadWindow/BadPixmap walau requestnya sendiri gak punya reply.
            response.sendError(win == null
                    ? new com.eltechs.axs.proto.input.errors.BadWindow()
                    : new com.eltechs.axs.proto.input.errors.BadPixmap());
            return;
        }
        com.eltechs.axs.xserver.Drawable oldFront = win.getFrontBuffer();
        com.eltechs.axs.xserver.Drawable newFront = pix.getBackingStore();
        win.replaceBackingStores(oldFront, newFront);
        // replaceBackingStores() KEMUNGKINAN BESAR sudah trigger
        // WindowContentModificationListenersList secara internal (AXSRendererGL
        // kemungkinan subscribe ke situ utk tau kapan re-composite) - TAPI ini
        // asumsi, belum saya verifikasi bytecode replaceBackingStores() itu
        // sendiri. Kalau ternyata TIDAK auto-notify, compositor gak akan
        // pernah tau window ini berubah & layar gak update - perlu telusur
        // WindowImpl.replaceBackingStores() body kalau presentasi ternyata
        // "keterima" tapi gak pernah kelihatan di layar.
    }

    private void handleNotifyMSCStub(XClient client, int seq, byte minorOpcode,
                                      XRequest request, XResponse response) {
        request.skipRequest();
        // void request, no-op selain consume bytes - lihat catatan risiko di kelas.
    }

    private void handleSelectInputStub(XClient client, int seq, byte minorOpcode,
                                        XRequest request, XResponse response) {
        request.skipRequest();
        // void request, TIDAK benar2 register listener - event gak akan pernah
        // dikirim. Lihat catatan risiko di kelas.
    }

    private void handleQueryCapabilities(XClient client, int seq, byte minorOpcode,
                                          XRequest request, XResponse response) {
        request.readInt(); // target (window/pixmap XID)
        // capabilities = 0 (PRESENT_CAPABILITY_NONE) - klaim TIDAK support async/
        // fence/UST-timestamp dsb, biar klien pakai jalur paling sederhana
        // (sinkron) yg cocok dgn implementasi minimal kita.
        response.sendSuccessReply((byte) 1, 0);
    }
}
