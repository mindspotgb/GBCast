#pragma once
#include <cstdint>
#include <array>

namespace gbcast {
#ifdef __ANDROID__
void InitAutoSkip();
bool AutoSkipActive();
void AutoSkipPresent();
bool AutoSkipDropPresentation();
void AutoSkipAudio(int16_t left, int16_t right);
void AutoSkipDisc(uint32_t sectors);
void AutoSkipStorage(uint32_t bytes);
bool SkipLoads();
bool SkipTransitions();
bool SkipSaves();
void SetSkipLoads(bool value);
void SetSkipTransitions(bool value);
void SetSkipSaves(bool value);
std::array<int64_t, 10> AutoSkipStats();
#else
inline bool AutoSkipActive() { return false; }
inline void AutoSkipPresent() {}
inline bool AutoSkipDropPresentation() { return false; }
inline void AutoSkipAudio(int16_t, int16_t) {}
inline void AutoSkipDisc(uint32_t) {}
inline void AutoSkipStorage(uint32_t) {}
#endif
}
