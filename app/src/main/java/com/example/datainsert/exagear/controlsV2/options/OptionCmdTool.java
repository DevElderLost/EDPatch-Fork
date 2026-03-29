package com.example.datainsert.exagear.controlsV2.options;

import android.content.Context;

import com.example.datainsert.exagear.controlsV2.Const;
// import class LogTracer (sesuaikan package-nya)
import com.eltechs.ed.guestContainers.Cmd;  // <-- ubah sesuai lokasi sebenarnya dari LogTracer

public class OptionCmdTool extends AbstractOption {

    @Override
    public void run() {
        Context context = Const.getContext();
        if (context != null) {
            Cmd.Cmdtool(context);
        }
    }

    @Override
    public String getName() {
        return "Run Terminal";  // atau gunakan string resource jika ada, misal RR.getS(RR.log_tracer_title)
    }
}