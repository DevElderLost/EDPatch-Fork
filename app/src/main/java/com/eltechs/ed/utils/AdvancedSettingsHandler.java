package com.eltechs.ed.utils;

import com.eltechs.axs.Globals;

public class AdvancedSettingsHandler {

public static int dpToPx(int i) {
        int i2 = i;
        return (int) ((((float) i2) * Globals.getAppContext().getResources().getDisplayMetrics().density) + 0.5f);
    }
}
