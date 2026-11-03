#include "gbcast_profile.h"

#ifdef __ANDROID__
#include "cfg/option.h"
#include "emulator.h"
#include "types.h"
#include <mutex>

namespace gbcast {
namespace {
struct Profile {
	bool enabled = false;
	int renderer = -1;
	int resolution = -1;
	int autoSkip = -1;
	int threadedRendering = -1;
	int textureFiltering = -1;
	int framePacing = -1;
	int fastCdLoad = -1;
	int cdReadSpeed = -1;
};

std::mutex profileMutex;
Profile pending;
}

void setRecommendedProfile(bool enabled, int renderer, int resolution, int autoSkip,
		int threadedRendering, int textureFiltering, int framePacing, int fastCdLoad, int cdReadSpeed)
{
	std::lock_guard<std::mutex> lock(profileMutex);
	pending = { enabled, renderer, resolution, autoSkip, threadedRendering,
		textureFiltering, framePacing, fastCdLoad, cdReadSpeed };
}

void applyRecommendedProfile()
{
	// Dynarec is mandatory for usable Android performance, independent of whether a measured profile is enabled.
	config::DynarecEnabled.override(true);
	Profile profile;
	{
		std::lock_guard<std::mutex> lock(profileMutex);
		profile = pending;
	}
	if (!profile.enabled)
		return;
	if (config::Settings::instance().hasPerGameConfig()) {
		NOTICE_LOG(COMMON, "GBFrame recommended settings skipped: saved per-game settings take priority");
		return;
	}
	// The window is created for the saved renderer's API, and a renderer of the other API can't start in it: switching
	// Vulkan to OpenGL at game start found no GL context and crashed (Sega Rally 2 on the RG Rotate). The profile keeps
	// the saved API and only picks the renderer within it.
	const RenderType current = config::RendererType.get();
	const bool profileRenderer = profile.renderer == 0 || profile.renderer == 3 || profile.renderer == 4 || profile.renderer == 5;
	if (profileRenderer && isOpenGL(static_cast<RenderType>(profile.renderer)) == isOpenGL(current))
		config::RendererType.override(static_cast<RenderType>(profile.renderer));
	else if (profileRenderer)
		NOTICE_LOG(COMMON, "GBFrame profile renderer %d skipped: it needs another graphics API than the saved %d",
			profile.renderer, static_cast<int>(current));
	if (profile.resolution >= 240 && profile.resolution <= 4320)
		config::RenderResolution.override(profile.resolution);
	if (profile.autoSkip >= 0 && profile.autoSkip <= 2)
		config::AutoSkipFrame.override(profile.autoSkip);
	if (profile.threadedRendering >= 0)
		config::ThreadedRendering.override(profile.threadedRendering != 0);
	if (profile.textureFiltering >= 1 && profile.textureFiltering <= 2)
		config::TextureFiltering.override(profile.textureFiltering);
	if (profile.framePacing >= 0)
		config::FramePacing.override(profile.framePacing != 0);
	if (profile.fastCdLoad >= 0)
		config::FastGDRomLoad.override(profile.fastCdLoad != 0);
	if (profile.cdReadSpeed == 2 || profile.cdReadSpeed == 4 || profile.cdReadSpeed == 8 || profile.cdReadSpeed == 16)
		config::CdReadSpeed.override(profile.cdReadSpeed);
	NOTICE_LOG(COMMON, "GBFrame profile applied for %s: renderer=%d resolution=%d autoSkip=%d",
		settings.content.gameId.c_str(), static_cast<int>(config::RendererType.get()),
		static_cast<int>(config::RenderResolution), static_cast<int>(config::AutoSkipFrame));
}
}
#endif
