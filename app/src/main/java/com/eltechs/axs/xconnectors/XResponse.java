package com.eltechs.axs.xconnectors;

import com.eltechs.axs.helpers.Assert;
import com.eltechs.axs.proto.input.XProtocolError;
import com.eltechs.axs.proto.input.impl.ProtoHelpers;
import com.eltechs.axs.proto.output.PODWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

/* loaded from: classes.dex */
public class XResponse {
    private static final byte RESP_CODE_ERROR = 0;
    private static final byte RESP_CODE_SUCCESS = 1;
    private static final int SIMPLE_EVENT_LENGTH = 28;
    private static final int SIMPLE_REPLY_LENGTH = 24;
    private static final byte[] zero = new byte[32];
    private final XRequest inResponseTo;
    private final XOutputStream outputStream;
    private final int requestSequenceNumber;

    /* loaded from: classes.dex */
    public interface ResponseDataWriter extends BufferFiller {
    }

    public XResponse(XRequest xRequest, XOutputStream xOutputStream) {
        this.inResponseTo = xRequest;
        this.requestSequenceNumber = xRequest.getSequenceNumber();
        this.outputStream = xOutputStream;
    }

    public XResponse(int i, XOutputStream xOutputStream) {
        this.inResponseTo = null;
        this.requestSequenceNumber = i;
        this.outputStream = xOutputStream;
    }

    public void sendSimpleSuccessReply(byte b, final ResponseDataWriter responseDataWriter) throws IOException {
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte((byte) 1);
            this.outputStream.writeByte(b);
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.writeInt(0);
            this.outputStream.write(24, new BufferFiller() { // from class: com.eltechs.axs.xconnectors.XResponse.1

                @Override // com.eltechs.axs.xconnectors.BufferFiller
                public void write(ByteBuffer byteBuffer) {
                    responseDataWriter.write(byteBuffer);
                    byteBuffer.put(XResponse.zero, 0, byteBuffer.remaining());
                }
            });
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }

    public void sendSuccessReplyWithPayload(byte b, final ResponseDataWriter responseDataWriter, int i, ResponseDataWriter responseDataWriter2) throws IOException {
        int roundUpLength4 = ProtoHelpers.roundUpLength4(i);
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte((byte) 1);
            this.outputStream.writeByte(b);
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.writeInt(ProtoHelpers.calculateLengthInWords(roundUpLength4));
            this.outputStream.write(24, new BufferFiller() { // from class: com.eltechs.axs.xconnectors.XResponse.2
                @Override // com.eltechs.axs.xconnectors.BufferFiller
                public void write(ByteBuffer byteBuffer) {
                    if (responseDataWriter != null) {
                        responseDataWriter.write(byteBuffer);
                    }
                    byteBuffer.put(XResponse.zero, 0, byteBuffer.remaining());
                }
            });
            this.outputStream.write(i, responseDataWriter2);
            int calculatePad = ProtoHelpers.calculatePad(i);
            if (calculatePad != 0) {
                this.outputStream.write(zero, 0, calculatePad);
            }
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }

    public void sendEvent(byte b, byte b2, final ResponseDataWriter responseDataWriter) throws IOException {
        Assert.isTrue(b != 1, "Event codes must be other than RESP_CODE_SUCCESS and RESP_CODE_ERROR.");
        Assert.isTrue(b != 0, "Event codes must be other than RESP_CODE_SUCCESS and RESP_CODE_ERROR.");
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte(b);
            this.outputStream.writeByte(b2);
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.write(28, new BufferFiller() { // from class: com.eltechs.axs.xconnectors.XResponse.3
                @Override // com.eltechs.axs.xconnectors.BufferFiller
                public void write(ByteBuffer byteBuffer) {
                    responseDataWriter.write(byteBuffer);
                    byteBuffer.put(XResponse.zero, 0, byteBuffer.remaining());
                }
            });
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }

    private void sendReply(boolean z, byte b, byte b2, int i, Object... objArr) throws IOException {
        int i2 = 24;
        if (z) {
            int onWireLength = PODWriter.getOnWireLength(objArr);
            if (onWireLength <= 24) {
                i2 = 24 - onWireLength;
            } else {
                int i3 = onWireLength - 24;
                i2 = ProtoHelpers.calculatePad(i3);
                i = ProtoHelpers.roundUpLength4(i3) / 4;
            }
        }
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte(b);
            this.outputStream.writeByte(b2);
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.writeInt(i);
            if (z) {
                PODWriter.write(this.outputStream, objArr);
                this.outputStream.write(zero, 0, i2);
            } else {
                this.outputStream.write(zero, 0, i2);
                PODWriter.write(this.outputStream, objArr);
            }
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }

    public void sendSimpleSuccessReply(byte b, Object... objArr) throws IOException {
        sendReply(true, (byte) 1, b, 0, objArr);
    }

    /* DUMMY: stub kompatibilitas untuk DRI3Requests (fd tidak benar-benar dikirim).
       Cocok dengan pemanggilan: sendReplyWithFd((byte) 1, seq, deviceFd, null) */
    public void sendReplyWithFd(byte b, int i, int i2, Object obj) throws IOException {
        sendReply(true, (byte) 1, b, 0, new Object[0]);
    }

    public void sendSuccessReply(byte b, Object... objArr) throws IOException {
        sendReply(false, (byte) 1, b, ProtoHelpers.calculateLengthInWords(PODWriter.getOnWireLength(objArr)), objArr);
    }

    public void sendError(XProtocolError xProtocolError) throws IOException {
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte((byte) 0);
            this.outputStream.writeByte(xProtocolError.getErrorCode());
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.writeInt(xProtocolError.getData());
            this.outputStream.writeShort(this.inResponseTo.getMinorOpcode());
            this.outputStream.writeByte(this.inResponseTo.getMajorOpcode());
            this.outputStream.write(zero, 0, 21);
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }

    /**
     * BARU: infrastruktur X Generic Event (XGE) - proyek ini sebelumnya
     * TIDAK PUNYA kode XGE sama sekali. Wire format diverifikasi persis
     * terhadap header resmi X.org (presentproto.h, xproto GenericEvent
     * spec - https://www.x.org/releases/X11R7.6/doc/xextproto/geproto.html):
     *
     *   byte  0    : type       - SELALU 35 (GenericEvent), bukan opcode
     *                             extension seperti event klasik
     *   byte  1    : extension  - major opcode extension (mis. 156=Present)
     *   byte  2-3  : sequenceNumber (CARD16)
     *   byte  4-7  : length     - CARD32, jumlah blok 4-byte SETELAH byte
     *                             ke-32 (BUKAN total panjang event!). 0
     *                             kalau event pas 32 byte (spt PresentIdleNotify)
     *   byte  8-9  : evtype     - CARD16, sub-tipe event dalam extension ini
     *   byte 10-31 : sisa header 32-byte + payload evtype-specific (22 byte
     *                pertama dari sisa ini WAJIB ada meski extension event-nya
     *                cuma 32 byte total - itulah "22 byte" standar xGenericEvent)
     *   byte 32+   : payload tambahan kalau length > 0 (length*4 byte)
     *
     * payloadAfterEvtype: BufferFiller yang WAJIB mengisi TEPAT
     * (22 + extraPayloadWords*4) byte - dihitung dan divalidasi oleh
     * caller (lihat EventWriter untuk PresentCompleteNotify.class di
     * XEventSender.java), bukan oleh method ini, supaya kesalahan ukuran
     * ketahuan dari sisi pemanggil yang tahu struct event mana yang
     * sedang ditulis.
     */
    public void sendGenericEvent(byte extension, short evtype, int extraPayloadWords,
                                  final ResponseDataWriter payloadAfterEvtype) throws IOException {
        Assert.isTrue(extraPayloadWords >= 0, "extraPayloadWords tidak boleh negatif.");
        final int payloadSize = 22 + (extraPayloadWords * 4);
        XStreamLock lock = this.outputStream.lock();
        try {
            this.outputStream.writeByte((byte) 35); // xGenericEvent.type, SELALU 35
            this.outputStream.writeByte(extension);
            this.outputStream.writeShort((short) this.requestSequenceNumber);
            this.outputStream.writeInt(extraPayloadWords);
            this.outputStream.writeShort(evtype);
            this.outputStream.write(payloadSize, new BufferFiller() {
                @Override
                public void write(ByteBuffer byteBuffer) {
                    int startPos = byteBuffer.position();
                    payloadAfterEvtype.write(byteBuffer);
                    int written = byteBuffer.position() - startPos;
                    // Sisa slot yang tidak diisi caller (mis. field pad
                    // XGE yang memang harus nol) diisi nol - konsisten
                    // dengan pola sendReply()/sendEvent() yang sudah ada
                    // di file ini (pad dengan XResponse.zero).
                    if (written < payloadSize) {
                        byteBuffer.put(XResponse.zero, 0, payloadSize - written);
                    }
                }
            });
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            try {
                throw th;
            } catch (Throwable th2) {
                if (lock != null) {
                    try {
                        lock.close();
                    } catch (Throwable th3) {
                        th.addSuppressed(th3);
                    }
                }
                throw th2;
            }
        }
    }
}