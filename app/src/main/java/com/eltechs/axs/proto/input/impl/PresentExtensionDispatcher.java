package com.eltechs.axs.proto.input.impl;

import com.eltechs.axs.proto.input.TrivialExtensionDispatcher;

/**
 * Pola persis DRI3ExtensionDispatcher. majorOpcode = 156 (lanjutan penomoran
 * dari sesi sebelumnya: 140 SHM, 142 XTEST, 153 DRI2, 154 GLX, 155 DRI3 baru).
 *
 * firstAssignedEventId sebelumnya 0 sebagai placeholder aman (lihat riwayat
 * di git blame / komentar lama), karena PresentCompleteNotify/PresentIdleNotify
 * belum diimplementasikan. Sekarang PresentRequests.java benar-benar
 * mengirim kedua event itu (lihat handleSelectInput/handleNotifyMSC di sana),
 * jadi diganti 69 - angka bebas asal tidak 0 dan tidak bentrok dengan
 * FIRST_EVENT extension lain (DRI2=65, GLX=67, next available=69).
 *
 * PENTING: nilai ini HANYA memengaruhi apa yang dibalas server saat client
 * memanggil QueryExtension (byte first-event di reply) - dikonfirmasi lewat
 * grep ke seluruh codebase, getFirstAssignedEventId() tidak dipanggil di
 * jalur lain manapun. TIDAK memengaruhi wire format event Present itu
 * sendiri - event Present adalah XGE (GenericEvent), type selalu 35 tetap,
 * dan evtype (0/1/2) ditulis langsung di field evtype milik xGenericEvent,
 * bukan dihitung dari firstAssignedEventId + offset seperti event klasik
 * DRI2/GLX. Lihat ExtensionEventCodes.PRESENT_EVTYPE_* dan
 * XResponse.sendGenericEvent() untuk detail wire format XGE.
 */
public final class PresentExtensionDispatcher extends TrivialExtensionDispatcher {
    public PresentExtensionDispatcher() {
        super((byte) 156, "Present", (byte) 69, (byte) 0);
    }
}
