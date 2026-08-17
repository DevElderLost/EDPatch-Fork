package com.eltechs.axs.proto.input.impl;

import com.eltechs.axs.proto.input.TrivialExtensionDispatcher;

/**
 * Pola persis DRI2ExtensionDispatcher / GLXExtensionDispatcher / XTestExtensionDispatcher
 * (lihat disassembly masing-masing constructor - semua cuma panggil
 *  super(majorOpcode, name, firstErrorId, firstEventId)).
 *
 * majorOpcode = 155 dipilih karena opcode terpakai saat ini: 140(SHM), 142(XTEST),
 * 153(DRI2), 154(GLX). GANTI kalau ternyata bentrok dengan extension lain yang
 * belum kelihatan di analisis (mis. RandR/Present kalau nanti ditambah juga).
 *
 * firstErrorId=0, firstEventId=0 karena DRI3 protocol asli (dri3proto.h upstream)
 * memang tidak mendefinisikan error maupun event sendiri - sama seperti pola XTEST.
 */
public final class DRI3ExtensionDispatcher extends TrivialExtensionDispatcher {
    public DRI3ExtensionDispatcher() {
        super((byte) 155, "DRI3", (byte) 0, (byte) 0);
    }
}
