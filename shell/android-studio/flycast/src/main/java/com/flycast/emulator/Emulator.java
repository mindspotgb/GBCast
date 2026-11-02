package com.flycast.emulator;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.flycast.emulator.emu.JNIdc;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import androidx.appcompat.app.AppCompatDelegate;

import com.flycast.emulator.config.Config;
import com.flycast.emulator.emu.VGamepad;
import com.flycast.emulator.periph.InputDeviceManager;

public class Emulator extends Application {
    private static Context context;
    private static BaseGLActivity currentActivity;
    private WifiManager wifiManager = null;
    private WifiManager.MulticastLock multicastLock = null;

    public static int vibrationPower = 80;
    private boolean coreReady;

    /**
     * Loads the Flycast core and emu.cfg once, so the library can read settings before a game starts.
     * NativeGLActivity calls initEnvironment again; the native side ignores the second initialization.
     * @return an error string, or null when the core is ready
     */
    public synchronized String ensureCore() {
        if (coreReady)
            return null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String home = prefs.getString(Config.pref_home, "");
        if (home == null || home.isEmpty()) {
            File dir = getExternalFilesDir(null);
            if (dir == null)
                dir = getFilesDir();
            home = dir.getAbsolutePath();
            prefs.edit().putString(Config.pref_home, home).apply();
        }
        com.flycast.emulator.shell.GBCastBridge.prepareDataFolder(this, home);
        String result = JNIdc.initEnvironment(this, getFilesDir().getAbsolutePath(), home, Locale.getDefault().toString());
        if (result != null)
            return result;
        getConfigurationPrefs();
        ArrayList<String> paths = new ArrayList<>();
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        paths.add(getFilesDir().getAbsolutePath());
        File ext = getExternalFilesDir(null);
        if (ext != null)
            paths.add(ext.getAbsolutePath());
        JNIdc.setExternalStorageDirectories(paths.toArray());
        coreReady = true;
        return null;
    }

    /**
     * Load the settings from native code
     *
     */
    public void getConfigurationPrefs() {
        Emulator.vibrationPower = VGamepad.getVibrationPower();
    }

    /**
     * Fetch current configuration settings from the emulator and save them
     * Called from JNI code
     */
    public void SaveAndroidSettings(String homeDirectory)
    {
        Log.i("flycast", "SaveAndroidSettings: saving preferences");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Emulator.vibrationPower = VGamepad.getVibrationPower();

        prefs.edit()
                .putString(Config.pref_home, homeDirectory).apply();

        if (InputDeviceManager.isMicPluggedIn() && currentActivity instanceof BaseGLActivity) {
            Log.i("flycast", "SaveAndroidSettings: MIC PLUGGED IN");
            ((BaseGLActivity)currentActivity).requestRecordAudioPermission();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gb.frame.LauncherIconCompatibility.applyFromMetadata(this);
        Emulator.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return Emulator.context;
    }

    public static BaseGLActivity getCurrentActivity() {
        return Emulator.currentActivity;
    }

    public static void setCurrentActivity(BaseGLActivity activity) {
        Emulator.currentActivity = activity;
    }

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    public void enableNetworkBroadcast(boolean enable) {
        if (enable) {
            if (wifiManager == null)
                wifiManager = (WifiManager)Emulator.context.getSystemService(Context.WIFI_SERVICE);
            if (multicastLock == null)
                multicastLock = wifiManager.createMulticastLock("GBCast");
            if (multicastLock != null && !multicastLock.isHeld())
                multicastLock.acquire();
        }
        else
        {
            if (multicastLock != null && multicastLock.isHeld())
                multicastLock.release();
        }
    }
}
