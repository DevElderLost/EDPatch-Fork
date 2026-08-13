package com.eltechs.ed.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;

// Helper sederhana – cari kemungkinan data ICO di dalam .exe
public class SimpleExeIconExtractor {

    public static Bitmap tryExtractIconBitmap(File exeFile) {
        if (!exeFile.exists() || !exeFile.canRead()) return null;

        try (RandomAccessFile raf = new RandomAccessFile(exeFile, "r")) {
            long fileSize = raf.length();
            if (fileSize < 100) return null;

            // Cek header MZ
            raf.seek(0);
            if (raf.readShort() != 0x5A4D) { // 'MZ'
                return null;
            }

            // Cari offset PE header
            raf.seek(0x3C);
            int peOffset = Integer.reverseBytes(raf.readInt()); // little-endian

            raf.seek(peOffset);
            if (raf.readInt() != 0x00004550) { // 'PE\0\0'
                return null;
            }

            // Cari byte magic ICO di seluruh file (cari \x00\x00\x01\x00)
            byte[] buffer = new byte[32768]; // baca chunk demi chunk agar hemat memori
            raf.seek(0);
            int bytesRead;

            while ((bytesRead = raf.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead - 6; i++) {
                    // ICO header biasa: 00 00 01 00 (reserved, type=1=ICO)
                    if (buffer[i] == 0 && buffer[i+1] == 0 &&
                        buffer[i+2] == 1 && buffer[i+3] == 0) {

                        // Coba ambil dari sini sampai akhir chunk (asumsi 1 icon group)
                        int possibleIcoStart = i;
                        int possibleLength = bytesRead - possibleIcoStart;

                        byte[] candidate = new byte[possibleLength];
                        System.arraycopy(buffer, possibleIcoStart, candidate, 0, possibleLength);

                        try {
                            Bitmap bmp = BitmapFactory.decodeByteArray(candidate, 0, candidate.length);
                            if (bmp != null && bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                                // Sukses! Kembalikan thumbnail
                                return ThumbnailUtils.extractThumbnail(bmp, 128, 128);
                            }
                        } catch (Exception ignored) {
                            // Bukan ICO valid atau corrupt → lanjut cari
                        }
                    }
                }
            }
        } catch (IOException e) {
            // silent fail
        }
        return null;
    }
}