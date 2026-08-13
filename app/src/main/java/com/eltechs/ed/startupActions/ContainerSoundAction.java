package com.eltechs.ed.startupActions;

import com.eltechs.axs.configuration.startup.actions.AbstractStartupAction;
import com.eltechs.ed.WineRegistryEditor;
import com.eltechs.ed.guestContainers.GuestContainer;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/* loaded from: classes.dex */
public class ContainerSoundAction extends AbstractStartupAction {
    public static String ID_OFF = "off";
    public static String ID_ALSA = "alsa";
    public static String ID_PULSE = "pulse";
    private static final HashMap<String, AbstractAction> actionsMap = new HashMap<String, AbstractAction>() { // from class: com.eltechs.ed.startupActions.ContainerStartupAction.1
        {
            put(ContainerSoundAction.ID_OFF, new OffSettings());
            put(ContainerSoundAction.ID_ALSA, new AlsaSettings());
            put(ContainerSoundAction.ID_PULSE, new PulseSettings());
        }
    };
    private GuestContainer mCont;
    private String mIdList;

    /* loaded from: classes.dex */
    private interface AbstractAction {
        void run(GuestContainer guestContainer);
    }

    public ContainerSoundAction(GuestContainer guestContainer, String str) {
        this.mCont = guestContainer;
        this.mIdList = str;
    }

    @Override // com.eltechs.axs.configuration.startup.StartupAction
    public void execute() {
        String[] split;
        for (String str : this.mIdList.split(" ")) {
            if (actionsMap.containsKey(str)) {
                actionsMap.get(str).run(this.mCont);
            }
        }
        sendDone();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class OffSettings implements AbstractAction {
        @Override // com.eltechs.ed.startupActions.ContainerStartupAction.AbstractAction
        public void run(GuestContainer guestContainer) {
            WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainer.mWinePrefixPath, "user.reg"));
            try {
                wineRegistryEditor.read();
                wineRegistryEditor.setStringParam("Software\\Wine\\Drivers", "Audio", "off");
                wineRegistryEditor.write();
            } catch (IOException unused) {
            }
        }

        private OffSettings() {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class AlsaSettings implements AbstractAction {
        private AlsaSettings() {
        }

        @Override // com.eltechs.ed.startupActions.ContainerStartupAction.AbstractAction
        public void run(GuestContainer guestContainer) {
            WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainer.mWinePrefixPath, "user.reg"));
            try {
                wineRegistryEditor.read();
                wineRegistryEditor.setStringParam("Software\\Wine\\Drivers", "Audio", "alsa");
                wineRegistryEditor.write();
            } catch (IOException unused) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class PulseSettings implements AbstractAction {
        private PulseSettings() {
        }

        @Override // com.eltechs.ed.startupActions.ContainerStartupAction.AbstractAction
        public void run(GuestContainer guestContainer) {
            WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainer.mWinePrefixPath, "user.reg"));
            try {
                wineRegistryEditor.read();
                wineRegistryEditor.setStringParam("Software\\Wine\\Drivers", "Audio", "pulse");
                wineRegistryEditor.write();
            } catch (IOException unused) {
            }
        }
    }
}