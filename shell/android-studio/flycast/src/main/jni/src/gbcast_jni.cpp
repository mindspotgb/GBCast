#include "gbcast_skip.h"
#include "gbcast_profile.h"
#include <jni.h>

#include "cfg/option.h"
#include "emulator.h"
#include "cheats.h"
#include "achievements/achievements.h"
#include "imgread/common.h"
#include "stdclass.h"
#include "ui/gui.h"
#include "jni_util.h"
#include "hw/pvr/Renderer_if.h"
#include "hw/pvr/pvr_regs.h"
#include "rend/transform_matrix.h"
#include "rend/gpu_usage.h"
#include "wsi/context.h"
#include "gl_renderer.hpp"

#include <algorithm>
#include <atomic>
#include <climits>
#include <mutex>
#include <string>
#include <sstream>
#include <unordered_map>
#include <future>
#include <chrono>

// GBCast reads and writes Flycast's real config::Option objects. Unknown keys are ignored.

namespace {

#ifdef USE_RACHIEVEMENTS
static std::atomic_int raHttpNextId { 1 };
static std::mutex raHttpMutex;
static std::unordered_map<int, achievements::HttpResponse> raHttpPending;
static jclass raBridgeClass;
static jmethodID raHttpMethod;
static std::string raUserAgent;

static jstring nullableJString(JNIEnv *env, const std::string *value) {
	return value != nullptr ? env->NewStringUTF(value->c_str()) : nullptr;
}

static bool dispatchRaHttp(const std::string& url, const std::string *postData,
		const std::string *contentType, achievements::HttpResponse response) {
	JNIEnv *env = jni::env();
	if (env == nullptr || raBridgeClass == nullptr || raHttpMethod == nullptr)
		return false;

	const int id = raHttpNextId.fetch_add(1);
	{
		std::lock_guard<std::mutex> lock(raHttpMutex);
		raHttpPending.emplace(id, std::move(response));
	}
	jstring jurl = env->NewStringUTF(url.c_str());
	jstring jpost = nullableJString(env, postData);
	jstring jcontentType = nullableJString(env, contentType);
	jstring jagent = env->NewStringUTF(raUserAgent.c_str());
	env->CallStaticVoidMethod(raBridgeClass, raHttpMethod, id, jurl, jpost, jcontentType, jagent);
	env->DeleteLocalRef(jurl);
	if (jpost != nullptr)
		env->DeleteLocalRef(jpost);
	if (jcontentType != nullptr)
		env->DeleteLocalRef(jcontentType);
	env->DeleteLocalRef(jagent);
	if (!env->ExceptionCheck())
		return true;

	env->ExceptionClear();
	std::lock_guard<std::mutex> lock(raHttpMutex);
	raHttpPending.erase(id);
	return false;
}

static bool installRaHttpBridge(JNIEnv *env, const std::string& userAgent) {
	raUserAgent = userAgent;
	if (raBridgeClass == nullptr) {
		jclass localClass = env->FindClass("com/flycast/emulator/emu/JNIdc");
		if (localClass == nullptr)
			return false;
		raBridgeClass = static_cast<jclass>(env->NewGlobalRef(localClass));
		env->DeleteLocalRef(localClass);
	}
	if (raHttpMethod == nullptr)
		raHttpMethod = env->GetStaticMethodID(raBridgeClass, "raHttp",
				"(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
		raHttpMethod = nullptr;
	}
	if (raHttpMethod == nullptr)
		return false;
	achievements::setHttpRequestHandler(dispatchRaHttp);
	return true;
}
#endif

struct BoolOpt {
	const char *section;
	const char *name;
	bool (*get)();
	void (*set)(bool);
};

struct IntOpt {
	const char *section;
	const char *name;
	int (*get)();
	void (*set)(int);
};

static bool getWide() { return config::Widescreen; }
static bool getStretchToWindow() { return config::StretchToWindow; }
static void setStretchToWindow(bool v) { config::StretchToWindow = v; }
static void setWide(bool v) { config::Widescreen = v; }
static bool getSuperWide() { return config::SuperWidescreen; }
static void setSuperWide(bool v) { config::SuperWidescreen = v; }
static bool getFps() { return config::ShowFPS; }
static void setFps(bool v) { config::ShowFPS = v; }
static bool getVsync() { return config::VSync; }
static void setVsync(bool v) { config::VSync = v; }
static bool getInteger() { return config::IntegerScale; }
static void setInteger(bool v) { config::IntegerScale = v; }
static bool getThreaded() { return config::ThreadedRendering; }
static void setThreaded(bool v) { config::ThreadedRendering = v; }
static bool getPacing() { return config::FramePacing; }
static void setPacing(bool v) { config::FramePacing = v; }
static bool getFog() { return config::Fog; }
static bool getCustomTextures() { return config::CustomTextures; }
static void setCustomTextures(bool v) { config::CustomTextures = v; }
static void setFog(bool v) { config::Fog = v; }
static bool getMip() { return config::UseMipmaps; }
static void setMip(bool v) { config::UseMipmaps = v; }
static bool getDelay() { return config::DelayFrameSwapping; }
static void setDelay(bool v) { config::DelayFrameSwapping = v; }
static bool getHacks() { return config::WidescreenGameHacks; }
static void setHacks(bool v) {
	config::WidescreenGameHacks = v;
	cheatManager.reset(settings.content.gameId);
}
static bool getRotate() { return config::Rotate90; }
static void setRotate(bool v) { config::Rotate90 = v; }
static bool getLinear() { return config::LinearInterpolation; }
static void setLinear(bool v) { config::LinearInterpolation = v; }
static bool getStrips() { return config::PerStripSorting; }
static void setStrips(bool v) { config::PerStripSorting = v; }
static bool getFb() { return config::EmulateFramebuffer; }
static void setFb(bool v) { config::EmulateFramebuffer = v; }
static bool getDsp() { return config::DSPEnabled; }
static void setDsp(bool v) { config::DSPEnabled = v; }
static bool getLatency() { return config::AutoLatency; }
static void setLatency(bool v) { config::AutoLatency = v; }
static bool getVmu() { return config::VmuSound; }
static void setVmu(bool v) { config::VmuSound = v; }
static bool getDynarec() { return config::DynarecEnabled; }
static void setDynarec(bool v) { config::DynarecEnabled = v; }
static bool getAutoLoad() { return config::AutoLoadState; }
static void setAutoLoad(bool v) { config::AutoLoadState = v; }
static bool getAutoSave() { return config::AutoSaveState; }
static void setAutoSave(bool v) { config::AutoSaveState = v; }
static bool getFreePlay() { return config::ForceFreePlay; }
static void setFreePlay(bool v) { config::ForceFreePlay = v; }
static bool getSkipLoads() { return gbcast::SkipLoads(); }
static bool getSkipTransitions() { return gbcast::SkipTransitions(); }
static bool getSkipSaves() { return gbcast::SkipSaves(); }
static void setSkipLoads(bool v) { gbcast::SetSkipLoads(v); }
static void setSkipTransitions(bool v) { gbcast::SetSkipTransitions(v); }
static void setSkipSaves(bool v) { gbcast::SetSkipSaves(v); }
static bool getFastBoot() { return config::FastBoot; }
static void setFastBoot(bool v) { config::FastBoot = v; }
static bool getFastGd() { return config::FastGDRomLoad; }
static void setFastGd(bool v) { config::FastGDRomLoad = v; }
static bool getRam() { return config::RamMod32MB; }
static void setRam(bool v) { config::RamMod32MB = v; }
static bool getReios() { return config::UseReios; }
static void setReios(bool v) { config::UseReios = v; }
static bool getNet() { return config::NetworkEnable; }
static void setNet(bool v) { config::NetworkEnable = v; }
static bool getBba() { return config::EmulateBBA; }
static void setBba(bool v) { config::EmulateBBA = v; }
static bool getGgpo() { return config::GGPOEnable; }
static void setGgpo(bool v) { config::GGPOEnable = v; }
static bool getBattle() { return config::BattleCableEnable; }
static void setBattle(bool v) { config::BattleCableEnable = v; }
static bool getRa() { return config::EnableAchievements; }
static void setRa(bool v) { config::EnableAchievements = v; }
static bool getHardcore() { return config::AchievementsHardcoreMode; }
static void setHardcore(bool v) { config::AchievementsHardcoreMode = v; }
static bool getCrash() { return config::UploadCrashLogs; }
static void setCrash(bool v) { config::UploadCrashLogs = v; }

static const BoolOpt kBools[] = {
    {"config", "rend.CustomTextures", getCustomTextures, setCustomTextures},
	{"config", "rend.StretchToWindow", getStretchToWindow, setStretchToWindow},
	{"config", "rend.WideScreen", getWide, setWide},
	{"config", "rend.SuperWideScreen", getSuperWide, setSuperWide},
	{"config", "rend.ShowFPS", getFps, setFps},
	{"config", "rend.vsync", getVsync, setVsync},
	{"config", "rend.IntegerScale", getInteger, setInteger},
	{"config", "rend.ThreadedRendering", getThreaded, setThreaded},
	{"config", "rend.FramePacing", getPacing, setPacing},
	{"config", "rend.Fog", getFog, setFog},
	{"config", "rend.UseMipmaps", getMip, setMip},
	{"config", "rend.DelayFrameSwapping", getDelay, setDelay},
	{"config", "rend.WidescreenGameHacks", getHacks, setHacks},
	{"config", "rend.Rotate90", getRotate, setRotate},
	{"config", "rend.LinearInterpolation", getLinear, setLinear},
	{"config", "rend.PerStripSorting", getStrips, setStrips},
	{"config", "rend.EmulateFramebuffer", getFb, setFb},
	{"config", "aica.DSPEnabled", getDsp, setDsp},
	{"config", "aica.AutoLatency", getLatency, setLatency},
	{"audio", "VmuSound", getVmu, setVmu},
	{"config", "Dynarec.Enabled", getDynarec, setDynarec},
	{"config", "Dreamcast.AutoLoadState", getAutoLoad, setAutoLoad},
	{"config", "Dreamcast.AutoSaveState", getAutoSave, setAutoSave},
	{"config", "ForceFreePlay", getFreePlay, setFreePlay},
	{"config", "GBFrame.FastBoot", getFastBoot, setFastBoot},
	{"config", "FastGDRomLoad", getFastGd, setFastGd},
	{"config", "GBFrame.SkipLoadingScreens", getSkipLoads, setSkipLoads},
	{"config", "GBFrame.SkipTransitions", getSkipTransitions, setSkipTransitions},
	{"config", "GBFrame.SkipSaveWaits", getSkipSaves, setSkipSaves},
	{"config", "Dreamcast.RamMod32MB", getRam, setRam},
	{"config", "UseReios", getReios, setReios},
	{"network", "Enable", getNet, setNet},
	{"network", "EmulateBBA", getBba, setBba},
	{"network", "GGPO", getGgpo, setGgpo},
	{"network", "BattleCable", getBattle, setBattle},
	{"achievements", "Enabled", getRa, setRa},
	{"achievements", "HardcoreMode", getHardcore, setHardcore},
	{"config", "UploadCrashLogs", getCrash, setCrash},
};

static int getRes() { return config::RenderResolution; }
static void setRes(int v) { config::RenderResolution = v; }
static int getFilter() { return config::TextureFiltering; }
static void setFilter(int v) { config::TextureFiltering = v; }
static int getAniso() { return config::AnisotropicFiltering; }
static void setAniso(int v) { config::AnisotropicFiltering = v; }
static int getRenderer() { return (int)(RenderType)config::RendererType; }
static void setRenderer(int v) { config::RendererType = (RenderType)v; }
static int getCable() { return config::Cable; }
static void setCable(int v) { config::Cable = v; }
static int getRegion() { return config::Region; }
static void setRegion(int v) { config::Region = v; }
static int getBroadcast() { return config::Broadcast; }
static void setBroadcast(int v) { config::Broadcast = v; }
static int getLanguage() { return config::Language; }
static void setLanguage(int v) { config::Language = v; }
static int getAudio() { return config::AudioBufferSize; }
static void setAudio(int v) { config::AudioBufferSize = v; }
// The volume's dB scale is only recomputed on load(): do it here so a change is heard at once
static int getVolume() { return config::AudioVolume; }
static void setVolume(int v) { config::AudioVolume.set(std::clamp(v, 0, 100)); config::AudioVolume.calcDbPower(); }
static int getFfAudio() { return config::FastForwardAudio; }
static void setFfAudio(int v) { config::FastForwardAudio = std::clamp(v, 0, 2); }
static int getPad() { return (int)(MapleDeviceType)config::MapleMainDevices[0]; }
static void setPad(int v) { config::MapleMainDevices[0] = (MapleDeviceType)v; }
static int getVibe() { return config::VirtualGamepadVibration; }
static void setVibe(int v) { config::VirtualGamepadVibration = v; }
static int getSlot() { return config::SavestateSlot; }
static void setSlot(int v) { config::SavestateSlot = v < 0 || v >= 5 ? 0 : v; }
static int getSkip() { return config::SkipFrame; }
static void setSkip(int v) { config::SkipFrame = v; }
static int getAutoSkip() { return config::AutoSkipFrame; }
static void setAutoSkip(int v) { config::AutoSkipFrame = v; }
static int getCdSpeed() { return config::CdReadSpeed; }
static void setCdSpeed(int v) { config::CdReadSpeed = v == 4 || v == 8 || v == 16 ? v : 2; }
static int getPresenterShader() { return config::PresenterShader; }
static void setPresenterShader(int v) { config::PresenterShader = v; }

static int getScreenStretching() { return config::ScreenStretching; }
static void setScreenStretching(int v) { config::ScreenStretching = std::clamp(v, 100, 250); }

static const IntOpt kInts[] = {
	{"config", "rend.ScreenStretching", getScreenStretching, setScreenStretching},
	{"config", "rend.Resolution", getRes, setRes},
	{"config", "rend.TextureFiltering", getFilter, setFilter},
	{"config", "rend.AnisotropicFiltering", getAniso, setAniso},
	{"config", "pvr.rend", getRenderer, setRenderer},
	{"config", "Dreamcast.Cable", getCable, setCable},
	{"config", "Dreamcast.Region", getRegion, setRegion},
	{"config", "Dreamcast.Broadcast", getBroadcast, setBroadcast},
	{"config", "Dreamcast.Language", getLanguage, setLanguage},
	{"config", "aica.BufferSize", getAudio, setAudio},
	{"config", "aica.Volume", getVolume, setVolume},
	{"config", "aica.FastForwardAudio", getFfAudio, setFfAudio},
	{"input", "device1", getPad, setPad},
	{"input", "VirtualGamepadVibration", getVibe, setVibe},
	{"config", "Dreamcast.SavestateSlot", getSlot, setSlot},
	{"config", "ta.skip", getSkip, setSkip},
	{"config", "pvr.AutoSkipFrame", getAutoSkip, setAutoSkip},
	{"config", "Dreamcast.CdReadSpeed", getCdSpeed, setCdSpeed},
	{"config", "GBCast.Shader", getPresenterShader, setPresenterShader},
};

static bool same(const char *a, const std::string &b) { return b == a; }

static const BoolOpt *findBool(const std::string &section, const std::string &name) {
	for (const auto &opt : kBools)
		if (same(opt.section, section) && same(opt.name, name))
			return &opt;
	return nullptr;
}

static const IntOpt *findInt(const std::string &section, const std::string &name) {
	for (const auto &opt : kInts)
		if (same(opt.section, section) && same(opt.name, name))
			return &opt;
	return nullptr;
}

static std::string jstr(JNIEnv *env, jstring value) {
	if (value == nullptr)
		return {};
	const char *chars = env->GetStringUTFChars(value, nullptr);
	std::string out = chars != nullptr ? chars : "";
	if (chars != nullptr)
		env->ReleaseStringUTFChars(value, chars);
	return out;
}

static std::string cleanTabField(std::string value) {
	for (char &c : value)
		if (c == '\t' || c == '\r' || c == '\n')
			c = ' ';
	return value;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_configGetBool(JNIEnv *env, jclass, jstring section, jstring name) {
	const BoolOpt *opt = findBool(jstr(env, section), jstr(env, name));
	return opt != nullptr && opt->get();
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_configSetBool(JNIEnv *env, jclass, jstring section, jstring name, jboolean value) {
	const BoolOpt *opt = findBool(jstr(env, section), jstr(env, name));
	if (opt == nullptr)
		return JNI_FALSE;
	opt->set(value == JNI_TRUE);
	SaveSettings();
	return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL Java_com_flycast_emulator_emu_JNIdc_configGetInt(JNIEnv *env, jclass, jstring section, jstring name) {
	const IntOpt *opt = findInt(jstr(env, section), jstr(env, name));
	return opt != nullptr ? opt->get() : INT32_MIN;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_configSetInt(JNIEnv *env, jclass, jstring section, jstring name, jint value) {
	const IntOpt *opt = findInt(jstr(env, section), jstr(env, name));
	if (opt == nullptr)
		return JNI_FALSE;
	opt->set(value);
	SaveSettings();
	return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_configSave(JNIEnv *, jclass) {
	SaveSettings();
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_hasPerGameConfig(JNIEnv *, jclass) {
	return config::Settings::instance().hasPerGameConfig();
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_setPerGameConfig(JNIEnv *, jclass, jboolean enabled) {
	config::Settings::instance().setPerGameConfig(enabled == JNI_TRUE);
	SaveSettings();
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_setRecommendedProfile(
		JNIEnv *, jclass, jboolean enabled, jint renderer, jint resolution, jint autoSkip,
		jint threadedRendering, jint textureFiltering, jint framePacing, jint fastCdLoad, jint cdReadSpeed) {
	gbcast::setRecommendedProfile(enabled == JNI_TRUE, renderer, resolution, autoSkip,
		threadedRendering, textureFiltering, framePacing, fastCdLoad, cdReadSpeed);
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_dataDirectory(JNIEnv *env, jclass) {
	return env->NewStringUTF(get_writable_data_path("").c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_currentGamePath(JNIEnv *env, jclass) {
	return env->NewStringUTF(settings.content.path.c_str());
}


extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_hasWidescreenHack(JNIEnv *, jclass) {
	return cheatManager.hasWidescreenHack() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL Java_com_flycast_emulator_emu_JNIdc_guiState(JNIEnv *, jclass) {
	return (jint)gui_state;
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_guiSetState(JNIEnv *, jclass, jint state) {
	gui_setState((GuiState)state);
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_guiTogglePause(JNIEnv *, jclass) {
	gui_togglePause();
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_setFastForwardMode(JNIEnv *, jclass, jboolean enabled) {
    settings.input.fastForwardMode = enabled == JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_saveCurrentSlot(JNIEnv *, jclass) {
	const int slot = config::SavestateSlot;
	gui_runOnUiThread([slot]() {
		const int previous = config::SavestateSlot;
		config::SavestateSlot = slot;
		gui_saveState(gui_state == GuiState::Closed, false);
		config::SavestateSlot = previous;
	});
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_saveSlotWithPreview(JNIEnv *env, jclass, jint slot) {
	if (slot < 0 || slot > 5)
		return env->NewStringUTF("Invalid save slot");
	auto completion = std::make_shared<std::promise<std::string>>();
	auto result = completion->get_future();
	// Renderer capture must run on the thread owning the GL/Vulkan context, never the Android UI thread.
	gui_runOnUiThread([slot, completion]() {
		if (!dc_savestateAllowed() || settings.raHardcoreMode) {
			completion->set_value("Save states are unavailable for this session");
			return;
		}
		const int previous = config::SavestateSlot;
		try {
			config::SavestateSlot = slot;
			gui_saveState(gui_state == GuiState::Closed, false);
			std::vector<u8> preview;
			dc_getStateScreenshot(slot, preview);
			completion->set_value(preview.empty() ? "State saved, but a screenshot could not be captured" : "");
		} catch (const std::exception& error) {
			completion->set_value(error.what());
		}
		config::SavestateSlot = previous;
	});
	if (result.wait_for(std::chrono::seconds(15)) != std::future_status::ready)
		return env->NewStringUTF("Saving is taking longer than expected");
	const std::string message = result.get();
	return message.empty() ? nullptr : env->NewStringUTF(message.c_str());
}

extern "C" JNIEXPORT jlong JNICALL Java_com_flycast_emulator_emu_JNIdc_stateTimestamp(JNIEnv *, jclass, jint slot) {
	if (slot < 0 || slot > 5) return 0;
	return static_cast<jlong>(dc_getStateCreationDate(slot)) * 1000;
}

extern "C" JNIEXPORT jintArray JNICALL Java_com_flycast_emulator_emu_JNIdc_overlayStats(JNIEnv *env, jclass) {
	int height = config::RenderResolution > 0 ? static_cast<int>(config::RenderResolution) : 480;
	const float aspect = getOutputFramebufferAspectRatio();
	int width = static_cast<int>(height * aspect);
	// Extended screen on a screen narrower than 4:3 keeps the 4:3 width and renders more lines
	if (config::Widescreen && config::SuperWidescreen && !config::Rotate90 && aspect < 4.f / 3.f) {
		width = height * 4 / 3;
		height = static_cast<int>(width / aspect);
	}
	jint values[] = {static_cast<jint>(FrameCount), width, height,
		SPG_CONTROL.isPAL() ? 50 : 60, getRenderer(), settings.input.fastForwardMode ? 1 : 0};
	jintArray result = env->NewIntArray(6);
	if (result != nullptr) env->SetIntArrayRegion(result, 0, 6, values);
	return result;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_overlayGpuName(JNIEnv *env, jclass) {
	auto *context = GraphicsContext::Instance();
	return env->NewStringUTF(context != nullptr ? context->getDriverName().c_str() : "");
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_flycast_emulator_emu_JNIdc_overlayGpuLoad(JNIEnv *, jclass) {
	static auto last = std::chrono::steady_clock::now();
	const auto now = std::chrono::steady_clock::now();
	const double elapsedNs = std::chrono::duration<double, std::nano>(now - last).count();
	last = now;
	const uint64_t gpuNs = gpu_usage::TakeNanoseconds() + glr::TakeGpuNs();
	if (!gpu_usage::IsSupported() && !glr::GpuTimed())
		return -1.f;
	if (elapsedNs <= 0.0)
		return 0.f;
	return static_cast<jfloat>(std::min(100.0, static_cast<double>(gpuNs) / elapsedNs * 100.0));
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_pollOverlayMessage(JNIEnv *env, jclass) {
	const std::string message = gui_takeOverlayMessage();
	return message.empty() ? nullptr : env->NewStringUTF(message.c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raOverlayEvents(JNIEnv *env, jclass) {
#ifdef USE_RACHIEVEMENTS
	return env->NewStringUTF(achievements::takeOverlayEvents().c_str());
#else
	return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_flycast_emulator_emu_JNIdc_stateThumbnail(JNIEnv *env, jclass, jint slot) {
	if (slot < 0 || slot > 5) return nullptr;
	std::vector<u8> png;
	dc_getStateScreenshot(slot, png);
	if (png.empty()) return nullptr;
	jbyteArray result = env->NewByteArray(static_cast<jsize>(png.size()));
	if (result != nullptr) env->SetByteArrayRegion(result, 0, static_cast<jsize>(png.size()), reinterpret_cast<const jbyte *>(png.data()));
	return result;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_loadCurrentSlot(JNIEnv *, jclass) {
	if (!dc_savestateAllowed() || settings.raHardcoreMode)
		return JNI_FALSE;
	if (gui_state == GuiState::Pause) {
		dc_loadstate(config::SavestateSlot);
		return JNI_TRUE;
	}
	if (gui_state != GuiState::Closed)
		return JNI_FALSE;
	gui_loadState(false);
	return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_loadStateSlot(JNIEnv *, jclass, jint slot) {
	if (slot < 0 || slot > 5 || settings.raHardcoreMode || !dc_savestateAllowed()
			|| (gui_state != GuiState::Closed && gui_state != GuiState::Pause)) return JNI_FALSE;
	gui_runOnUiThread([slot]() {
		const int previous = config::SavestateSlot;
		config::SavestateSlot = slot;
		if (gui_state == GuiState::Pause) dc_loadstate(slot);
		else gui_loadState(false);
		config::SavestateSlot = previous;
	});
	return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_insertDisk(JNIEnv *env, jclass, jstring path) {
	try {
		gdr::insertDisk(jstr(env, path));
		return nullptr;
	} catch (const FlycastException &e) {
		return env->NewStringUTF(e.what());
	} catch (const std::exception &e) {
		return env->NewStringUTF(e.what());
	}
}

extern "C" JNIEXPORT jint JNICALL Java_com_flycast_emulator_emu_JNIdc_cheatCount(JNIEnv *, jclass) {
	return (jint)cheatManager.cheatCount();
}

extern "C" JNIEXPORT jint JNICALL Java_com_flycast_emulator_emu_JNIdc_importCheatFile(JNIEnv *env, jclass, jstring path) {
	if (settings.raHardcoreMode || settings.content.gameId.empty()) return -1;
	try { return (jint)cheatManager.importCheatFile(jstr(env, path)); }
	catch (const std::exception&) { return -1; }
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_cheatDescription(JNIEnv *env, jclass, jint index) {
	if (index < 0 || (size_t)index >= cheatManager.cheatCount())
		return nullptr;
	return env->NewStringUTF(cheatManager.cheatDescription((size_t)index).c_str());
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_cheatEnabled(JNIEnv *, jclass, jint index) {
	if (index < 0 || (size_t)index >= cheatManager.cheatCount())
		return JNI_FALSE;
	return cheatManager.cheatEnabled((size_t)index) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_cheatBuiltIn(JNIEnv *, jclass, jint index) {
	if (index < 0 || (size_t)index >= cheatManager.cheatCount())
		return JNI_FALSE;
	return cheatManager.cheatBuiltIn((size_t)index) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_setCheatEnabled(JNIEnv *, jclass, jint index, jboolean enabled) {
	if (index < 0 || (size_t)index >= cheatManager.cheatCount() || settings.raHardcoreMode)
		return JNI_FALSE;
	// The cheat manager's VBlank callback applies changes when emulation resumes.
	cheatManager.enableCheat((size_t)index, enabled == JNI_TRUE);
	return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_removeCheat(JNIEnv *, jclass, jint index) {
	if (index >= 0 && (size_t)index < cheatManager.cheatCount() && !cheatManager.cheatBuiltIn((size_t)index))
		cheatManager.removeCheat((size_t)index);
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_addGameSharkCheat(JNIEnv *env, jclass, jstring name, jstring code) {
	if (settings.raHardcoreMode)
		return env->NewStringUTF("Cheats are disabled while RetroAchievements hardcore mode is active.");
	try {
		cheatManager.addGameSharkCheat(jstr(env, name), jstr(env, code));
		return nullptr;
	} catch (const std::exception &e) {
		return env->NewStringUTF(e.what());
	} catch (...) {
		return env->NewStringUTF("Flycast could not parse that GameShark code.");
	}
}

#ifdef USE_RACHIEVEMENTS
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raInit(JNIEnv *env, jclass, jstring userAgent) {
	if (!installRaHttpBridge(env, jstr(env, userAgent)))
		achievements::setHttpRequestHandler({});
	return achievements::init() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raHttpResponse(
		JNIEnv *env, jclass, jint id, jint status, jbyteArray body) {
	achievements::HttpResponse response;
	{
		std::lock_guard<std::mutex> lock(raHttpMutex);
		auto it = raHttpPending.find(id);
		if (it == raHttpPending.end())
			return;
		response = std::move(it->second);
		raHttpPending.erase(it);
	}
	std::vector<u8> bytes;
	if (body != nullptr) {
		const jsize size = env->GetArrayLength(body);
		bytes.resize(size);
		if (size > 0)
			env->GetByteArrayRegion(body, 0, size, reinterpret_cast<jbyte *>(bytes.data()));
	}
	response(status, std::move(bytes));
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLogin(JNIEnv *env, jclass, jstring username, jstring password) {
	const std::string user = jstr(env, username);
	const std::string passwordText = jstr(env, password);
	(void)achievements::login(user.c_str(), passwordText.c_str());
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLoginWithToken(JNIEnv *env, jclass, jstring username, jstring token) {
	const std::string user = jstr(env, username);
	const std::string savedToken = jstr(env, token);
	achievements::loginWithToken(user.c_str(), savedToken.c_str());
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLogout(JNIEnv *, jclass) {
	achievements::logout();
}

extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raSetHardcore(JNIEnv *, jclass, jboolean enabled) {
	achievements::setHardcore(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsLoggedOn(JNIEnv *, jclass) {
	return achievements::isLoggedOn() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsActive(JNIEnv *, jclass) {
	return achievements::isActive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsLoginPending(JNIEnv *, jclass) {
	return achievements::isLoginPending() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsHardcore(JNIEnv *, jclass) {
	return achievements::isHardcore() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raLoginError(JNIEnv *env, jclass) {
	return env->NewStringUTF(cleanTabField(achievements::lastLoginError()).c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raCurrentUser(JNIEnv *env, jclass) {
	const achievements::User user = achievements::getCurrentUser();
	std::ostringstream out;
	out << cleanTabField(user.username) << '\t' << cleanTabField(user.token) << '\t'
			<< cleanTabField(user.avatarUrl) << '\t' << user.score << '\t' << user.softcoreScore;
	return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raCurrentGame(JNIEnv *env, jclass) {
	const achievements::Game game = achievements::getCurrentGame();
	std::ostringstream out;
	out << cleanTabField(game.title) << '\t' << game.unlockedAchievements << '\t' << game.totalAchievements
			<< '\t' << game.points << '\t' << game.totalPoints << '\t' << cleanTabField(game.imageUrl)
			<< '\t' << (game.hardcore ? 1 : 0);
	return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raAchievements(JNIEnv *env, jclass) {
	const achievements::Game game = achievements::getCurrentGame();
	if (game.title.empty())
		return env->NewStringUTF("");
	std::ostringstream out;
	out << "#\t" << cleanTabField(game.title) << '\t' << game.unlockedAchievements << '\t'
			<< game.totalAchievements << '\t' << game.points << '\t' << game.totalPoints << '\t'
			<< cleanTabField(game.imageUrl) << "\t\t" << (game.hardcore ? 1 : 0) << '\n';
	for (const auto &item : achievements::getAchievementList()) {
		out << item.id << '\t' << cleanTabField(item.title) << '\t' << cleanTabField(item.description)
				<< '\t' << item.points << '\t' << (item.unlocked ? 1 : 0) << '\t'
				<< cleanTabField(item.imageUrl) << '\t' << cleanTabField(item.lockedImageUrl) << '\t'
				<< cleanTabField(item.progress) << '\t' << cleanTabField(item.category) << '\n';
	}
	return env->NewStringUTF(out.str().c_str());
}
#else
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raInit(JNIEnv *, jclass, jstring) { return JNI_FALSE; }
extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raHttpResponse(JNIEnv *, jclass, jint, jint, jbyteArray) {}
extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLogin(JNIEnv *, jclass, jstring, jstring) {}
extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLoginWithToken(JNIEnv *, jclass, jstring, jstring) {}
extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raLogout(JNIEnv *, jclass) {}
extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_raSetHardcore(JNIEnv *, jclass, jboolean) {}
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsLoggedOn(JNIEnv *, jclass) { return JNI_FALSE; }
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsActive(JNIEnv *, jclass) { return JNI_FALSE; }
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsLoginPending(JNIEnv *, jclass) { return JNI_FALSE; }
extern "C" JNIEXPORT jboolean JNICALL Java_com_flycast_emulator_emu_JNIdc_raIsHardcore(JNIEnv *, jclass) { return JNI_FALSE; }
extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raLoginError(JNIEnv *env, jclass) { return env->NewStringUTF("RetroAchievements is not enabled in this build."); }
extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raCurrentUser(JNIEnv *env, jclass) { return env->NewStringUTF("E\t\t\t0\t0"); }
extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raCurrentGame(JNIEnv *env, jclass) { return env->NewStringUTF("\t0\t0\t0\t0\t\t0"); }
extern "C" JNIEXPORT jstring JNICALL Java_com_flycast_emulator_emu_JNIdc_raAchievements(JNIEnv *env, jclass) { return env->NewStringUTF(""); }
#endif


extern "C" JNIEXPORT void JNICALL Java_com_flycast_emulator_emu_JNIdc_initAutoSkip(JNIEnv*, jclass) { gbcast::InitAutoSkip(); }
extern "C" JNIEXPORT jlongArray JNICALL Java_com_flycast_emulator_emu_JNIdc_autoSkipStats(JNIEnv* env, jclass) {
    auto values = gbcast::AutoSkipStats();
    jlong data[10]; for (int i = 0; i < 10; ++i) data[i] = values[i];
    auto result = env->NewLongArray(10); if (result) env->SetLongArrayRegion(result, 0, 10, data);
    return result;
}
