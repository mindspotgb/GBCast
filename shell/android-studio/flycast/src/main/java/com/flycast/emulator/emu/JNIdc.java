package com.flycast.emulator.emu;

import android.view.Surface;

import com.flycast.emulator.Emulator;
import com.flycast.emulator.periph.SipEmulator;

import gb.frame.RetroAchievements;

public final class JNIdc
{
    public static native void initAutoSkip();
    public static native long[] autoSkipStats();

	static { System.loadLibrary("flycast"); }

	public static native String initEnvironment(Emulator emulator, String filesDirectory, String homeDirectory, String locale);
	public static native void setExternalStorageDirectories(Object[] pathList);
	public static native void setGameUri(String fileName);
	public static native void pause();
	public static native void resume();
	public static native void stop();
	public static native void disableOmpAffinity();

	public static native void rendinitNative(Surface surface, int w, int h);

	public static native void setupMic(SipEmulator sip);

	public static native void screenCharacteristics(float screenDpi, float refreshRate);
	public static native void guiOpenSettings();
	public static native boolean guiIsOpen();
	public static native boolean guiIsContentBrowser();
	public static native void guiSetInsets(int left, int right, int top, int bottom);
	public static native void postRestore(String filesDir);

	// GBCast bridge. Keys are Flycast config section + option name (see core/cfg/option.cpp).
	public static native boolean configGetBool(String section, String name);
	public static native boolean configSetBool(String section, String name, boolean value);
	public static native int configGetInt(String section, String name);
	public static native boolean configSetInt(String section, String name, int value);
	public static native void configSave();
	public static native boolean hasPerGameConfig();
	public static native void setPerGameConfig(boolean enabled);
	/** Queue a measured GBFrame profile for the next game boot; -1 means leave an option unchanged. */
	public static native void setRecommendedProfile(boolean enabled, int renderer, int resolution, int autoSkip,
			int dynarec, int threadedRendering, int textureFiltering, int fastCdLoad, int cdReadSpeed);
	public static native String dataDirectory();
	public static native String currentGamePath();
	public static native boolean hasWidescreenHack();
	public static native int guiState();
	public static native void guiSetState(int state);
	public static native void guiTogglePause();
	public static native void restartGame();
	public static native void saveCurrentSlot();
	public static native String saveSlotWithPreview(int slot);
	public static native int[] overlayStats();
	public static native String overlayGpuName();
	public static native String pollOverlayMessage();
	public static native String raOverlayEvents();
	public static native byte[] stateThumbnail(int slot);
	public static native long stateTimestamp(int slot);
	public static native void setFastForwardMode(boolean enabled);
	public static native boolean loadCurrentSlot();
	public static native boolean loadStateSlot(int slot);
	public static native String insertDisk(String path);
	public static native int cheatCount();
	/** Appends validated .cht RAM operations, disabled, preserving existing and built-in cheats. */
	public static native int importCheatFile(String path);
	public static native String cheatDescription(int index);
	public static native boolean cheatEnabled(int index);
	public static native boolean cheatBuiltIn(int index);
	public static native boolean setCheatEnabled(int index, boolean enabled);
	public static native void removeCheat(int index);
	/** Returns null on success or an error message when Flycast rejects the code. */
	public static native String addGameSharkCheat(String name, String code);

	// Shared GBFrame RetroAchievements adapter. The core owns authentication and game recognition;
	// HTTP is routed through GBFrame so its offline cache/queue is shared by every GB app.
	public static native boolean raInit(String userAgent);
	public static native void raLogin(String username, String password);
	public static native void raLoginWithToken(String username, String token);
	public static native void raLogout();
	public static native void raSetHardcore(boolean enabled);
	public static native boolean raIsLoggedOn();
	public static native boolean raIsActive();
	public static native boolean raIsLoginPending();
	public static native boolean raIsHardcore();
	public static native String raLoginError();
	/** Tab-separated username, token, avatar URL, hardcore score, softcore score. */
	public static native String raCurrentUser();
	/** Tab-separated title, unlocked, total, points, total points, badge URL, hardcore. */
	public static native String raCurrentGame();
	/** GBFrame's summary row and achievement rows, tab-separated. */
	public static native String raAchievements();
	public static native void raHttpResponse(int id, int status, byte[] body);

	/** Called by native rcheevos; completion returns asynchronously through raHttpResponse. */
	public static void raHttp(int id, String url, String post, String contentType, String userAgent) {
		RetroAchievements.INSTANCE.httpRequest(id, url, post, contentType, userAgent);
	}
}
