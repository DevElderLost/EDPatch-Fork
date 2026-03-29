package com.eltechs.ed.startupActions;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import android.os.Build;
import android.os.Environment;
import android.util.Log;
import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.applicationState.UBTLaunchConfigurationAware;
import com.eltechs.axs.configuration.UBTLaunchConfiguration;
import com.eltechs.axs.configuration.startup.EnvironmentCustomisationParameters;
import com.eltechs.axs.configuration.startup.actions.AbstractStartupAction;
import com.eltechs.axs.helpers.SafeFileHelpers;
import com.eltechs.axs.xserver.ScreenInfo;
import com.eltechs.ed.EDApplicationState;
import com.eltechs.ed.MSBitmap;
import com.eltechs.ed.fragments.ContainerSettingsFragment;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.example.datainsert.exagear.FAB.dialogfragment.PulseAudio;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.containerSettings.ConSetOtherArgv;
import com.example.datainsert.exagear.containerSettings.otherargv.Arguments;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CreateLaunchConfiguration<StateClass extends EDApplicationState>
        extends AbstractStartupAction<StateClass> {

    private static final String TAG = "CreateLaunchConfig";

    private final File applicationWorkingDir;
    private final String winePrefix;
    private final String[] argv;
    private final String[] envp;
    private final String socketPathSuffix;
    private final String userAreaDir;

    public CreateLaunchConfiguration(File applicationWorkingDir,
                                     String winePrefix,
                                     String[] argv,
                                     String[] envp,
                                     String socketPathSuffix,
                                     String userAreaDir) {
        this.applicationWorkingDir = applicationWorkingDir;
        this.winePrefix = winePrefix;
        this.argv = argv;
        this.envp = envp;
        this.socketPathSuffix = socketPathSuffix;
        this.userAreaDir = userAreaDir;
    }

    @Override
    public void execute() {

        EDApplicationState appState = (EDApplicationState) getApplicationState();

        EnvironmentCustomisationParameters envParams =
                appState.getSelectedExecutableFile()
                        .getEnvironmentCustomisationParameters();

        UBTLaunchConfiguration launchConfig = new UBTLaunchConfiguration();

        File imagePath = appState.getExagearImage().getPath();

        /* =========================================================
           Ambil container ID dari folder home/xdroid
           ========================================================= */
        File sedap = new File(imagePath, "home/xdroid");
        long contId = 0;

        try {
            String folderName = sedap.getCanonicalFile().getName();
            String idString = folderName.replace("xdroid_", "");
            contId = Long.parseLong(idString);
        } catch (Exception e) {
            Log.e(TAG, "Gagal mendapatkan container ID", e);
            sendError(e.getMessage(), e);
        }

        SharedPreferences contPref = QH.getContPref(contId);

        /* =========================================================
           Wallpaper Logic (resize jika SCREEN_SIZE bukan Default)
           ========================================================= */
        String screenSizePref = contPref.getString("SCREEN_SIZE", "");
        GuestContainersManager guestContainersManager = new GuestContainersManager(Globals.getAppContext());

        if (!"Default".equals(screenSizePref)) {
            String[] sizeParts = screenSizePref.split(",");
            if (sizeParts.length == 2) {
                try {
                    int width = Integer.parseInt(sizeParts[0].trim());
                    int height = Integer.parseInt(sizeParts[1].trim());

                    ScreenInfo screenInfo = new ScreenInfo(width, height, width / 10, height / 10, 24);
                    int scaledWidth = (int) Math.ceil((double) ((float) height / screenInfo.heightInPixels) * screenInfo.widthInPixels);

                    File wallpaperFile = new File(
                            guestContainersManager.getHostPath("/home/xdroid_" + contId + "/.wine/wallpaper.bmp")
                    );

                    if (wallpaperFile.exists()) {
                        Bitmap original = BitmapFactory.decodeFile(wallpaperFile.getPath());
                        if (original != null) {
                            Bitmap resized = Bitmap.createBitmap(scaledWidth, height, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(resized);
                            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            canvas.drawBitmap(original, new Rect(0, 0, original.getWidth(), original.getHeight()),
                                    new Rect(0, 0, scaledWidth, height), paint);

                            File targetBmp = new File(
                                    guestContainersManager.getGuestWinePrefixPath() + "/wallpaper.bmp"
                            );
                            MSBitmap.create(resized, targetBmp);
                            Log.d(TAG, "Wallpaper berhasil di-resize ke " + scaledWidth + "x" + height);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Gagal resize wallpaper", e);
                }
            }
        }

        /* =========================================================
           PulseAudio support
           ========================================================= */
        if ("pulse".equals(contPref.getString("SOUND_ACTIONS", "alsa"))) {
            try {
                Class.forName("com.example.datainsert.exagear.FAB.dialogfragment.PulseAudio");
                startPulseAudio(launchConfig);
            } catch (Exception e) {
                Log.w(TAG, "PulseAudio tidak tersedia atau gagal di-load", e);
            }
        }

        /* =========================================================
           VirGL built-in renderer
           ========================================================= */
        if ("virgl_built_in".equals(contPref.getString(ContainerSettingsFragment.KEY_RENDERER, "llvm"))) {
            try {
                handleVirGLRenderer();
            } catch (Throwable t) {
                Log.e(TAG, "Gagal mengatur VirGL renderer", t);
            }
        }

        /* =========================================================
           Konfigurasi dasar Launch
           ========================================================= */
        launchConfig.setFsRoot(imagePath.getAbsolutePath());
        launchConfig.setGuestExecutablePath(applicationWorkingDir.getAbsolutePath());
        launchConfig.setGuestExecutable(argv[0]);
        launchConfig.setGuestArguments(argv);
        launchConfig.setGuestEnvironmentVariables(envp);

        launchConfig.addEnvironmentVariable("LC_ALL", envParams.getLocaleName());
        launchConfig.addArgumentsToEnvironment(appState.getEnvironment());

        /* =========================================================
           Symlink dosdevices (c:, d:, e:, z:)
           ========================================================= */
        SafeFileHelpers.symlink(
                "../drive_c",
                new File(imagePath, winePrefix + "/dosdevices/c:").getAbsolutePath()
        );

        File driveD = new File(imagePath, winePrefix + "/dosdevices/d:");
        driveD.delete();

        SafeFileHelpers.symlink(
                userAreaDir,
                driveD.getAbsolutePath()
        );

        SafeFileHelpers.symlink(
                "/tmp/",
                new File(imagePath, winePrefix + "/dosdevices/e:").getAbsolutePath()
        );

        SafeFileHelpers.symlink(
                "/",
                new File(imagePath, winePrefix + "/dosdevices/z:").getAbsolutePath()
        );

        /* =========================================================
           VFS Hacks
           ========================================================= */
        launchConfig.setVfsHacks(
                EnumSet.of(
                        UBTLaunchConfiguration.VFSHacks.TREAT_LSTAT_SOCKET_AS_STATTING_WINESERVER_SOCKET,
                        UBTLaunchConfiguration.VFSHacks.TRUNCATE_STAT_INODE,
                        UBTLaunchConfiguration.VFSHacks.SIMPLE_PASS_DEV
                )
        );

        launchConfig.setSocketPathSuffix(socketPathSuffix);

        appState.setUBTLaunchConfiguration(launchConfig);

        sendDone();
    }

    private void startPulseAudio(UBTLaunchConfiguration launchConfig) {
        // Sesuaikan dengan preferensi Anda, atau gunakan nilai default
        boolean shouldStart = QH.getPreference().getBoolean("PULSE_AUTORUN", true);
        PulseAudio.installAndRun(shouldStart);
        launchConfig.addEnvironmentVariable("PULSE_SERVER", "tcp:127.0.0.1:4713");
    }

    private void handleVirGLRenderer() {
        File logFile = new File(Environment.getExternalStorageDirectory(), "virglLog.txt");
        String libPath = Globals.getAppContext().getApplicationInfo().nativeLibraryDir + "/libvirgl_test_server.so";

        File tmpDir = ((ExagearImageAware) getApplicationState())
                .getExagearImage()
                .getPath()
                .toPath()
                .resolve("tmp")
                .toFile();

        try {
            ProcessBuilder pb = new ProcessBuilder(libPath);
            pb.environment().put("TMPDIR", tmpDir.getAbsolutePath());

            if (Build.VERSION.SDK_INT >= 26) {
                pb.redirectErrorStream(true);
                pb.redirectOutput(logFile);
            }

            pb.start();
            Log.d(TAG, "VirGL test server dijalankan");
        } catch (Exception e) {
            Log.e(TAG, "Gagal menjalankan VirGL test server", e);
            // Optional: simpan stacktrace ke logFile
            try (java.io.PrintWriter pw = new java.io.PrintWriter(logFile)) {
                e.printStackTrace(pw);
            } catch (Exception ignored) {}
        }
    }
}