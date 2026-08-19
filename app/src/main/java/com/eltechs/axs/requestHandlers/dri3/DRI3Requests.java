package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * REVISI KE-5 - handleOpen() DITULIS ULANG mengikuti Winlator persis
 * (dikonfirmasi baca app/src/main/java/.../DRI3Extension.java Winlator-Ludashi-test):
 * DRI3Open TIDAK PERNAH kirim fd device - cuma reply sukses kosong. Buffer
 * sharing sepenuhnya lewat AHardwareBuffer di PixmapFromBuffer (lihat
 * Dri3BufferAllocator.importDmaBufFd yg sudah ditulis ulang total jadi
 * AHardwareBuffer_recvHandleFromUnixSocket, BUKAN dma-buf lagi).
 *
 * openRenderNodeFd() DIHAPUS TOTAL dari sini (dan dari Dri3BufferAllocator.java)
 * - sudah tidak relevan, /dev/dri tidak pernah dibutuhkan lagi dgn pendekatan ini.
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
    }

    private void handleQueryVersion(XClient client, int seq, byte minorOpcode,
                                     XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        request.readInt();
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    private void handleOpen(XClient client, int seq, byte minorOpcode,
                             XRequest request, XResponse response) throws java.io.IOException {
        request.readInt(); // drawable (diabaikan, sama spt Winlator - cuma dibaca lalu tidak dipakai)
        request.readInt(); // provider (diabaikan)

        // TIDAK ADA fd dikirim sama sekali - persis DRI3Extension.java Winlator.
        // sendReplyWithFd/writeWithFd TIDAK DIPAKAI LAGI di sini - kembali ke
        // reply biasa (sendSuccessReply dgn nol field tambahan = 32-byte
        // reply kosong, sesuai spec: DRI3Open reply memang tidak wajib fd
        // kalau server tidak menyediakan device node asli).
        response.sendSuccessReply((byte) 1);
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

        // fd ini SEKARANG socketpair fd (protokol Winlator), BUKAN dma-buf -
        // lihat Dri3BufferAllocator.importDmaBufFd yg sudah ditulis ulang.
        // Mekanisme AMBIL fd dari SCM_RIGHTS-nya SENDIRI (dequeueReceivedFd)
        // TIDAK BERUBAH - itu independen dari apa ISI/MAKNA fd-nya.
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
}
