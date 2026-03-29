package com.eltechs.ed;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class MSBitmap {

    private MSBitmap() {
    }

    public static boolean create(Bitmap bitmap, File file) {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int rowPadding = (4 - ((width * 3) % 4)) % 4;
        int imageSize = ((width * 3) + rowPadding) * height;
        int fileSize = 54 + imageSize;

        ByteBuffer buffer = ByteBuffer
                .allocate(fileSize)
                .order(ByteOrder.LITTLE_ENDIAN);

        /* ===== BMP HEADER ===== */

        buffer.putShort((short) 0x4D42);      // Signature "BM"
        buffer.putInt(fileSize);              // File size
        buffer.putInt(0);                     // Reserved
        buffer.putInt(54);                    // Pixel data offset

        /* ===== DIB HEADER ===== */

        buffer.putInt(40);                    // DIB header size
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putShort((short) 1);           // Planes
        buffer.putShort((short) 24);          // Bits per pixel
        buffer.putInt(0);                     // Compression (none)
        buffer.putInt(imageSize);
        buffer.putInt(0);                     // X pixels per meter
        buffer.putInt(0);                     // Y pixels per meter
        buffer.putInt(0);                     // Total colors
        buffer.putInt(0);                     // Important colors

        /* ===== PIXEL DATA (Bottom-Up) ===== */

        for (int y = height - 1; y >= 0; y--) {

            for (int x = 0; x < width; x++) {

                int color = pixels[y * width + x];

                buffer.put((byte) Color.blue(color));
                buffer.put((byte) Color.green(color));
                buffer.put((byte) Color.red(color));
            }

            for (int p = 0; p < rowPadding; p++) {
                buffer.put((byte) 0);
            }
        }

        /* ===== WRITE FILE ===== */

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array());
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}