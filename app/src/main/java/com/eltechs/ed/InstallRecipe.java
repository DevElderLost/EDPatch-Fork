package com.eltechs.ed;

import android.content.Context;
import android.text.TextUtils;
import com.eltechs.axs.xserver.ScreenInfo;
import com.eltechs.ed.controls.Controls;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InstallRecipe {

    private static final String DRIVES_FILE_PATH = "/opt/edpatch/drives.txt";

    // Recipe statis yang selalu ada
    private static final List<InstallRecipe> STATIC_RECIPES = Arrays.asList(
            new InstallRecipe("Browse File")
            // Tambah recipe statis lain jika diperlukan
    );

    /**
     * Mendapatkan daftar semua recipe: statis + dinamis dari drives.txt
     */
    public static List<InstallRecipe> getAllRecipes(
            Context context,
            GuestContainer guestCont,
            GuestContainersManager manager) {

        List<InstallRecipe> recipes = new ArrayList<>(STATIC_RECIPES);

        if (guestCont == null || manager == null) {
            return recipes; // fallback hanya static jika container/manager tidak ada
        }

        // Baca daftar drive menggunakan path yang sama seperti di DriveAdapter
        List<String> drives = readDrivesFromFile(manager);

        for (String driveEntry : drives) {
            if (driveEntry == null || driveEntry.trim().isEmpty()) continue;

            String entry = driveEntry.trim();
            if (entry.length() < 2 || entry.charAt(1) != ':') continue;

            char letter = Character.toUpperCase(entry.charAt(0));
            if (letter < 'A' || letter > 'Z') continue;

            String displayName = (letter == 'D')
                    ? "Drive D: (Default)"
                    : "Drive " + letter + ":";

            InstallRecipe recipe = new InstallRecipe(displayName)
                    .setGuestCont(guestCont)
                    .setRunScript("run/drive_access.sh")          // sesuaikan script sesuai aplikasi
                    .setRunArguments("\"" + entry + "\"")
                    .setRunGuide("Akses folder di " + entry)
                    .setInstallScript("simple.sh");

            recipes.add(recipe);
        }

        // Urutkan agar Drive D muncul paling atas setelah item statis
        if (recipes.size() > STATIC_RECIPES.size()) {
            Collections.sort(recipes.subList(STATIC_RECIPES.size(), recipes.size()),
                    (r1, r2) -> {
                        String n1 = r1.getName();
                        String n2 = r2.getName();
                        if (n1.contains("Drive D")) return -1;
                        if (n2.contains("Drive D")) return 1;
                        return n1.compareTo(n2);
                    });
        }

        return recipes;
    }

    private static List<String> readDrivesFromFile(GuestContainersManager manager) {
        List<String> drives = new ArrayList<>();

        try {
            File file = new File(manager.getHostPath(DRIVES_FILE_PATH));

            if (!file.exists() || !file.canRead()) {
                return drives;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        drives.add(line);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Bisa tambahkan logging atau Toast jika diperlukan
        }

        return drives;
    }

    // ────────────────────────────────────────────────────────────────
    // Field dan method chainable (asli + tambahan)
    // ────────────────────────────────────────────────────────────────

    public Controls mControls = null;
    public String mDownloadURL = null;
    public GuestContainer mGuestCont;
    public String mInstallScriptName = "simple.sh";
    public String mLocaleName = null;
    public final String mName;
    public String mRunArguments = null;
    public String mRunGuide = null;
    public String mRunScriptName = "run/simple.sh";
    public ScreenInfo mScreenInfo = null;
    public String mStartupActions = null;

    public InstallRecipe(String name) {
        this.mName = name;
    }

    private InstallRecipe setControls(Controls controls) {
        this.mControls = controls;
        return this;
    }

    private InstallRecipe setDownloadURL(String url) {
        this.mDownloadURL = url;
        return this;
    }

    private InstallRecipe setInstallScript(String script) {
        this.mInstallScriptName = script;
        return this;
    }

    private InstallRecipe setLocaleName(String locale) {
        this.mLocaleName = locale;
        return this;
    }

    private InstallRecipe setRunArguments(String args) {
        this.mRunArguments = args;
        return this;
    }

    private InstallRecipe setRunGuide(String guide) {
        this.mRunGuide = guide;
        return this;
    }

    private InstallRecipe setRunScript(String script) {
        this.mRunScriptName = script;
        return this;
    }

    private InstallRecipe setScreenInfo(ScreenInfo info) {
        this.mScreenInfo = info;
        return this;
    }

    private InstallRecipe setStartupActions(String... actions) {
        this.mStartupActions = TextUtils.join(" ", actions);
        return this;
    }

    private InstallRecipe setGuestCont(GuestContainer cont) {
        this.mGuestCont = cont;
        return this;
    }

    public String getDownloadURL() {
        return mDownloadURL;
    }

    public String getName() {
        return mName;
    }

    public GuestContainer getGuestContainer() {
        return mGuestCont;
    }

    @Override
    public String toString() {
        return getName();
    }
}