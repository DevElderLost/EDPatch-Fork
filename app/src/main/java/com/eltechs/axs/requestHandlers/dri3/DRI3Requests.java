package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * REVISI KE-6 - REVERT ke dma-buf (dari REVISI KE-5 yang sempat pakai
 * pendekatan Winlator/AHardwareBuffer). Alasan revert: paket Turnip-Zink
 * Anda dikonfirmasi Mesa VANILLA (bukan fork Winlator), dan arsitektur UBT
 * ExaGear tidak punya mekanisme wrapping/thunking native library spt Box64,
 * jadi AHardwareBuffer (fungsi Bionic) tidak pernah bisa dipanggil dari
 * Turnip x86 murni yang jalan di guest. handleOpen() KEMBALI kirim fd asli
 * via SCM_RIGHTS (sendReplyWithFd) - Mesa vanilla expect xcb_dri3_open_reply_fds()
 * dpt hasil beneran (dikonfirmasi baca strings libvulkan_freedreno.so Anda).
 */
public final class DRI3Requests extends HandlerObjectBase {

    private final Dri3BufferAllocator bufferAllocator;

    public DRI3Requests(XServer xServer, Dri3BufferAllocator bufferAllocator) {
        super(xServer);
        this.bufferAllocator = bufferAllocator;
    }

    public void installInto(ConfigurableRequestsDispatcher dispatcher) {
        dispatcher.installRequestHandler(Opcodes.QueryVersion, this::handleQueryVersion);
        dispatcher.installRequestHandler(Opcodes.Open, this::handleOpen);
        dispatcher.installRequestHandler(Opcodes.PixmapFromBuffer, this::handlePixmapFromBuffer);
        dispatcher.installRequestHandler(Opcodes.BufferFromPixmap, this::handleBufferFromPixmap);
        // Ditambahkan: opcode 4/5 sudah punya konstanta di Opcodes.java sejak
        // awal tapi tidak pernah didaftarkan ke dispatcher - padanan persis
        // dari patch yang sudah diterapkan ke DRI3Requests.smali (eltechs.zip)
        // minggu lalu. Server ini tidak punya infrastruktur explicit-sync
        // fence sama sekali (tidak ada pemetaan ke sync_file/dma-fence di
        // Dri3BufferAllocator manapun), jadi bukan implementasi fence
        // sungguhan - ini membalas BadImplementation yang benar secara
        // protokol X11, supaya client fence-aware (Mesa modern) mendapat
        // jawaban jelas dan bisa fallback dengan baik, alih-alih server
        // diam / client salah baca sisa byte request berikutnya.
        dispatcher.installRequestHandler(Opcodes.FenceFromFD, this::handleFenceFromFDUnsupported);
        dispatcher.installRequestHandler(Opcodes.FDFromFence, this::handleFDFromFenceUnsupported);
    }

    private void handleQueryVersion(XClient client, int seq, byte minorOpcode,
                                     XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        request.readInt();
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    private void handleOpen(XClient client, int seq, byte minorOpcode,
                             XRequest request, XResponse response) throws java.io.IOException {
        int drawable = request.readInt();
        request.readInt(); // provider (diabaikan - Android single-GPU)

        int deviceFd = bufferAllocator.openRenderNodeFd();
        if (deviceFd < 0) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadAlloc(drawable));
            return;
        }
        response.sendReplyWithFd((byte) 1, seq, deviceFd, null);
    }

    private void handlePixmapFromBuffer(XClient client, int seq, byte minorOpcode,
                                         XRequest request, XResponse response) throws java.io.IOException {
        int newPixmapId = request.readInt();
        int drawable = request.readInt();
        request.readInt(); // size
        int width = request.readShort() & 0xFFFF;
        int height = request.readShort() & 0xFFFF;
        int stride = request.readShort() & 0xFFFF;
        int depth = request.readByte() & 0xFF;
        int bpp = request.readByte() & 0xFF;

        int bufferFd = request.dequeueReceivedFd();
        if (bufferFd < 0) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadAlloc(newPixmapId));
            return;
        }
        bufferAllocator.createPixmapFromFd(getXServer(), newPixmapId, bufferFd,
                width, height, stride, depth, bpp);
    }

    private void handleBufferFromPixmap(XClient client, int seq, byte minorOpcode,
                                         XRequest request, XResponse response) throws java.io.IOException {
        int pixmap = request.readInt();
        Dri3BufferAllocator.BufferInfo info = bufferAllocator.exportPixmapAsFd(pixmap);
        if (info == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadPixmap(pixmap));
            return;
        }
        response.sendReplyWithFd((byte) 1, seq, info.fd, info);
    }

    private void handleFenceFromFDUnsupported(XClient client, int seq, byte minorOpcode,
                                               XRequest request, XResponse response) throws java.io.IOException {
        request.skipRequest();
        response.sendError(new com.eltechs.axs.proto.input.errors.BadImplementation());
    }

    private void handleFDFromFenceUnsupported(XClient client, int seq, byte minorOpcode,
                                               XRequest request, XResponse response) throws java.io.IOException {
        request.skipRequest();
        response.sendError(new com.eltechs.axs.proto.input.errors.BadImplementation());
    }
}
