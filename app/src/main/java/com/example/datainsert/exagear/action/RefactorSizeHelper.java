package com.example.datainsert.exagear.action;

import android.util.Log;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.QH;

import java.io.File;

/**
 * "Refactor Size" - toggles borderless-fullscreen for the foreground guest
 * window, using the same SetWindowLong/SetWindowPos technique as
 * Winlator-Ludashi-test's wn-refactor-size helper.
 *
 * v4 (this revision): fixes a second cross-session state leak, found via
 * x86-stderr(-first).txt after v3 shipped. v3's fix ("use a plain static
 * field instead of SharedPreferences, it'll reset itself every session")
 * assumed the Android process gets recreated every time the container is
 * re-entered - true for Winlator's Activity-scoped equivalent field, but
 * NOT true here: EDPatch-Fork's process stays alive across "exit
 * container, re-enter container" (confirmed from the logs -
 * winhandler_mini.exe launches identically both times, so the guest side
 * was always fine - the bug was purely static Java state on the Android
 * side never getting reset). A static field with no explicit reset is
 * exactly as broken as the original SharedPreferences bug, just moved
 * from disk to RAM - same symptom: works once on a fresh container, does
 * nothing after re-entering.
 *
 * Fix: `enabled` and refactorsize.dat cleanup are now reset EXPLICITLY via
 * resetForNewSession(), called from WinHandlerMini.prepareForLaunch() -
 * which itself is called once per container launch from
 * StartGuest.execute() (see the smali patch). That's a reliable "a new
 * session is starting" signal regardless of whether the process is fresh
 * or reused, unlike relying on static field initialization.
 */
public class RefactorSizeHelper {
    private static final String TAG = "RefactorSizeHelper";
    private static final String EXE_GUEST_PATH = "Z:\\opt\\edpatch\\refactorsize.exe";
    private static final long HELPER_EXE_BYTES = 16384L;

    /** Deliberately NOT persisted - but unlike v3, do NOT rely on this
     * merely being a static field to reset itself. It's reset explicitly
     * by resetForNewSession() every container launch. */
    private static volatile boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    /** Called from the options grid item. Flips state and runs the helper. */
    public static void toggle() {
        // Normally a no-op: StartGuest.execute() already launched
        // winhandler_mini.exe eagerly (see WinHandlerMini.prepareForLaunch()/
        // injectIntoCommand()). Only actually spawns anything if that eager
        // path was somehow skipped for this launch.
        WinHandlerMini.get().ensureStartedFallback();

        enabled = !enabled;
        apply(enabled);
    }

    /**
     * Called once per container launch by WinHandlerMini.prepareForLaunch().
     * Resets the on/off flag AND deletes any refactorsize.dat left over
     * from a previous session - that file's saved HWND can never be valid
     * again once the wine/X session that created it is gone, so leaving
     * either of these around invites the exact stale-state bug this
     * revision fixes. Safe/idempotent to call even with nothing to clean up.
     */
    static void resetForNewSession() {
        enabled = false;
        try {
            File dat = new File(QH.Files.edPatchDir(), "refactorsize.dat");
            if (dat.exists() && dat.delete()) {
                Log.i(TAG, "resetForNewSession: removed stale refactorsize.dat from a previous session");
            }
        } catch (Exception e) {
            Log.e(TAG, "resetForNewSession: failed to clear stale state", e);
        }
    }

    private static void apply(boolean enabled) {
        stageHelperExe();
        WinHandlerMini.get().exec(EXE_GUEST_PATH, enabled ? "on" : "off");
    }

    /**
     * Copies refactorsize.exe from assets into <exagear image>/opt/edpatch,
     * same convention FuncFAB already uses for pulseaudio/renderer files.
     */
    private static void stageHelperExe() {
        try {
            File dst = new File(QH.Files.edPatchDir(), "refactorsize.exe");
            if (dst.exists() && dst.length() == HELPER_EXE_BYTES) return;

            try (java.io.InputStream in = Globals.getAppContext().getAssets().open("refactorsize/refactorsize.exe");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setExecutable(true, false);
            Log.i(TAG, "stageHelperExe: staged refactorsize.exe (" + dst.length() + " B) at " + dst.getPath());
        } catch (Exception e) {
            Log.e(TAG, "stageHelperExe: helper staging failed", e);
        }
    }
}
