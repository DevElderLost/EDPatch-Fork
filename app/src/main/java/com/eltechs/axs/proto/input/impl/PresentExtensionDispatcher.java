package com.eltechs.axs.proto.input.impl;

import com.eltechs.axs.proto.input.TrivialExtensionDispatcher;

/**
 * Pola persis DRI3ExtensionDispatcher. majorOpcode = 156 (lanjutan penomoran
 * dari sesi sebelumnya: 140 SHM, 142 XTEST, 153 DRI2, 154 GLX, 155 DRI3 baru).
 *
 * firstAssignedEventId TIDAK bisa 0 spt DRI3/XTest - Present protokol beneran
 * mendefinisikan event (PresentCompleteNotify dkk lewat mekanisme GenericEvent).
 * TAPI di implementasi awal ini (lihat PresentRequests.java) event notify BELUM
 * benar2 dikirim (SelectInput/NotifyMSC baru stub) - jadi utk SEKARANG angka
 * event id ini belum dipakai riil, saya isi 0 dulu sbg placeholder AMAN
 * (server tetap jalan, klien yg nunggu completion event akan nunggu tanpa
 * respons - lihat catatan risiko di PresentRequests). Ganti kalau notify
 * event sudah diimplementasi beneran.
 */
public final class PresentExtensionDispatcher extends TrivialExtensionDispatcher {
    public PresentExtensionDispatcher() {
        super((byte) 156, "Present", (byte) 0, (byte) 0);
    }
}
