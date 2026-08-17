package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.proto.input.OpcodeHandler;
import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * REVISI KE-3 - perbaiki bug API: client.getOutputStream()/request.getInputStream()
 * yg dipakai di revisi ke-2 TIDAK ADA di XClient/XRequest asli (sudah dicek ulang
 * dump lengkap kedua kelas itu - XClient cuma expose outputStream lewat constructor,
 * XRequest cuma expose inputStream sbg private field, keduanya TANPA getter publik).
 *
 * Perbaikan: sendReplyWithFd() ditaruh SEBAGAI METHOD BARU DI DALAM XResponse itu
 * sendiri (bisa akses this.outputStream langsung, sama-sama private tapi 1 kelas).
 * dequeueReceivedFd() ditaruh SEBAGAI METHOD BARU DI DALAM XRequest itu sendiri
 * (akses this.inputStream langsung). Lihat XResponse.patch.txt & XRequest.patch.txt
 * (baru) utk definisi kedua method itu.
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
                                     XRequest request, XResponse response) {
        request.readInt();
        request.readInt();
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    private void handleOpen(XClient client, int seq, byte minorOpcode,
                             XRequest request, XResponse response) {
        request.readInt(); // drawable
        request.readInt(); // provider (diabaikan - Android single-GPU)

        int deviceFd = bufferAllocator.openRenderNodeFd();
        if (deviceFd < 0) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadAlloc());
            return;
        }
        // response.sendReplyWithFd() internal sudah handle lock+flush+writeWithFd,
        // gak perlu try-with-resources manual lagi di sini (beda dari revisi ke-2).
        response.sendReplyWithFd((byte) 1, seq, deviceFd, null);
    }

    private void handlePixmapFromBuffer(XClient client, int seq, byte minorOpcode,
                                         XRequest request, XResponse response) {
        int newPixmapId = request.readInt();
        int drawable = request.readInt();
        request.readInt(); // size
        int width = request.readShort() & 0xFFFF;
        int height = request.readShort() & 0xFFFF;
        int stride = request.readShort() & 0xFFFF;
        int depth = request.readByte() & 0xFF;
        int bpp = request.readByte() & 0xFF;

        int bufferFd = request.dequeueReceivedFd(); // method baru di XRequest, lihat patch
        if (bufferFd < 0) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadAlloc());
            return;
        }
        bufferAllocator.createPixmapFromFd(getXServer(), newPixmapId, bufferFd,
                width, height, stride, depth, bpp);
    }

    private void handleBufferFromPixmap(XClient client, int seq, byte minorOpcode,
                                         XRequest request, XResponse response) {
        int pixmap = request.readInt();
        Dri3BufferAllocator.BufferInfo info = bufferAllocator.exportPixmapAsFd(pixmap);
        if (info == null) {
            response.sendError(new com.eltechs.axs.proto.input.errors.BadPixmap());
            return;
        }
        response.sendReplyWithFd((byte) 1, seq, info.fd, info);
    }
}
