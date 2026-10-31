#pragma once

#ifdef __ANDROID__
namespace gbcast {
void setRecommendedProfile(bool enabled, int renderer, int resolution, int autoSkip,
		int dynarec, int threadedRendering, int textureFiltering, int framePacing, int fastCdLoad, int cdReadSpeed);
void applyRecommendedProfile();
}
#endif
