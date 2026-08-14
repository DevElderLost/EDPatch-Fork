package com.example.datainsert.exagear.action;

import android.content.SharedPreferences;
import android.util.Log;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * "Refactor Size" - toggles borderless-fullscreen for the foreground guest
 * window, using the same SetWindowLong/SetWindowPos technique as
 * Winlator-Ludashi-test's wn-refactor-size helper.
 *
 * v2 design (after two rounds of on-device testing):
 *  - v1 launched refactorsize.exe as a one-shot process via
 *    GuestApplicationsTrackerComponent.startGuestApplication() every time
 *    the menu item was tapped, same as OptionTaskMgr does for taskmgr.
 *    That failed: it always spawns a brand new UBT/wine process, and
 *    unless launched into the exact same named Wine virtual desktop the
 *    game uses ("explorer /desktop=shell,WxH ..." from StartGuest.java),
 *    GetForegroundWindow() in the new process can never see the game's
 *    window at all.
 *  - v2: refactorsize.exe is now started ONCE, automatically, bundled
 *    into the SAME launch as the main exe via CreateLaunchConfiguration
 *    (so it's part of the same script -> same UBT process -> same
 *    "explorer /desktop=shell" desktop as the game, no separate spawn,
 *    no desktop-mismatch risk). It idles there polling a tiny flag file.
 *    This class's only job now is to stage the exe (so
 *    CreateLaunchConfiguration has something to launch) and flip that
 *    flag file - no UBTLaunchConfiguration/GuestApplicationsTracker calls
 *    needed here anymore, no new process spawned on toggle at all.
 *
 * The flag/state files live at <exagear image>/opt/edpatch/, the same
 * path the running refactorsize.exe reads via its Z: drive mapping
 * (Z:\opt\edpatch\... - see CreateLaunchConfiguration's z: symlink to
 * the image root), so writing directly to the Android-side file is
 * enough to signal the guest daemon - no IPC needed.
 */
public class RefactorSizeHelper {
    private static final String TAG = "RefactorSizeHelper";
    private static final String PREF_KEY = "refactor_size_enabled";
    private static final long HELPER_EXE_BYTES = 16896L;
    public static final String EXE_NAME = "refactorsize.exe";
    private static final String FLAG_FILE_NAME = "refactorsize.flag";

    public static boolean isEnabled() {
        return QH.getPreference().getBoolean(PREF_KEY, false);
    }

    /** Called from the options grid item. Flips state and writes the flag file. */
    public static void toggle() {
        boolean newState = !isEnabled();
        writeFlag(newState);
        SharedPreferences.Editor editor = QH.getPreference().edit();
        editor.putBoolean(PREF_KEY, newState);
        editor.apply();
    }

    private static void writeFlag(boolean enabled) {
        try {
            File f = new File(QH.Files.edPatchDir(), FLAG_FILE_NAME);
            try (java.io.FileWriter fw = new java.io.FileWriter(f, false)) {
                fw.write(enabled ? "1" : "0");
            }
        } catch (Exception e) {
            Log.e(TAG, "writeFlag: failed to write flag file", e);
        }
    }

    /**
     * Copies refactorsize.exe from assets into <exagear image>/opt/edpatch,
     * same convention FuncFAB already uses for pulseaudio/renderer files.
     * Called both from the menu item's first use (so toggling before the
     * next container restart still has the exe in place) and from
     * CreateLaunchConfiguration on every launch (so the daemon is always
     * present to be started with the main exe).
     */
    public static void stageHelperExe() {
        try {
            File dst = new File(QH.Files.edPatchDir(), EXE_NAME);
            if (dst.exists() && dst.length() == HELPER_EXE_BYTES) return;

            try (InputStream in = Globals.getAppContext().getAssets().open("refactorsize/refactorsize.exe");
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setExecutable(true, false);
            Log.i(TAG, "stageHelperExe: staged " + EXE_NAME + " (" + dst.length() + " B) at " + dst.getPath());
        } catch (Exception e) {
            Log.e(TAG, "stageHelperExe: helper staging failed", e);
        }
    }
}
