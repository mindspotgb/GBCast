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
- `Threaded rendering` Off crashed startup on both Vulkan and GLES in two RG Rotate launches (native SIGSEGV, invalid access to `0x164`, process exit status 11). On launched the same title and state. This toggle's Off path is broken in this tested Android build/device combination; do not recommend turning it off. The UI toggle remains present pending a code-level fix or product decision.
- VSync Off and frame pacing Off produced no material FPS increase in the sampled attract scenes. Keep both On for presentation stability.

The next useful performance investigation is a native sample/profile of `Flycast-emu` during the slow scene, with `Flycast-rend` and GPU counters collected at the same time. Reducing resolution or dropping rendered frames alone is unlikely to fix a saturated SH-4 emulation thread.

## Toggle results

| Control | Result / disposition |
|---|---|
| Renderer: GLES vs Vulkan | Both near target at 2x; provisional GLES profile due slightly lower median GPU use. OIT was excluded. |
| Dynarec | On required; Off is unusably slow. |
| Threaded rendering | On required for this test; Off crashes on both renderers. |
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
