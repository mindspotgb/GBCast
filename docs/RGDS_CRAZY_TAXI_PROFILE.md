# Crazy Taxi profile: RG DS / RK3568

## Test setup

- Device: RG DS, Rockchip RK3568, Android 14, 640x480.
- Game: Crazy Taxi, same saved attract/title scene (`Crazy Taxi_2.state`).
- Launch path: the GBFrame intent contract, explicitly targeting `LibraryActivity` with `--es ROM 'Crazy%20Taxi.chd'`.
- Samples: eight OSD readings, two seconds apart, after loading the same state. Attract mode changes camera and traffic, so scene load varies substantially; use medians and regard small differences as inconclusive.
- Baseline: GLES, Native 640x480, Bilinear, VSync On, anisotropic Off, Per-strip sorting Off, Fog On, Mipmaps On, Delay frame swapping On, Emulate framebuffer Off; Auto frame skip Normal, Dynarec On, Threaded rendering On, Frame pacing On.

## Recommended Low-tier profile

The per-game entry in `shell/android-studio/flycast/src/main/assets/game_profiles.json` selects:

- GLES, Native resolution, Bilinear texture filtering.
- Auto frame skip Normal.
- Dynarec, Threaded rendering, and Frame pacing On.

The profile keeps global settings for unmeasured options. Saved per-game settings retain priority. Frame pacing is now part of the GBFrame profile handoff through Kotlin/JNI/native code so this recommendation does not silently depend on the global default.

## Results

| Setting | Median FPS | Notes |
| --- | ---: | --- |
| Auto frame skip Off | 35.3 | Eight readings; more dips below target. |
| Auto frame skip Normal | 37.3 | Best median among Off/Normal/Max; modest gain, with less aggressive frame dropping than Max. |
| Auto frame skip Max | 32.8 | Did not improve throughput in this scene. |
| Dynarec Off + Normal skip | about 7 | Severe regression; leave Dynarec On. |
| Threaded rendering On | 37.3 | Reference run. |
| Threaded rendering Off | 25.9 | Clear regression; leave On. |
| Frame pacing On | 37.3 | Reference run. |
| Frame pacing Off | 36.3 | Small difference inside scene variation; retain On. |
| VSync On | 37.3 | Reference run. |
| VSync Off | 41.2 | Not repeatable evidence of a speed gain; retain On for presentation. |
| GLES OIT | 25.4 | Slower than regular GLES. |
| Vulkan | 11.9 | Black/mostly black output in this RG DS run. |
| Vulkan OIT | — | Renderer initialization crashed with SIGSEGV. |
| Native resolution | 37.3 | Recommended. |
| 2x resolution | 19.9 | Large performance loss; 3x/4x were not pursued. |
| Bilinear | 37.3 | Kit default; retain. |
| Nearest | 35.8 | No measured gain. |
| Anisotropic 16x | 34.8 | No measured gain over Off. |
| Per-strip sorting On | 45.1 | One run looked faster, but scene variation and possible transparency-order errors make it too uncertain for the profile. |
| Fog Off | 45.3 | Tested with Per-strip On; effectively tied with Fog On (45.1), so retain Fog On. |
| Mipmaps Off | 48.7 | Tested with Per-strip On; modest, confounded gain, not enough to recommend. |
| Delay frame swapping Off | 46.8 | Tested with Mipmaps Off and Per-strip On; no evidence of improvement. |
| Emulate framebuffer On | 15.9 | Large regression; leave Off unless a game requires the effect. |

Fast CD load and CD read speed affect disc loading, not steady-state game FPS. Intent-to-savestate load time was about 4.64 s with Fast CD load Off / Original speed, 4.65 s with it On / Original, and 4.57 s with it On / 16x. The differences are under 0.1 s and do not justify changing the default; the 16x run was not a fully repeated timing trial.

## Bottleneck evidence and limits

`top -H` sampled the Flycast render thread at about 70% of one logical core and the emulation thread at about 41%. The app's GPU overlay reports `Mali-G52|--` because GPU load is unavailable to the app on this device. This suggests substantial CPU-side rendering work, but does not establish whether the Mali GPU is saturated or waiting. No GBSat/Ymir Saturn-specific optimization is directly reusable in Flycast; keep profiling Flycast's own renderer and thread waits before changing core code or thread affinity.

## Validation

Local GBCast Release build 33 installed on the RG DS. The exact GBFrame intent logged `Crazy%20Taxi.chd`; the library resolved it to Crazy Taxi, queued the Low profile, and the native core logged `renderer=0 resolution=480 autoSkip=1`. The same state file was then loaded. This confirms the profile handoff and launch path; it is not a GitHub build or mainline status.

## GLES-only follow-up: native 1x, 2026-10-31

Scope was limited to GLES renderers at Native 640x480. The RG DS reports OpenGL ES 3.2 on ARM Mali-G52. Each run used the same GBFrame intent and Crazy Taxi save state; the title/attract camera and traffic continue to change, so per-sample FPS is noisy.

- Per-strip sorting On: eight OSD samples had a median of about 29.9 FPS in the repeated run. Per-strip sorting Off: median about 27.4 FPS. This difference is within scene variation; no repeatable gain or visual defect was established. Keep it Off in the recommended profile.
- The local GLES-only change combines per-program caching of clipping rectangle, palette index/texture size, and trilinear alpha uniforms with an `unordered_map` for cached GL texture parameters. It skips redundant `glUniform*` updates and removes the tree lookup from the texture-parameter cache. Draw order, shader selection, and Vulkan are untouched.
- Builds 35 and 36 compiled locally; build 36 (the combined change) is installed. The exact GBFrame intent opened Crazy Taxi; logs confirm GLES 3.2, Mali-G52, and the game profile's native 480-line resolution. The tested frame remained visually intact.
- `simpleperf` on build 36 (6-second capture) showed `DrawStrips` at 73.4% inclusive, Mali GLES driver at 57.3%, and `OpenGLRenderer::Process` at 10.4%. The map lookup symbol disappeared after switching the parameter cache to a hash table, but overall renderer/driver share remained near the earlier profile range (DrawStrips 66-72%, Mali 52-56%); the short capture does not establish a measurable FPS or frame-time gain.
- The current evidence points to CPU-side GLES draw submission/driver work as the largest renderer cost. The GPU load is still unavailable from the device overlay, so this does not establish GPU saturation. The next high-value investigation is reducing compatible draw submissions while preserving Saturn/Dreamcast polygon ordering; a state-compatible multi-draw path needs runtime extension confirmation and visual validation before adoption.

The profile remains GLES, Native, Bilinear, VSync On, anisotropic Off, Per-strip sorting Off, Fog On, Mipmaps On, Delay frame swapping On, Emulate framebuffer Off, Auto frame skip Normal, Dynarec On, Threaded rendering On, and Frame pacing On. No change is recommended from this follow-up based on FPS alone.
