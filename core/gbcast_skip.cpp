#include "gbcast_skip.h"
#include "auto_skip.hpp"
#include "cfg/option.h"
#include "emulator.h"
#include "hw/pvr/Renderer_if.h"
#include "hw/sh4/sh4_if.h"
#include "hw/sh4/sh4_mem.h"
#include <cstring>
#include "ui/gui.h"
#include <atomic>
#include <chrono>
#include <vector>
#include <algorithm>

namespace gbcast {
namespace {
config::Option<bool, false> loadOption("GBFrame.SkipLoadingScreens", false);
config::Option<bool, false> transitionOption("GBFrame.SkipTransitions", false);
config::Option<bool, false> saveOption("GBFrame.SkipSaveWaits", false);
gb::AutoSkip engine;
std::atomic<uint32_t> enabled{0}, generation{0}, discSectors{0}, storageBytes{0};
std::atomic<bool> running{false}, active{false};
// Fast boot: the real BIOS intro runs at maximum speed, muted, until the BIOS hands over to the disc (IP.BIN at 8C008000)
std::atomic<bool> booting{false};
int bootFrames = 0, bootIpFrames = 0;
bool bootInIpBin = false;
int64_t bootStart = 0;
std::atomic<int64_t> pictureTime{0}, frames{0}, pictures{0}, reads{0}, saves{0}, activeFrames{0}, worstCaptureUs{0}, captureFailures{0}, captureTotalUs{0};
int64_t nowNs() { return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
void reset() {
    generation.fetch_add(1, std::memory_order_relaxed);
    active.store(false, std::memory_order_relaxed);
    pictureTime.store(0, std::memory_order_relaxed);
    discSectors.store(0, std::memory_order_relaxed); storageBytes.store(0, std::memory_order_relaxed);
    engine.Reset();
}
void options() {
    engine.skipLoads = loadOption.get(); engine.skipTransitions = transitionOption.get(); engine.skipSaves = saveOption.get();
    enabled.store((loadOption.get() ? 1 : 0) | (transitionOption.get() ? 2 : 0) | (saveOption.get() ? 4 : 0), std::memory_order_relaxed);
    if (!enabled.load(std::memory_order_relaxed)) reset();
}
void endBoot(const char* reason) {
    if (booting.exchange(false, std::memory_order_relaxed))
        NOTICE_LOG(COMMON, "GBFrame Fast boot off (%s) after %d frames, %d ms", reason, bootFrames, (int)((nowNs() - bootStart) / 1'000'000));
}
void bootVBlank() {
    if (!booting.load(std::memory_order_relaxed)) return;
    ++bootFrames;
    // The BIOS menu/intro program also runs from main RAM, so the address alone can't tell it from the game.
    // The disc's IP.BIN bootstrap (license screen) runs from 8C008000-8C00FFFF once IP.BIN is loaded there;
    // the game starts when execution leaves that area for main RAM.
    const u32 pc = Sh4cntx.pc & 0x1FFFFFFF;
    const bool inRam = (pc & 0x1C000000) == 0x0C000000;
    const u32 offset = pc & 0x00FFFFFF;
    const bool inIpBin = inRam && offset >= 0x8000 && offset < 0x10000
            && memcmp(GetMemPtr(0x8C008000, 16), "SEGA SEGAKATANA ", 16) == 0;
    bootIpFrames = inIpBin ? bootIpFrames + 1 : (inRam && offset >= 0x10000 ? 0 : bootIpFrames);
    if (bootIpFrames >= 10) bootInIpBin = true;
    if (bootInIpBin && inRam && offset >= 0x10000) endBoot("disc running");
    else if (settings.input.fastForwardMode || settings.network.online) endBoot("user or network");
    else if (bootFrames > 30 * 60) endBoot("timeout");
}
void event(Event event, void*) {
    if (event == Event::VBlank) {
        bootVBlank();
        if (!enabled.load(std::memory_order_relaxed) || !running.load(std::memory_order_relaxed)) return;
        ++frames;
        const int64_t age = nowNs() - pictureTime.load(std::memory_order_relaxed);
        if (gui_state != GuiState::Closed || settings.network.online || settings.naomi.multiboard || settings.input.fastForwardMode || age > 250'000'000) {
            if (active.exchange(false, std::memory_order_relaxed)) engine.Reset();
            discSectors.exchange(0, std::memory_order_relaxed); storageBytes.exchange(0, std::memory_order_relaxed);
            return;
        }
        engine.Update(discSectors.exchange(0, std::memory_order_relaxed) != 0, storageBytes.exchange(0, std::memory_order_relaxed));
        const bool on = engine.Active();
        if (active.exchange(on, std::memory_order_relaxed) != on)
            NOTICE_LOG(COMMON, "GBFrame AutoSkip %s reason=%u", on ? "on" : "off", engine.ActiveReason());
        if (on) ++activeFrames;
        return;
    }
    if (event == Event::Pause || event == Event::Terminate) running = false;
    else if (event == Event::Start || event == Event::Resume) running = true;
    if (event == Event::Start && config::FastBoot && settings.platform.isConsole() && !settings.content.path.empty()) {
        bootFrames = bootIpFrames = 0; bootInIpBin = false; bootStart = nowNs(); booting = true;
        NOTICE_LOG(COMMON, "GBFrame Fast boot on");
    }
    else if (event == Event::LoadState || event == Event::Terminate || event == Event::DiskChange) endBoot("state or disc change");
    reset();
    options();
    if (event == Event::Start) { frames = pictures = reads = saves = activeFrames = worstCaptureUs = captureFailures = captureTotalUs = 0; }
}
}
void InitAutoSkip() {
    static const bool initialized = [] {
        for (Event e : {Event::Start, Event::Pause, Event::Resume, Event::Terminate, Event::VBlank, Event::LoadState, Event::DiskChange, Event::Network})
            EventManager::listen(e, event);
        options();
        return true;
    }();
    (void)initialized;
}
bool AutoSkipActive() { return active.load(std::memory_order_relaxed) || booting.load(std::memory_order_relaxed); }
bool SkipLoads() { return loadOption.get(); }
bool SkipTransitions() { return transitionOption.get(); }
bool SkipSaves() { return saveOption.get(); }
void SetSkipLoads(bool value) { loadOption = value; reset(); options(); }
void SetSkipTransitions(bool value) { transitionOption = value; reset(); options(); }
void SetSkipSaves(bool value) { saveOption = value; reset(); options(); }
void AutoSkipAudio(int16_t l, int16_t r) { if (enabled.load(std::memory_order_relaxed)) engine.OnSample(l, r); }
void AutoSkipDisc(uint32_t count) { if (enabled.load(std::memory_order_relaxed)) { discSectors.fetch_add(count, std::memory_order_relaxed); reads.fetch_add(count, std::memory_order_relaxed); } }
void AutoSkipStorage(uint32_t bytes) { if (enabled.load(std::memory_order_relaxed)) { storageBytes.fetch_add(bytes, std::memory_order_relaxed); saves.fetch_add(bytes, std::memory_order_relaxed); } }
bool AutoSkipDropPresentation() {
    // Emulation-thread only. Do not recreate a Vulkan swapchain to accelerate a short wait.
    // Keep the displayed picture updating at ~30 Hz while allowing more emulated frames.
    static int64_t lastDraw = 0;
    if (!AutoSkipActive()) { lastDraw = 0; return false; }
    const int64_t now = nowNs();
    if (now - lastDraw < 33'333'333) return true;
    lastDraw = now;
    return false;
}
void AutoSkipPresent() {
    if (!enabled.load(std::memory_order_relaxed) || !running.load(std::memory_order_relaxed) || gui_state != GuiState::Closed ||
        settings.network.online || settings.naomi.multiboard || settings.input.fastForwardMode || renderer == nullptr) return;
    // Reuse each renderer's tiny, downscaled readback. Never capture the Android UI or a full-resolution screenshot.
    static int64_t lastCapture = 0;
    const int64_t now = nowNs();
    if (now - lastCapture < (AutoSkipActive() ? 16'000'000 : 50'000'000)) return;
    lastCapture = now;
    const uint32_t epoch = generation.load(std::memory_order_relaxed);
    static std::vector<uint8_t> rgb;
    int width = 32, height = 0;
    bool valid = false;
    try { valid = renderer->GetLastFrame(rgb, width, height); }
    catch (const std::exception& error) { WARN_LOG(RENDERER, "AutoSkip picture unavailable: %s", error.what()); }
    const int64_t cost = (nowNs() - now) / 1000;
    captureTotalUs.fetch_add(cost, std::memory_order_relaxed);
    int64_t previous = worstCaptureUs.load(std::memory_order_relaxed);
    while (cost > previous && !worstCaptureUs.compare_exchange_weak(previous, cost, std::memory_order_relaxed)) {}
    if (epoch != generation.load(std::memory_order_relaxed)) return;
    if (!valid || width <= 0 || height <= 0 || rgb.size() < static_cast<size_t>(width) * height * 3) {
        ++captureFailures; active = false; engine.Reset(); pictureTime = 0; return;
    }
    static std::vector<uint32_t> pixels;
    pixels.resize(static_cast<size_t>(width) * height);
    for (size_t i = 0; i < pixels.size(); ++i) pixels[i] = rgb[i * 3] | (rgb[i * 3 + 1] << 8) | (rgb[i * 3 + 2] << 16);
    engine.OnFrame(pixels.data(), width, height);
    pictureTime.store(nowNs(), std::memory_order_relaxed);
    ++pictures;
}
std::array<int64_t, 10> AutoSkipStats() {
    return {AutoSkipActive() ? 1 : 0, engine.ActiveReason(), frames.load(), pictures.load(), reads.load(), saves.load(), activeFrames.load(), worstCaptureUs.load(), captureFailures.load(), captureTotalUs.load()};
}
}
