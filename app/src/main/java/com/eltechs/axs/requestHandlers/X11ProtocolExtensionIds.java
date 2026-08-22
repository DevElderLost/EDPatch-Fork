package com.eltechs.axs.requestHandlers;

/* loaded from: classes.dex */
public class X11ProtocolExtensionIds {
    public static final int BIGREQ = 143;
    public static final int DRI2 = 153;
    public static final int GLX = 154;
    public static final int MITSHM = 140;
    public static final int XTEST = 142;

    // Ditambahkan: sebelumnya DRI3ExtensionDispatcher dan
    // PresentExtensionDispatcher sudah pakai 155/156 sebagai majorOpcode
    // hardcoded di constructor masing-masing, tapi RootXRequestHandlerConfigurer
    // tidak pernah punya konstanta untuk memanggil installExtensionHandler()
    // dengan angka yang sama. Nilai di sini WAJIB identik dengan yang
    // di-hardcode di kedua file *ExtensionDispatcher.java tsb.
    public static final int DRI3 = 155;
    public static final int PRESENT = 156;

    private X11ProtocolExtensionIds() {
    }
}
