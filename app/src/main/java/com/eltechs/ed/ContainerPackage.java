package com.eltechs.ed;

import android.content.Context;
import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ContainerPackage {

    public final String mDisplayName;
    public final String mName;

    public ContainerPackage(String displayName, String name) {
        this.mDisplayName = displayName;
        this.mName = name;
    }

    @Override
    public String toString() {
        return mDisplayName != null ? mDisplayName : mName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerPackage that = (ContainerPackage) o;
        return mName.equals(that.mName);
    }

    @Override
    public int hashCode() {
        return mName.hashCode();
    }

    public static List<ContainerPackage> getAllPackages() {
        List<ContainerPackage> packages = new ArrayList<>();

        Context ctx = Globals.getAppContext();
        if (ctx == null) {
            Log.e("ContainerPkg", "AppContext null");
            return packages;
        }

        File exagearImageDir;
        try {
            ExagearImageAware imageAware =
                    (ExagearImageAware) Globals.getApplicationState();
            exagearImageDir = imageAware.getExagearImage().getPath();
        } catch (Exception e) {
            Log.e("ContainerPkg", "Gagal mendapatkan ExagearImage path", e);
            return packages;
        }

        File file = new File(exagearImageDir, "opt/rcp/morepackages");

        if (!file.exists()) {
            Log.w("ContainerPkg", "File tidak ditemukan: " + file.getAbsolutePath());
            return packages;
        }

        if (!file.canRead()) {
            Log.e("ContainerPkg", "File tidak bisa dibaca: " + file.getAbsolutePath());
            return packages;
        }

        Log.d("ContainerPkg", "Membaca paket dari: " +
                file.getAbsolutePath() + " (" + file.length() + " bytes)");

        String pendingDisplay = null;
        boolean afterPopd = false;

        try (InputStream is = new FileInputStream(file);
             InputStreamReader isr =
                     new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {

            String line;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.equals("popd")) {
                    afterPopd = true;
                    Log.d("ContainerPkg", "Ditemukan popd → mulai parsing custom packages");
                    continue;
                }

                if (afterPopd &&
                        trimmed.startsWith("progress \"-1\" \"Preparing,Please wait...\"")) {
                    Log.d("ContainerPkg", "Ditemukan progress Preparing → stop parsing");
                    break;
                }

                if (!afterPopd) continue;

                if (trimmed.startsWith("# Package:")) {
                    String content = trimmed.substring(10).trim();
                    String[] parts = content.split("\\|", 2);
                    if (parts.length == 2) {
                        pendingDisplay = parts[0].trim();
                    }
                    continue;
                }

                if (trimmed.startsWith("if [ \"$2\" = \"")
                        && trimmed.contains("\" ]; then")) {

                    int start = trimmed.indexOf('"', 13) + 1;
                    int end = trimmed.indexOf('"', start);

                    if (start > 0 && end > start) {
                        String internal = trimmed.substring(start, end);

                        String display =
                                (pendingDisplay != null && !pendingDisplay.isEmpty())
                                        ? pendingDisplay
                                        : capitalize(internal
                                        .replace("-", " ")
                                        .replace("_", " "));

                        packages.add(new ContainerPackage(display, internal));

                        Log.d("ContainerPkg",
                                "Paket dibaca: " + display + " (" + internal + ")");

                        pendingDisplay = null;
                    }
                }
            }

        } catch (Exception e) {
            Log.e("ContainerPkg", "Gagal membaca file morepackages", e);
        }

        Log.d("ContainerPkg", "Total paket dibaca: " + packages.size());
        return packages;
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "Paket Tanpa Nama";

        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return sb.toString().trim();
    }
}