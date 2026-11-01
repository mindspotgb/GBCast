# Dreamcast settings reference

## Video renderers

GBCast exposes four renderer choices: GLES, GLES OIT, Vulkan, and Vulkan OIT.
GLES and Vulkan select the graphics API. OIT means order-independent
transparency: the renderer resolves transparent pixels independently of draw
order, improving some overlapping effects at additional GPU and memory cost.
The non-OIT path is lighter; choose OIT for correctness if a game shows
transparency-order artifacts.

Enhanced resolution changes the PVR render target above native resolution. It
can sharpen 3D polygons and sprites, but increases GPU work and render-target
memory. Shader and Filter are GBFrame's shared output pass for GLES and Vulkan:
Shader applies a picture effect and Filter chooses Nearest or Bilinear scaling.
On Android, Vulkan frames are handed to the GBFrame GLES presenter through
AHardwareBuffer when supported; staged CPU frame delivery remains the fallback.
Core Texture filtering is separate: it controls texture sampling inside
Flycast. Both filter menus offer Nearest and Bilinear, with Bilinear as the
default.

## Loading and performance

CD read speed multiplies the emulated GD-ROM drive rate (Original, 4x, 8x, or
16x). Fast CD load reduces emulated GD-ROM DMA transfer delays. They affect
different parts of loading and remain separate. Higher drive rates may desync
streamed audio/video; Fast CD load can affect timing-sensitive games.

Auto frame skip Normal waits for both emulation CPU and GPU work to be behind
before dropping a rendered frame. Max can drop a frame whenever GPU work is
behind, even when CPU emulation is keeping up. Max can therefore preserve more
emulation speed at the cost of more visible skipped frames.

Dynarec (the SH-4 dynamic CPU recompiler) stays enabled in GBCast on Android;
turning it off makes gameplay substantially slower, so there is no user toggle.
Flycast already runs audio output through its Oboe callback; it does not expose
a separate threaded-audio option. Flycast has no distinct CPU sync control;
Threaded rendering only moves PVR rendering to a separate thread.

## System settings

Expanded RAM and console region are automatic system behavior, not user-facing
settings. GBCast starts in its existing USA region and falls back to a region
the disc supports when needed; title-specific core exceptions remain active.
The 32 MB RAM mode is enabled only for confirmed product IDs in the internal
compatibility list. That list currently has no entries: extra RAM does not
improve speed and can break compatibility. Broadcast remains a separate video
standard setting for the Dreamcast system flash; Default leaves the existing
flash value in place.

“Preload disc into RAM” is currently a GBSat/Ymir loader option, not a GBCast
setting. Ymir's ISO reader loads the image into memory and the CHD reader asks
libchdr to precache it. This can reduce storage-read stalls but increases load
time and RAM use roughly with the image/cache size; it does not speed emulated
CPU or GPU work. GBCast's core does not currently expose an equivalent option.

## Recommended settings per game

GBFrame loads `assets/game_profiles.json`; GBCast maps measured renderer,
resolution, auto-skip, threaded-rendering, texture-filtering, and disc settings
to temporary Flycast overrides at game boot. A saved per-game override
takes priority, and unmatched games keep global settings. The initial measured
profile is Crazy Taxi on the RG Rotate's Low tier, using Vulkan; the prior
device run recorded 60.6 FPS / 101.1% in its title/attract sequence. Other games
and device tiers are left untouched until measured.
