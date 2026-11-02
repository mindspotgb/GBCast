package com.flycast.emulator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.flycast.emulator.config.Config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class RetroAchievementsHostOverrideReceiverTest {
    private static final String ACTION_SET =
            "com.flycast.emulator.action.SET_RETROACHIEVEMENTS_HOST_OVERRIDE";
    private static final String ACTION_CLEAR =
            "com.flycast.emulator.action.CLEAR_RETROACHIEVEMENTS_HOST_OVERRIDE";
    private static final String EXTRA_HOST = "host";
    private static final String TEST_HOST = "http://127.0.0.1:18432";

    private Context context;
    private SharedPreferences preferences;
    private String originalHome;
    private File testHome;
    private File cfgFile;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        originalHome = preferences.getString(Config.pref_home, "");
        testHome = new File(context.getCacheDir(), "ra-host-override-receiver-test");
        if (!testHome.exists() && !testHome.mkdirs()) {
            throw new AssertionError("Could not create temporary config directory");
        }
        preferences.edit().putString(Config.pref_home, testHome.getAbsolutePath()).commit();
        cfgFile = new File(testHome, "emu.cfg");
        Files.write(cfgFile.toPath(), (
                "[general]\nKeepMe = yes\n\n" +
                "[achievements]\nUsername = test-user\nHostUrl = http://127.0.0.1:8080\n\n" +
                "[video]\nKeepVideoSetting = true\n"
        ).getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        preferences.edit().putString(Config.pref_home, originalHome).commit();
        if (cfgFile != null) cfgFile.delete();
        if (testHome != null) testHome.delete();
    }

    @Test
    public void setBroadcastPersistsHostUrlAndPreservesOtherSettings() throws Exception {
        send(ACTION_SET, TEST_HOST);
        String cfg = readAfterHostState(TEST_HOST);

        assertTrue(cfg.contains("[achievements]"));
        assertTrue(cfg.contains("HostUrl = " + TEST_HOST));
        assertTrue(cfg.contains("Username = test-user"));
        assertTrue(cfg.contains("KeepMe = yes"));
        assertTrue(cfg.contains("KeepVideoSetting = true"));
        assertFalse(cfg.contains("HostUrl = http://127.0.0.1:8080"));
    }

    @Test
    public void clearBroadcastRemovesHostUrlButPreservesOtherSettings() throws Exception {
        send(ACTION_CLEAR, null);
        String cfg = readAfterHostState(null);

        assertFalse(cfg.contains("HostUrl ="));
        assertTrue(cfg.contains("[achievements]"));
        assertTrue(cfg.contains("Username = test-user"));
        assertTrue(cfg.contains("KeepMe = yes"));
        assertTrue(cfg.contains("KeepVideoSetting = true"));
    }

    @Test
    public void invalidSetBroadcastDoesNotOverwritePersistedHostUrl() throws Exception {
        send(ACTION_SET, "https://proxy.example.test/api");
        String cfg = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(cfg.contains("HostUrl = http://127.0.0.1:8080"));
        assertFalse(cfg.contains("HostUrl = https://proxy.example.test/api"));
    }

    private void send(String action, String host) {
        Intent intent = new Intent(action)
                .setComponent(new ComponentName(context, RetroAchievementsHostOverrideReceiver.class));
        if (host != null) intent.putExtra(EXTRA_HOST, host);
        context.sendBroadcast(intent);
    }

    private String readAfterHostState(String expectedHost) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3000;
        String cfg = "";
        do {
            cfg = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);
            boolean reached = expectedHost == null
                    ? !cfg.contains("HostUrl =")
                    : cfg.contains("HostUrl = " + expectedHost);
            if (reached) return cfg;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Timed out waiting for emu.cfg to reach expected HostUrl state. Contents:\n" + cfg);
    }
}
