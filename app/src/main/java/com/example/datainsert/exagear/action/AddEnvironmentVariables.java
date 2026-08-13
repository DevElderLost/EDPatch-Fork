package com.example.datainsert.exagear.action;

import android.util.Log;

import com.eltechs.axs.applicationState.EnvironmentAware;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.applicationState.UBTLaunchConfigurationAware;
import com.eltechs.axs.configuration.UBTLaunchConfiguration;
import com.eltechs.axs.configuration.startup.actions.AbstractStartupAction;
import com.eltechs.axs.helpers.SafeFileHelpers;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.containerSettings.ConSetOtherArgv;
import com.example.datainsert.exagear.containerSettings.otherargv.Arguments;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Versi minimal: hanya otherArgv + LD_LIBRARY_PATH
 * Tetap pertahankan logika penentuan contId dari path xdroid_n
 */
public class AddEnvironmentVariables<StateClass extends UBTLaunchConfigurationAware & EnvironmentAware & ExagearImageAware>
        extends AbstractStartupAction<StateClass> {

    private static final String TAG = "AddEnvVars-Minimal";

    /**
     * Path library dasar yang hampir selalu ditambahkan
     */
    private final StringBuilder extraLDPaths = new StringBuilder(":/usr/lib/i386-linux-gnu");

    public AddEnvironmentVariables() {
    }

    @Override
    public void execute() {
        new Thread(() -> {
            try {
                UBTLaunchConfiguration ubtConfig = getApplicationState().getUBTLaunchConfiguration();

                // ────────────────────────────────────────────────
                // Ambil container ID seperti logika asli
                // ────────────────────────────────────────────────
                File xdroidFile = new File(getApplicationState().getExagearImage().getPath(), "home/xdroid");
                long contId = 0;
                try {
                    String folderName = xdroidFile.getCanonicalFile().getName();
                    if (folderName.startsWith("xdroid_")) {
                        contId = Long.parseLong(folderName.substring("xdroid_".length()));
                    }
                } catch (IOException | NumberFormatException e) {
                    Log.w(TAG, "Gagal parse container ID dari xdroid path", e);
                    // lanjutkan dengan contId = 0 sebagai fallback
                }

                // ────────────────────────────────────────────────
                // 1. OtherArgv (taskset, env, prefix/suffix, dll)
                // ────────────────────────────────────────────────
                if (QH.classExist("com.example.datainsert.exagear.containerSettings.ConSetOtherArgv")) {
                    modifyWineCommandWithOtherArgs(ubtConfig, contId);
                }

                // ────────────────────────────────────────────────
                // 2. Gabungkan LD_LIBRARY_PATH (extra di depan)
                // ────────────────────────────────────────────────
                mergeExtraLdLibraryPath(ubtConfig);

                Log.i(TAG, "Setup selesai untuk container " + contId);
            } catch (Exception e) {
                Log.w(TAG, "Gagal menjalankan AddEnvironmentVariables (minimal)", e);
            } finally {
                sendDone();
            }
        }, "AddEnvVars-Minimal").start();
    }

    private void modifyWineCommandWithOtherArgs(UBTLaunchConfiguration ubtConfig, long contId) {
        List<String> argvList = ubtConfig.getGuestArguments();
        if (argvList == null || argvList.isEmpty()) {
            return;
        }

        // Ambil command wine (biasanya elemen terakhir)
        String wineCmd = argvList.get(argvList.size() - 1).trim();
        if (wineCmd.isEmpty()) {
            return;
        }

        // Load argumen dari pool sesuai container
        Arguments.allFromPoolFile((int) contId);

        // Sisipkan argumen sesuai aturan ConSetOtherArgv
        String modifiedCmd = ConSetOtherArgv.insertArgsToWineCmd(
                wineCmd,
                ubtConfig.getGuestEnvironmentVariables(),
                (int) contId
        );

        // Update daftar argumen
        List<String> newArgv = new ArrayList<>(argvList);
        newArgv.set(newArgv.size() - 1, modifiedCmd);
        ubtConfig.setGuestArguments(newArgv.toArray(new String[0]));

        Log.d(TAG, "Wine command setelah otherArgv:\n  " + modifiedCmd);
    }

    private void mergeExtraLdLibraryPath(UBTLaunchConfiguration ubtConfig) {
        List<String> envList = ubtConfig.getGuestEnvironmentVariables();

        String existingLD = null;
        int existingIndex = -1;

        for (int i = 0; i < envList.size(); i++) {
            String var = envList.get(i);
            if (var.startsWith("LD_LIBRARY_PATH=")) {
                existingLD = var.substring("LD_LIBRARY_PATH=".length());
                existingIndex = i;
                break;
            }
        }

        String finalLD;
        if (existingLD != null) {
            // Tambah extra di depan (jika ada)
            StringBuilder sb = new StringBuilder(extraLDPaths);
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(existingLD);
            finalLD = sb.toString();
            // hapus entry lama
            envList.remove(existingIndex);
        } else {
            finalLD = extraLDPaths.toString();
            if (finalLD.startsWith(":")) {
                finalLD = finalLD.substring(1);
            }
        }

        if (!finalLD.isEmpty()) {
            ubtConfig.addEnvironmentVariable("LD_LIBRARY_PATH", finalLD);
            Log.d(TAG, "LD_LIBRARY_PATH akhir: " + finalLD);
        }
    }
}