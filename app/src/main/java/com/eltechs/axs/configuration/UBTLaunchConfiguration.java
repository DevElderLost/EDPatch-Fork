package com.eltechs.axs.configuration;

import android.content.SharedPreferences;
import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.environmentService.AXSEnvironment;
import com.eltechs.axs.environmentService.components.ALSAServerComponent;
import com.eltechs.axs.environmentService.components.DirectSoundServerComponent;
import com.eltechs.axs.environmentService.components.VirglServerComponent;
import com.eltechs.axs.environmentService.components.XServerComponent;

import com.example.datainsert.exagear.QH;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UBTLaunchConfiguration implements Serializable {

    private static final String TAG = "UBTLaunchConfig";

    public static String RENDERER;
    public static String gallium_driver;

    private Map<String, String> fileNameReplacements = Collections.emptyMap();
    private String fsRoot = null;
    private List<String> guestArguments = Collections.emptyList();
    private List<String> guestEnvironmentVariables = new ArrayList<>();
    private String guestExecutable = null;
    private String guestExecutablePath = null;
    private boolean isForceShmEnabled = true;
    private boolean isStraceEnabled = false;
    private boolean isTraceMemEnabled = true;
    private String socketPathSuffix;
    private Set<VFSHacks> vfsHacks = EnumSet.noneOf(VFSHacks.class);

    public enum VFSHacks {
        PRERESOLVE_WINE_DRIVE_SYMLINKS("pwds"),
        PRERESOLVE_EXPLICITLY_LISTED_SYMLINKS("pels"),
        ASSUME_NO_SYMLINKS_EXCEPT_PRERESOLVED("ansep"),
        TREAT_LSTAT_SOCKET_AS_STATTING_WINESERVER_SOCKET("tlsasws"),
        TRUNCATE_STAT_INODE("tsi"),
        SIMPLE_PASS_DEV("spd");

        private final String shortName;

        VFSHacks(String shortName) {
            this.shortName = shortName;
        }

        public String getShortName() {
            return shortName;
        }
    }

    public void addArgumentsToEnvironment(AXSEnvironment env) {
        // GALLIUM_DRIVER (jika ada)
        if (gallium_driver != null && !gallium_driver.trim().isEmpty()) {
            guestEnvironmentVariables.add("GALLIUM_DRIVER=" + gallium_driver.trim());
        }

        // Environment tambahan dari file render_env.txt
        File renderEnvFile = new File(
                ((ExagearImageAware) Globals.getApplicationState()).getExagearImage().getPath(),
                "opt/rcp/render_env.txt"
        );
        Map<String, List<String>> envMap = parseEnvFile(renderEnvFile);
        List<String> rendererEnvs = envMap.get(RENDERER);
        if (rendererEnvs != null) {
            guestEnvironmentVariables.addAll(rendererEnvs);
        }

        // DISPLAY
        XServerComponent xServer = env.getComponent(XServerComponent.class);
        if (xServer != null) {
            guestEnvironmentVariables.add(String.format("DISPLAY=:%d", xServer.getDisplayNumber()));
        }

        // VirglRenderer (VTEST_SOCKET)
        VirglServerComponent virgl = env.getComponent(VirglServerComponent.class);
        if (virgl != null) {
            guestEnvironmentVariables.add("GALLIUM_DRIVER=virpipe");
            guestEnvironmentVariables.add(String.format("VTEST_SOCKET=%s", virgl.getAddress()));
        }

        // ALSA server
        ALSAServerComponent alsa = env.getComponent(ALSAServerComponent.class);
        if (alsa != null) {
            guestEnvironmentVariables.add(String.format("ANDROID_ALSA_SERVER=%s", alsa.getAddress()));
        }

        // DirectSound server
        DirectSoundServerComponent dsound = env.getComponent(DirectSoundServerComponent.class);
        if (dsound != null) {
            guestEnvironmentVariables.add(String.format("AXS_DSOUND_SERVER_PORT=%s", dsound.getAddress()));
        }

        // ────────────────────────────────────────────────
        // PulseAudio support (PULSE_SERVER)
        // ────────────────────────────────────────────────
        try {
            ExagearImageAware appState = (ExagearImageAware) Globals.getApplicationState();
            File imagePath = appState.getExagearImage().getPath();
            File xdroidFolder = new File(imagePath, "home/xdroid");

            if (xdroidFolder.exists() && xdroidFolder.isDirectory()) {
                String folderName = xdroidFolder.getName();  // biasanya xdroid_123456
                if (folderName.startsWith("xdroid_")) {
                    String idString = folderName.substring("xdroid_".length());
                    long contId = Long.parseLong(idString);

                    SharedPreferences contPref = QH.getContPref(contId);
                    if (contPref != null) {
                        String soundAction = contPref.getString("SOUND_ACTIONS", "pulse");
                        boolean pulseAutorun = QH.getPreference().getBoolean("PULSE_AUTORUN", true);

                        if (pulseAutorun && "pulse".equalsIgnoreCase(soundAction.trim())) {
                            guestEnvironmentVariables.add("PULSE_SERVER=tcp:127.0.0.1:4713");
                            // Opsional: tambahkan jika diperlukan
                            // guestEnvironmentVariables.add("PULSE_SINK=...default...");
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid container ID format in xdroid folder name", e);
        } catch (Exception e) {
            Log.w(TAG, "Failed to configure PULSE_SERVER environment", e);
        }
    }

    private Map<String, List<String>> parseEnvFile(File file) {
        Map<String, List<String>> result = new HashMap<>();

        if (!file.isFile() || !file.canRead()) {
            return result;
        }

        String currentKey = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("key:")) {
                    currentKey = line.substring(4).trim();
                    result.computeIfAbsent(currentKey, k -> new ArrayList<>());
                } else if (currentKey != null && line.startsWith("env:")) {
                    String value = line.substring(4).trim();
                    if (!value.isEmpty()) {
                        result.get(currentKey).add(value);
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to parse render_env.txt: " + file.getPath(), e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    //  Getter dan Setter
    // -------------------------------------------------------------------------

    public void addEnvironmentVariable(String key, String value) {
        if (key != null && value != null) {
            guestEnvironmentVariables.add(key + "=" + value);
        }
    }

    public Map<String, String> getFileNameReplacements() {
        return fileNameReplacements;
    }

    public void setFileNameReplacements(Map<String, String> replacements) {
        this.fileNameReplacements = (replacements != null) ? replacements : Collections.emptyMap();
    }

    public String getFsRoot() {
        return fsRoot;
    }

    public void setFsRoot(String fsRoot) {
        this.fsRoot = fsRoot;
    }

    public List<String> getGuestArguments() {
        return guestArguments;
    }

    public void setGuestArguments(List<String> args) {
        this.guestArguments = (args != null) ? new ArrayList<>(args) : Collections.emptyList();
    }

    public void setGuestArguments(String... args) {
        setGuestArguments(Arrays.asList(args));
    }

    public List<String> getGuestEnvironmentVariables() {
        return guestEnvironmentVariables;
    }

    public void setGuestEnvironmentVariables(List<String> vars) {
        this.guestEnvironmentVariables = (vars != null) ? new ArrayList<>(vars) : new ArrayList<>();
    }

    public void setGuestEnvironmentVariables(String... vars) {
        setGuestEnvironmentVariables(Arrays.asList(vars));
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String executable) {
        this.guestExecutable = executable;
    }

    public String getGuestExecutablePath() {
        return guestExecutablePath;
    }

    public void setGuestExecutablePath(String path) {
        this.guestExecutablePath = path;
    }

    public String getSocketPathSuffix() {
        return socketPathSuffix;
    }

    public void setSocketPathSuffix(String suffix) {
        this.socketPathSuffix = suffix;
    }

    public Set<VFSHacks> getVfsHacks() {
        if (!fileNameReplacements.isEmpty()) {
            vfsHacks.add(VFSHacks.PRERESOLVE_EXPLICITLY_LISTED_SYMLINKS);
        }
        return EnumSet.copyOf(vfsHacks);
    }

    public void setVfsHacks(Set<VFSHacks> hacks) {
        this.vfsHacks = (hacks != null) ? EnumSet.copyOf(hacks) : EnumSet.noneOf(VFSHacks.class);
    }

    public boolean isForceShmEnabled() {
        return isForceShmEnabled;
    }

    public void setForceShmEnabled(boolean enabled) {
        this.isForceShmEnabled = enabled;
    }

    public boolean isStraceEnabled() {
        return isStraceEnabled;
    }

    public void setStraceEnabled(boolean enabled) {
        this.isStraceEnabled = enabled;
    }

    public boolean isTraceMemEnabled() {
        return isTraceMemEnabled;
    }

    public void setTraceMemEnabled(boolean enabled) {
        this.isTraceMemEnabled = enabled;
    }
}