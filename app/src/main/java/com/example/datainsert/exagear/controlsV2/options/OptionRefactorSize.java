package com.example.datainsert.exagear.controlsV2.options;

import com.example.datainsert.exagear.RR;
import com.example.datainsert.exagear.action.RefactorSizeHelper;

/**
 * Grid-icon option (shown from OptionShowAllOptions' AlertDialog grid) that
 * toggles borderless-fullscreen for the foreground guest window - same
 * technique as Winlator-Ludashi-test's "Refactor Size" feature.
 *
 * Name deliberately contains "fullscreen" so OptionShowAllOptions.createGridItem()'s
 * name.contains("fullscreen") branch picks it up automatically and it gets
 * the same icon as OptionToggleFullScreen, no icon-matching changes needed.
 */
public class OptionRefactorSize extends AbstractOption {
    @Override
    public void run() {
        RefactorSizeHelper.toggle();
    }

    @Override
    public String getName() {
        return RR.getS(RR.ctr2_option_refactorsize);
    }
}
