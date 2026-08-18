package com.eltechs.axs.proto.input.errors;

import com.eltechs.axs.proto.input.XProtocolError;

/**
 * TIDAK ADA di project asli - baru dibuat, meniru persis pola BadPixmap/
 * BadWindow/BadDrawable (constructor sama: format string, kode error dari
 * CoreErrorCodes.ALLOC=0xb=11, panggil super(code, data, message)).
 *
 * ISI methodnya PERSIS diterjemahkan dari BadAlloc.smali yang sudah saya
 * tulis sebelumnya (folder smali_real_patches) - logika sama, cuma format
 * source Java biasa supaya javac (bukan smali assembler) yang compile.
 */
public final class BadAlloc extends XProtocolError {

    private static final byte CODE = 0xb; // CoreErrorCodes.ALLOC

    public BadAlloc(int data) {
        super(CODE, data, String.format("Insufficient resources to allocate id/buffer %d.", data));
    }

    public int getId() {
        return getErrorCode();
    }
}
