package com.eltechs.axs.requestHandlers.present;

import com.eltechs.axs.proto.input.ConfigurableRequestsDispatcher;
import com.eltechs.axs.requestHandlers.HandlerObjectBase;
import com.eltechs.axs.requestHandlers.dri3.Dri3BufferAllocator;
import com.eltechs.axs.xserver.XServer;
import com.eltechs.axs.xserver.client.XClient;
import com.eltechs.axs.xconnectors.XRequest;
import com.eltechs.axs.xconnectors.XResponse;

/**
 * REVISI - perbaikan sama spt DRI3Requests: BadWindow(int)/BadPixmap(int)
 * butuh argumen id, bukan constructor kosong.
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
                                     XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        request.readInt();
        response.sendSuccessReply((byte) 1, Version.MAJOR, Version.MINOR);
    }

    private void handlePixmap(XClient client, int seq, byte minorOpcode,
                               XRequest request, XResponse response) throws java.io.IOException {
        int window = request.readInt();
        int pixmap = request.readInt();
        request.readInt();   // serial
        request.readInt();   // valid-area region
        request.readInt();   // update-area region
        request.readShort(); // x-off
        request.readShort(); // y-off
        request.readInt();   // target-crtc
        request.readInt();   // wait-fence
        request.readInt();   // idle-fence
        request.readInt();   // options
        request.readInt();   // pad
        request.readAsByteBuffer(8); // target-msc
        request.readAsByteBuffer(8); // divisor
        request.readAsByteBuffer(8); // remainder
        request.skipRequest();

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
    }

    private void handleNotifyMSCStub(XClient client, int seq, byte minorOpcode,
                                      XRequest request, XResponse response) {
        request.skipRequest();
    }

    private void handleSelectInputStub(XClient client, int seq, byte minorOpcode,
                                        XRequest request, XResponse response) {
        request.skipRequest();
    }

    private void handleQueryCapabilities(XClient client, int seq, byte minorOpcode,
                                          XRequest request, XResponse response) throws java.io.IOException {
        request.readInt();
        response.sendSuccessReply((byte) 1, 0);
    }
}
