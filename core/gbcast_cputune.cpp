#include "gbcast_cputune.h"
#include "cpu_tuning.hpp"
#include "emulator.h"
#include "hw/pvr/pvr_regs.h"
#include <atomic>
#include <chrono>
#include <unistd.h>

namespace gbcast {
namespace {
int64_t nowNs() { return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }

std::atomic<pid_t> emuTid{0};
std::atomic<int64_t> waitNs{0};
// Emulation thread only (VBlank events)
int64_t lastVBlank = 0;
uint32_t vblanks = 0;
std::atomic<bool> restart{true};

void event(Event event, void*) {
    if (event != Event::VBlank) {
        // Started, resumed or a state loaded: the emulation thread may be new and the interval since the last VBlank
        // includes the pause, so the next VBlank starts over
        restart = true;
        return;
    }
    const pid_t tid = gettid();
    const int64_t now = nowNs();
    if (restart.exchange(false) || emuTid.load(std::memory_order_relaxed) != tid) {
        emuTid = tid;
        cputune::PinEmulationThreads();
        vblanks = 0;
        lastVBlank = now;
        waitNs = 0;
        return;
    }
    // Threads created meanwhile inherit the emulation thread's one-core affinity: move them every ~3 s
    if (++vblanks % 180 == 0) cputune::PinEmulationThreads();
    cputune::SetFrameRate(SPG_CONTROL.isPAL() ? 50.0 : 60.0);
    const int64_t work = (now - lastVBlank) - waitNs.exchange(0, std::memory_order_relaxed);
    lastVBlank = now;
    if (work > 0 && work < 200'000'000) cputune::ReportFrameWork(work);
}
}

CpuTuneWait::CpuTuneWait() : start(gettid() == emuTid.load(std::memory_order_relaxed) ? nowNs() : 0) {}

CpuTuneWait::~CpuTuneWait() {
    if (start != 0) waitNs.fetch_add(nowNs() - start, std::memory_order_relaxed);
}

void InitCpuTune() {
    static const bool initialized = [] {
        for (Event e : {Event::Start, Event::Resume, Event::LoadState, Event::VBlank})
            EventManager::listen(e, event);
        return true;
    }();
    (void)initialized;
}
}
