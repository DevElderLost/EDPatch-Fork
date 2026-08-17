package com.eltechs.axs.requestHandlers.present;

/** Opcode Present extension - spec publik X.org presentproto.h. */
public final class Opcodes {
    private Opcodes() {}
    public static final int QueryVersion       = 0;
    public static final int Pixmap             = 1; // PresentPixmap - buffer flip, INTI fitur ini
    public static final int NotifyMSC          = 2;
    public static final int SelectInput        = 3;
    public static final int QueryCapabilities  = 4;
}
