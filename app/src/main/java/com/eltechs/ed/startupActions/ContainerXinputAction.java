package com.eltechs.ed.startupActions;

import com.eltechs.axs.configuration.startup.actions.AbstractStartupAction;
import com.eltechs.ed.WineRegistryEditor;
import com.eltechs.ed.guestContainers.GuestContainer;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/* loaded from: classes.dex */
public class ContainerXinputAction extends AbstractStartupAction {
    public static String XINPUT_OFF = "disabled";
    public static String XINPUT_ON = "enabled";
    private static final HashMap<String, AbstractAction> actionsMap = new HashMap<String, AbstractAction>() { // from class: com.eltechs.ed.startupActions.ContainerStartupAction.1
        {
            put(ContainerXinputAction.XINPUT_OFF, new XinputOffSettings());
            put(ContainerXinputAction.XINPUT_ON, new XinputOnSettings());
        }
    };
    private GuestContainer mCont;
    private String mIdList;

    /* loaded from: classes.dex */
    private interface AbstractAction {
        void run(GuestContainer guestContainer);
    }

    public ContainerXinputAction(GuestContainer guestContainer, String str) {
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
    public static class XinputOffSettings implements AbstractAction {
        @Override // com.eltechs.ed.startupActions.ContainerXinputAction.AbstractAction
        public void run(GuestContainer guestContainer) {
            WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainer.mWinePrefixPath, "user.reg"));
            try {
                wineRegistryEditor.read();
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_1", "");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_2", "");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_3", "");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_4", "");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput9_1_0", "");
                wineRegistryEditor.write();
            } catch (IOException unused) {
            }
        }

        private XinputOffSettings() {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class XinputOnSettings implements AbstractAction {
        private XinputOnSettings() {
        }

        @Override // com.eltechs.ed.startupActions.ContainerXinputAction.AbstractAction
        public void run(GuestContainer guestContainer) {
            WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainer.mWinePrefixPath, "user.reg"));
            try {
                wineRegistryEditor.read();
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_1", "builtin");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_2", "builtin");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_3", "builtin");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput1_4", "builtin");
                wineRegistryEditor.setStringParam("Software\\Wine\\DllOverrides", "xinput9_1_0", "builtin");
                wineRegistryEditor.write();
            } catch (IOException unused) {
            }
        }
    }
}