package com.eltechs.axs.requestHandlers.dri3;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * REVISI KE-4 - perbaikan dari build error nyata (GitHub Actions log, 17 Agt):
 * 1. BadAlloc TIDAK ADA di codebase asli - dibuat baru (lihat
 *    proto/input/errors/BadAlloc.smali), pola persis BadPixmap/BadWindow.
 * 2. BadPixmap(int)/BadWindow(int) BUTUH argumen id resource yg bermasalah,
 *    bukan constructor kosong - sebelumnya salah tulis new BadPixmap().
 * 3. createPixmapFromFd sekarang match persis signature Dri3BufferAllocator
 *    yg SUDAH DIPERBAIKI (nambah XServer sbg parameter pertama - lihat
 *    Dri3BufferAllocator.java revisi terbaru).
 *
 * CATATAN BUILD ORDER: sendReplyWithFd()/dequeueReceivedFd() di sini HANYA
 * akan ketemu simbolnya kalau XResponse.smali & XRequest.smali SUDAH
 * dipatch+rebuild LEBIH DULU sebelum compile file ini. Kalau masih error
 * "cannot find symbol" utk 2 method itu setelah fix2 lain di atas, artinya
 * urutan build salah - patch XResponse/XRequest dulu, baru compile file ini.
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
        int drawable = request.readInt();
        request.readInt(); // provider (diabaikan - Android single-GPU)

        int deviceFd = bufferAllocator.openRenderNodeFd();
        if (deviceFd < 0) {
            // BadAlloc(int): tidak ada "resource id" yg benar2 pas di sini
            // (kegagalan alokasi fd, bukan soal resource X11 tertentu) - pakai
            // `drawable` sbg data diagnostik (setidaknya nunjuk drawable mana
            // yg lagi diminta saat gagal), bukan makna protokol yg baku.
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
}
