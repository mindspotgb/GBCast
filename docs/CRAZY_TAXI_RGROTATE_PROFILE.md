# Crazy Taxi performance profile — RG Rotate

## Result

For the tested RG Rotate (Unisoc T618 / Mali-G52), **Vulkan at 2x without OIT** is the best measured renderer candidate. It averaged 58.7 FPS / 97.8% emulation speed in the changing attract sequence. GLES at the same resolution averaged 55.0 FPS / 91.6%. The game profile already selects Vulkan for the GBFrame `mid` tier; that entry remains unchanged. The measurements do not justify forcing other settings in the profile because the attract sequence is variable and the normal-vs-off frameskip difference was small.

This is a local device profile, not a deterministic benchmark or a GitHub build. Several image/audio options were not A/B-tested and are listed below.

## Device and setup

- RG Rotate, Android 12, Unisoc T618, Mali-G52, 720x720 display.
- GBCast 0.4.0 build 55, installed locally.
- Crazy Taxi CHD, manual Slot 1 state (`Crazy Taxi_1.state`). The game's attract camera and traffic change continuously, so identical states do not guarantee identical rendered scenes.
- GBFrame launch intent was received but did not start the title. The game was opened from the library and the same Slot 1 state was confirmed loaded. This test therefore does not validate intent launch for this title.
- 2x internal resolution (960), fullscreen, Extended screen On, Widescreen hack Off; OIT excluded.
- Baseline: Auto frame skip Off, Dynarec On, threaded rendering On, VSync On, frame pacing On, Filter Nearest, shader None, anisotropic filtering Off, per-strip sorting Off, fog On, mipmaps On, delay frame swapping On, framebuffer emulation Off.
- Audio baseline: DSP Off, automatic latency On, 64 ms buffer, VMU sound Off, volume 100%.

## Renderer comparison at 2x

Each row contains eight OSD samples after a clean renderer launch and loading Slot 1. The sequences are not frame-synchronized; compare medians as indicative only.

| Renderer | FPS samples | Speed samples | GPU samples | Median |
|---|---|---|---|---|
| Vulkan | 59.6, 60.7, 58.7, 60.7, 58.6, 47.7, 57.7, 58.7 | 99.3, 101.2, 97.8, 101.2, 97.7, 79.6, 96.1, 97.8% | 69.8, 75.1, 73.5, 69.7, 69.7, 73.5, 76.9, 70.0% | 58.7 FPS, 97.8% speed, 71.8% GPU |
| GLES | 58.6, 51.8, 59.6, 59.6, 56.3, 53.7, 51.7, 44.8 | 97.7, 86.3, 99.4, 99.3, 93.8, 89.4, 86.2, 74.6% | 51.8, 49.2, 47.5, 59.3, 48.4, 47.2, 59.4, 59.8% | 55.0 FPS, 91.6% speed, 50.5% GPU |

The Vulkan run had one 47.7 FPS sample during an attract transition; it recovered to the high-50s without a clean restart. A previous live renderer switch produced temporary corrupted/slow frames, so those hot-switch samples were discarded. Cold-start results did not reproduce a persistent visual defect. Do not switch renderers mid-session when comparing performance.

## Performance and audio toggles

| Setting | Observed result | Recommendation |
|---|---|---|
| Dynarec | Off fell to 17.0 FPS / 28.3% speed. `Flycast-emu` reached 100% of one CPU core while `Flycast-rend` was about 21%. With Dynarec On, the same state returned to 58.8 FPS / 98.1%. | Keep On. Disabling it exposes a clear emulation-thread CPU bottleneck. |
| Auto frame skip Off | Eight samples: median 55.8 FPS / 93.0% speed. | Retain Off for consistent presentation while profiling. |
| Auto frame skip Normal | Eight samples: median 58.25 FPS / 97.1% speed. | Possible small gain, but scene variation makes it inconclusive versus Off. |
| Auto frame skip Max | Eight samples: median 45.9 FPS / 76.4% speed. | Avoid for this title/device; it regressed in this run and did not preserve emulation speed. |
| Filter Nearest / Bilinear | A brief Bilinear sample reached 60.8 FPS / 101.3% with 75.5% GPU, but it was not scene-matched to Nearest. | Keep Nearest as the tested baseline; no performance winner established. |
| DSP On | AudioFlinger reported zero partial/empty underruns during the check. A single observed game sample was 55.8 FPS / 93.0%; it is not a valid performance comparison against DSP Off. | Keep the user's original DSP Off preference; no throughput conclusion. |
| Threaded rendering, VSync, frame pacing | On throughout the main comparison; not A/B-tested in this run. | Keep the existing defaults. |

OIT renderers were explicitly excluded. Shader effects, anisotropic filtering, per-strip sorting, fog, mipmaps, delay frame swapping, framebuffer emulation, manual audio latency/buffer sizes, and VMU sounds were not individually A/B-tested. Their values above are the original baseline, not measured winners. No audio loopback/listening test was available.

## Bottleneck assessment

The strong Dynarec Off regression and full `Flycast-emu` core use establish that host CPU execution matters substantially. They do not prove that the normal Vulkan run is CPU-bound: renderer medians differed, GPU samples were higher in Vulkan, and the camera/traffic varied between runs. GPU load did not remain near saturation in the recorded Vulkan samples. A fixed gameplay scene plus repeated paired runs and native thread sampling would be needed to separate renderer cost from scene/CPU cost.

## End state

No code or profile asset was changed: the existing `mid` Crazy Taxi profile already selects Vulkan, and these noisy samples do not support adding more overrides. Test-only changes were restored: Recommended settings per game On, Vulkan at 2x, Auto frame skip Off, Dynarec On, DSP Off, Nearest Filter, and the original VSync/frame-pacing/audio settings. The RG Rotate display was returned to sleep after testing.

## Follow-up: threaded rendering, audio, and 3x (2026-11-01)

Repeated on RG Rotate with GBCast 0.4.0 build 59, launched by GBFrame intent (`--es ROM Crazy%20Taxi.chd`) and confirmed in `NativeGLActivity` with the FPS/GPU overlay active. Recommended per-game settings were disabled during the matrix so they could not overwrite the test values. Dynarec stayed On, Auto frame skip Off, OIT excluded, VSync and frame pacing On, and audio buffer at 64 ms. The original device config and preferences were restored after testing; the screen was returned to sleep.

### Threaded rendering at 2x

Six OSD samples per condition; these are different points in the changing attract sequence, not frame-matched captures.

| Renderer | Threaded On median | Threaded Off median | On minus Off |
|---|---:|---:|---:|
| Vulkan | 58.65 FPS / 97.70% / 39.65% GPU | 57.85 FPS / 96.45% / 40.90% GPU | +0.80 FPS / +1.25 percentage points |
| GLES | 59.05 FPS / 98.40% / 42.05% GPU | 58.70 FPS / 97.80% / 42.25% GPU | +0.35 FPS / +0.60 percentage points |

Threaded On was slightly ahead in both renderers, but the margins are small relative to the scene variation and do not establish a reliable performance gain. Off launched and ran in this build/device combination; the crash reported in the earlier build 55 test was not reproduced. Keep On as the default, and do not infer that Off is safe on every build/device.

### Audio toggles

| Test | Median FPS / speed / GPU | Reading |
|---|---:|---|
| DSP On, Crazy Taxi Vulkan | 59.50 / 99.25% / 39.00% | No defensible throughput change versus DSP Off; scene variation is larger than the apparent difference. |
| Auto latency Off, Crazy Taxi Vulkan | 58.70 / 97.90% / 40.70% | No measured speed benefit versus Auto On. |
| DSP On, Sega Rally 2 GLES | 60.25 / 100.40% / 29.35% | Effectively unchanged versus DSP Off. |
| Auto latency Off, Sega Rally 2 GLES | 59.70 / 99.60% / 29.30% | Slightly lower median; not a clear regression beyond scene variation. |

AudioFlinger reported zero aggregate partial/empty mixer underruns during the checks. There was no audio loopback or listening test, so these results do not validate crackle, audible latency, or sound quality. Keep the existing DSP Off and automatic latency On defaults; neither toggle showed a repeatable frame-rate advantage. Flycast has no separate Threaded Audio toggle.

### 3x headroom check

Because the 2x runs were near target and GPU use was well below saturation, the selected renderer was sampled at 3x (internal resolution 1440) with Threaded Rendering On:

| Renderer | Median FPS | Median speed | Median GPU | Median CPU overlay |
|---|---:|---:|---:|---:|
| Vulkan | 58.60 | 97.65% | 42.00% | 15.70% |

The 3x sequence remained close to the 2x result, without a clear FPS regression in this attract loop. The 3x CPU overlay median was 15.70% system-wide. Thread-level CPU sampling was performed in the separate Sega Rally 2 session, not in Crazy Taxi; see that report for the emulation-thread saturation evidence. No profile change is justified by this short capture.

Raw OSD samples are in [GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_2x.csv](GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_2x.csv) and [GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_3x.csv](GBCAST_THREADING_AUDIO_RGROTATE_2026-11-01_3x.csv). The 2x file contains the primary six-sample matrix, audio runs, and a second six-sample Sega Rally 2 GLES/Threaded On baseline check.

The 3x Frame pacing × Auto frame skip matrix across both renderers is documented in [the follow-up report](GBCAST_FRAME_PACING_SKIP_RGROTATE_2026-11-01.md).
