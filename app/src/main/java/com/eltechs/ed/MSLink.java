package com.eltechs.ed;

import android.content.Context;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public abstract class MSLink {

    private static final int HasLinkTargetIDList = 0x0001;
    private static final int HasArguments       = 0x0020;
    private static final int HasIconLocation    = 0x0040;
    private static final int ForceNoLinkInfo    = 0x0100;

    public static final byte SW_SHOWNORMAL      = 1;
    public static final byte SW_SHOWMAXIMIZED   = 3;
    public static final byte SW_SHOWMINNOACTIVE = 7;

    public static class Options {
        public String targetPath   = "";
        public String cmdArgs      = null;
        public String iconLocation = null;
        public int    iconIndex    = 0;
        public int    fileSize     = 0;
        public byte   showCommand  = SW_SHOWNORMAL;
    }

    public static void createShortcut(Options options, File outputLnkFile) throws IOException {
        if (options == null || outputLnkFile == null) {
            throw new IllegalArgumentException("Options and output file cannot be null");
        }

        options.targetPath = normalizePath(options.targetPath);

        int linkFlags = HasLinkTargetIDList | ForceNoLinkInfo;
        if (options.cmdArgs != null && !options.cmdArgs.isEmpty()) {
            linkFlags |= HasArguments;
        }
        if (options.iconLocation != null && !options.iconLocation.isEmpty()) {
            linkFlags |= HasIconLocation;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream(outputLnkFile)) {

            // Header
            baos.write(new byte[]{0x4C, 0x00, 0x00, 0x00});                           // HeaderSize
            baos.write(convertCLSID("00021401-0000-0000-c000-000000000046"));        // Link CLSID
            baos.write(intToBytes(linkFlags, 4));                                     // LinkFlags

            // Common fields
            baos.write(new byte[]{0x20, 0x00, 0x00, 0x00});                           // FileAttributes
            baos.write(new byte[8]);                                                  // CreationTime
            baos.write(new byte[8]);                                                  // AccessTime
            baos.write(new byte[8]);                                                  // WriteTime
            baos.write(intToBytes(options.fileSize, 4));                              // FileSize
            baos.write(intToBytes(options.iconIndex, 4));                             // IconIndex
            baos.write(new byte[]{options.showCommand, 0x00, 0x00, 0x00});           // ShowCommand
            baos.write(new byte[]{0x00, 0x00});                                       // Hotkey
            baos.write(new byte[]{0x00, 0x00});                                       // Reserved1
            baos.write(new byte[]{0x00, 0x00, 0x00, 0x00});                           // Reserved2
            baos.write(new byte[]{0x00, 0x00, 0x00, 0x00});                           // Reserved3

            // Target IDList
            byte[] idList = buildTargetIDList(options.targetPath);
            baos.write(shortToBytes((short) idList.length));
            baos.write(idList);

            // StringData
            if ((linkFlags & HasArguments) != 0) {
                baos.write(stringToUnicodePrefixed(options.cmdArgs));
            }
            if ((linkFlags & HasIconLocation) != 0) {
                baos.write(stringToUnicodePrefixed(options.iconLocation));
            }

            fos.write(baos.toByteArray());
        }
    }

    private static byte[] buildTargetIDList(String targetPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Root (My Computer CLSID)
        byte[] rootClsid = convertCLSID("20d04fe0-3aea-1069-a2d8-08002b30309d");
        addItemId(baos, concatBytes(new byte[]{0x1F, 0x50}, rootClsid));

        // Path components
        String[] parts = targetPath.split("\\\\");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            addItemId(baos, concatBytes(new byte[]{0x31, 0x00}, stringToAnsi(part)));
        }

        // Terminator
        baos.write(new byte[]{0x00, 0x00});

        return baos.toByteArray();
    }

    private static void addItemId(ByteArrayOutputStream baos, byte[] data) throws IOException {
        baos.write(shortToBytes((short) (data.length + 2)));
        baos.write(data);
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("/+", "\\\\").replaceAll("\\\\+$", "");
    }

    // ────────────────────────────────────────────────
    // Helper methods
    // ────────────────────────────────────────────────

    private static byte[] intToBytes(int value, int length) {
        ByteBuffer bb = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        if (length == 4) bb.putInt(value);
        else if (length == 2) bb.putShort((short) value);
        return bb.array();
    }

    private static byte[] shortToBytes(short value) {
        return intToBytes(value, 2);
    }

    private static byte[] stringToUnicodePrefixed(String s) {
        if (s == null) s = "";
        byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer bb = ByteBuffer.allocate(utf16.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) (s.length()));
        bb.put(utf16);
        return bb.array();
    }

    private static byte[] stringToAnsi(String s) {
        byte[] bytes = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            bytes[i] = (byte) s.charAt(i);
        }
        return bytes;
    }

    private static byte[] concatBytes(byte[]... arrays) throws IOException {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(total);
        for (byte[] a : arrays) {
            baos.write(a);
        }
        return baos.toByteArray();
    }

    private static byte[] convertCLSID(String clsid) {
        String clean = clsid.replace("-", "");
        byte[] bytes = new byte[16];
        int pos = 0;
        for (int i = 0; i < 16; i += 2) {
            int hi = Character.digit(clean.charAt(i), 16);
            int lo = Character.digit(clean.charAt(i + 1), 16);
            bytes[pos++] = (byte) ((hi << 4) | lo);
        }

        // Reorder ke format little-endian GUID
        byte[] result = new byte[16];
        // Data1 (4 bytes) dibalik
        for (int i = 0; i < 4; i++) result[i] = bytes[3 - i];
        // Data2 (2 bytes) dibalik
        result[4] = bytes[5];
        result[5] = bytes[4];
        // Data3 (2 bytes) dibalik
        result[6] = bytes[7];
        result[7] = bytes[6];
        // Data4 (8 bytes) tetap
        System.arraycopy(bytes, 8, result, 8, 8);

        return result;
    }

    // ────────────────────────────────────────────────
    // .desktop file creation (Linux/Wine style)
    // ────────────────────────────────────────────────

    public static void createDesktopEntry(File lnkFile, Context context) {
        String lnkPath = lnkFile.getAbsolutePath();
        String nameNoExt = lnkFile.getName().replaceFirst("[.][^.]+$", "");

        String targetPath = parseTargetPath(lnkFile);
        if (targetPath.isEmpty()) return;

        targetPath = targetPath.replace("\\", "/");  // Wine prefers POSIX paths

        File desktopFile = new File(lnkPath.replaceFirst("[.][^.]+$", ".desktop"));

        try (PrintWriter pw = new PrintWriter(new FileOutputStream(desktopFile))) {
            pw.println("[Desktop Entry]");
            pw.println("Name=" + nameNoExt);
            pw.println("Exec=env WINEPREFIX=\"home/xdroid/.wine\" wine \"" + targetPath + "\"");
            pw.println("Type=Application");
            pw.println("StartupNotify=true");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

  public static String parseTargetPath(File lnkFile) {
    try (FileInputStream fis = new FileInputStream(lnkFile);
         DataInputStream dis = new DataInputStream(fis)) {

        byte[] data = new byte[(int) lnkFile.length()];
        dis.readFully(data);
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int headerSize = bb.getInt(0);
        if (headerSize != 0x4C) return "";

        int linkFlags = bb.getInt(0x14); // offset 20

        // Lewati sampai akhir LinkTargetIDList
        short idListSize = bb.getShort(0x4C); // offset 76
        int pos = 0x4C + 2 + idListSize;     // 78 + size

        // Jika ada LinkInfo (bit 0x02)
        boolean hasLinkInfo = (linkFlags & 0x02) != 0;
        String target = "";

        if (hasLinkInfo && pos + 4 < data.length) {
            int linkInfoSize = bb.getInt(pos);
            if (linkInfoSize >= 0x1C && pos + linkInfoSize <= data.length) {
                int linkInfoFlags = bb.getInt(pos + 0x14);
                boolean hasUnicode = (linkInfoFlags & 0x01) != 0; // bit 0 = VolumeIDAndLocalBasePath unicode

                int localBasePathOffset = bb.getInt(pos + 0x10);
                if (localBasePathOffset > 0) {
                    localBasePathOffset += pos; // relative ke awal LinkInfo

                    if (hasUnicode) {
                        // Unicode string (UTF-16LE)
                        bb.position(localBasePathOffset);
                        StringBuilder sb = new StringBuilder();
                        while (bb.hasRemaining()) {
                            char c = bb.getChar();
                            if (c == 0) break;
                            sb.append(c);
                        }
                        target = sb.toString();
                    } else {
                        // ANSI
                        bb.position(localBasePathOffset);
                        StringBuilder sb = new StringBuilder();
                        while (bb.hasRemaining()) {
                            byte b = bb.get();
                            if (b == 0) break;
                            sb.append((char) (b & 0xFF));
                        }
                        target = sb.toString();
                    }

                    if (!target.isEmpty()) {
                        return target.replace('\\', '/');
                    }
                }
            }
        }

        // Fallback: coba baca StringData (jika LinkInfo gagal)
        // ... (bisa ditambah kalau perlu)

        return "";
    } catch (Exception e) {
        e.printStackTrace();
        return "";
    }
}

    private static String readNullTerminatedUnicode(ByteBuffer bb, int offset) {
        bb.position(offset);
        StringBuilder sb = new StringBuilder();
        while (bb.hasRemaining()) {
            char c = bb.getChar();
            if (c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }

    // Convenience method
    public static void createShortcut(String targetPath, File outputFile) throws IOException {
        Options opt = new Options();
        opt.targetPath = targetPath;
        createShortcut(opt, outputFile);
    }
}