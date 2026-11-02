package com.flycast.emulator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.flycast.emulator.emu.JNIdc;
import java.util.Arrays;

/** Local device-test diagnostics only; never included in a production APK. */
public class AutoSkipDiagnosticsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String[] extras = {"loads", "transitions", "saves"};
        String[] keys = {"GBFrame.SkipLoadingScreens", "GBFrame.SkipTransitions", "GBFrame.SkipSaveWaits"};
        for (int i = 0; i < keys.length; i++) if (intent.hasExtra(extras[i]))
            JNIdc.configSetBool("config", keys[i], intent.getBooleanExtra(extras[i], false));
        setResultCode(0);
        setResultData(Arrays.toString(JNIdc.autoSkipStats()));
    }
}
