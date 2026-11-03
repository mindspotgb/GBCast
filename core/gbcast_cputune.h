#pragma once
#include <cstdint>

// GBFrame CPU tuning (cpu_tuning.hpp) for Flycast's emulation thread: the thread is pinned alone on the fastest core,
// threads that inherited its affinity are moved off it, and the work of each frame (the time between two VBlanks
// without the waits below) goes to the Android performance hint session, with the PAL or NTSC frame time as target.
namespace gbcast {
#ifdef __ANDROID__
void InitCpuTune();

// Wraps a wait of the emulation thread (full render queue, render end, VRAM rollback, full audio buffer): that time is
// pacing, not work, and is left out of the frame's work time. Other threads are not counted.
class CpuTuneWait {
public:
    CpuTuneWait();
    ~CpuTuneWait();
    CpuTuneWait(const CpuTuneWait &) = delete;
    CpuTuneWait &operator=(const CpuTuneWait &) = delete;

private:
    int64_t start;
};
#else
inline void InitCpuTune() {}
struct CpuTuneWait {};
#endif
}
