# Sega Rally 2 performance profile — RG Rotate

## Status

Profile candidate is implemented for GBFrame's `mid` tier and is intentionally limited to settings with measurements. The local GBCast build 55 was installed on RG Rotate; its log queued the Sega Rally 2 mid profile, and the core confirmed `renderer=0`, `resolution=960`, `autoSkip=0`. The on-screen overlay confirmed GLES at 2x and about 99.4% speed. Testing is not complete: the available save state enters an attract/demo sequence, not a reproducible race, so scene-to-scene comparisons vary. This report separates measurements from recommendations and lists the remaining controls that have not received a valid A/B.

## Device and test setup

- RG Rotate, Android 12, Unisoc T618, Mali-G52.
- GBCast 0.4.0, installed local debug build (version code 54) during measurement.
- `Sega Rally 2.chd` in the configured Dreamcast folder; the GBFrame launch intent selected the title and auto-loaded slot 1 (`Sega Rally 2_1.state`).
- 2x internal resolution (960), fullscreen with Extended screen enabled, OIT excluded.
- Baseline: Dynarec On, Auto frame skip Off, threaded rendering On, VSync On, frame pacing On, nearest texture filtering, shader None, anisotropic filtering Off, per-strip sorting Off, fog On, mipmaps On, delay frame swap On, framebuffer emulation Off.
- Audio baseline: DSP effects Off, automatic latency On, 64 ms displayed buffer, VMU sounds Off.

## Renderer comparison at 2x

Each renderer was launched from the same library title and save-state path, but the attract/demo sequence changes camera and scene. These are separate-run samples, not a deterministic frame-for-frame benchmark.

| Renderer | FPS samples | Speed samples | GPU samples | Median |
|---|---|---|---|---|
| Vulkan | 58.8, 59.6, 56.7, 59.7, 60.7, 59.7 | 98.0, 99.4, 94.5, 99.5, 101.1, 99.5% | 75.1, 73.0, 73.3, 69.0, 68.6, 74.6% | 59.65 FPS, 99.45% speed, 73.15% GPU |
| GLES | 55.8, 60.8, 60.8, 59.8, 57.8, 60.7 | 93.0, 101.3, 101.3, 99.7, 96.3, 101.2% | 67.4, 66.1, 66.8, 64.5, 55.4, 74.5% | 60.3 FPS, 100.5% speed, 66.7% GPU |

GLES is the provisional `mid` recommendation: the measured median was slightly higher and GPU load lower. The margin is small and the scene is variable, so this does not establish that GLES is universally faster. Vulkan also held near full speed. The profile sets GLES, 2x, Dynarec On, Auto frame skip Off, threaded rendering On, and frame pacing On; unspecified settings continue to use the user's choices.

## Bottleneck evidence

- Dynarec Off reduced the game to 7–8 FPS and 11.6–13.3% speed. Dynarec On is essential for this device.
- In the brief slow section of the attract/demo loop, the overlay fell to roughly 30–45 FPS and 50–75% speed. `top -H` showed `Flycast-emu` at 100% of one core while `Flycast-rend` was around 33%; GPU utilization was about 36–55% in corresponding captures. The main limiting work is on the emulation CPU thread, not sustained GPU saturation. The process-wide CPU overlay (roughly 16–19%) hides the single-core saturation because it is averaged across device cores.
- The same short slowdown appeared with frameskip Off, Normal, and Max. Max reduced measured GPU use in some captures but did not keep emulation at full speed in that section. No repeatable throughput gain was established; Off is retained in the profile to preserve smooth presentation.
- In the earlier GBCast build 55 test, `Threaded rendering` Off crashed on both Vulkan and GLES. A follow-up on build 59 launched and ran Off on both renderers without reproducing that crash. The old failure is not evidence that the current toggle is broken; keep On as the default because Off did not improve performance in the new A/B.
- VSync Off and frame pacing Off produced no material FPS increase in the sampled attract scenes. Keep both On for presentation stability.

The next useful performance investigation is a native sample/profile of `Flycast-emu` during the slow scene, with `Flycast-rend` and GPU counters collected at the same time. Reducing resolution or dropping rendered frames alone is unlikely to fix a saturated SH-4 emulation thread.

## Toggle results

| Control | Result / disposition |
|---|---|
| Renderer: GLES vs Vulkan | Both near target at 2x; provisional GLES profile due slightly lower median GPU use. OIT was excluded. |
| Dynarec | On required; Off is unusably slow. |
| Threaded rendering | On is the recommended default; build 59 Off ran on both renderers without a crash and showed no reliable gain. |
| Auto frame skip: Off / Normal / Max | Tested in stable samples and around the short demo slowdown. No robust improvement in emulation speed; keep Off for this profile. |
| VSync / frame pacing | Both Off variants tested; no reliable speed gain. Keep On for smoothness. |
| Per-strip sorting | On showed mixed results and can produce incorrect transparent overlap; leave Off for accuracy. |
| Fog | Off did not show a repeatable speed gain; keep On for the intended image. |
| Framebuffer emulation | On and Off sampled; the same transient slowdown occurred with both, so no gain is established. Leave Off unless a game requires it. |
| DSP effects / automatic audio latency / 64 ms buffer | DSP On and automatic latency Off were sampled, but scene variation prevents a CPU-performance conclusion and audio was not monitored for crackle/underruns. Profile keeps DSP Off and automatic latency On. |
| CD read speed / Fast CD load | 8x is the current default. Faster transfer affects read-bound waits rather than the emulated CPU/GPU's steady frame rate; test streamed audio/video separately because accelerated reads can desynchronize them if the core does not compensate. |
| Filter, shader, anisotropic filtering, mipmaps, delay frame swap, VMU sounds, other audio buffer sizes, display/widescreen options | Not yet tested in a controlled A/B. They remain user-selectable because they change visual quality, accuracy, or latency tradeoffs; this run provides no evidence to remove them. |

## Remaining validation

1. Repeat the renderer and toggle matrix in an actual race or a save state with a fixed camera and gameplay state.
2. Measure audio underruns and listen for crackle while comparing DSP, automatic latency, and 64/96/128 ms buffers.
3. A/B shader, filtering, anisotropic filtering, mipmaps, delay frame swap, and framebuffer emulation at matched scene points.
4. Verify the new per-game profile is applied on the local build and remains lower priority than saved per-game overrides.

## Follow-up: threaded rendering, audio, and 3x (2026-11-01)

Retested on RG Rotate with GBCast 0.4.0 build 59, using the GBFrame intent (`--es ROM Sega%20Rally%202.chd`) and confirming the native game activity plus the FPS/GPU overlay. The available Sega Rally 2 save state enters the changing attract/demo loop. Per-game recommendations were disabled during the matrix; Dynarec On, Auto frame skip Off, OIT excluded, VSync and frame pacing On, audio buffer 64 ms. Settings and preferences were restored after the tests, and the device was returned to sleep.

### Threaded rendering at 2x

The primary comparison used six OSD samples for each renderer/threading state:

| Renderer | Threaded On median | Threaded Off median | On minus Off |
|---|---:|---:|---:|
| GLES | 60.25 FPS / 100.45% / 29.50% GPU | 60.25 FPS / 100.35% / 29.20% GPU | 0.00 FPS / +0.10 percentage points |
| Vulkan | 60.25 FPS / 100.35% / 29.50% GPU | 59.50 FPS / 99.20% / 29.30% GPU | +0.75 FPS / +1.15 percentage points |

The measured differences are small and the attract/demo scene is not frame-synchronized, so this does not establish a material throughput benefit. Threaded Off did not crash on either renderer in build 59, unlike the earlier build 55 runs. Keep On as the default; no code change is indicated by this A/B.

### Audio toggles

| Test | Median FPS / speed / GPU | Reading |
|---|---:|---|
| DSP On, GLES | 60.25 / 100.40% / 29.35% | No measurable throughput change versus DSP Off. |
| Auto latency Off, GLES | 59.70 / 99.60% / 29.30% | No performance gain; slight lower median is within scene variation. |

AudioFlinger reported zero aggregate partial/empty mixer underruns during the check. There was no loopback or listening test. Retain DSP Off and Auto latency On as defaults; no audio setting here showed a repeatable FPS benefit. Flycast does not expose a separate Threaded Audio toggle.

### 3x headroom check

With 2x holding near the target and GPU utilization well below saturation, GLES was sampled at 3x (internal resolution 1440), Threaded Rendering On:

| Median FPS | Median speed | Median GPU | Median CPU overlay |
|---:|---:|---:|---:|
| 60.15 | 100.25% | 29.25% | 19.85% |

The 3x result stayed near the 2x result in this changing attract/demo loop. A thread-level `top -H` sample showed `Flycast-emu` around 90–96% of one core at 2x and 92–95% at 3x; `Flycast-rend` was around 33–36% at 2x and 36–40% at 3x. The renderer/GPU is not saturated in these samples, but the emulation thread has little CPU margin. Treat 3x as viable for this tested scene, not as a guaranteed full-speed setting for the entire game.

Raw measurements are in [GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_2x.csv](GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_2x.csv) and [GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_3x.csv](GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_3x.csv). The 2x file includes a repeated six-sample GLES/Threaded On check after the 3x run.
