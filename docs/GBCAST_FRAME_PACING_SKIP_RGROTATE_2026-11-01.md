# GBCast frame pacing and auto frame skip at 3x — RG Rotate

## Result

At 3x resolution, frame pacing On stayed close to the 60 FPS target but reported a slightly lower median FPS/speed than Off in all four game/renderer pairs. Auto frame skip Off, Normal, and Max did not produce a repeatable performance winner. The results do not justify changing defaults or per-game profiles: keep frame pacing On for presentation timing and auto frame skip Off for these measured scenes.

Neither game showed sustained GPU saturation in the OSD. Auto frame skip therefore had little reason to discard presentation frames. The settings may behave differently in a demanding gameplay scene; the available save states enter changing attract/demo sequences.

## Device and method

- RG Rotate, Android 12, Unisoc T618, Mali-G52, 720x720 display.
- GBCast 0.4.0 build 59, installed on the device before this test; no new build was made.
- Crazy Taxi and Sega Rally 2 were launched through the GBFrame intent by ROM name; each run reached `NativeGLActivity` and exposed the performance overlay. The existing auto-loaded slot-1 states enter changing attract/demo sequences.
- 3x internal resolution (`rend.Resolution=1440`), OIT excluded, VSync On, Threaded Rendering On, Dynarec On, DSP Off, Auto latency On, 64 ms buffer.
- Recommended per-game profiles were temporarily disabled so they would not replace the test settings. The exact original `emu.cfg` was restored and SHA-256 verified; preferences were restored, including Recommended settings per game On. The device was returned to sleep.
- Matrix: two games × two renderers (GLES `pvr.rend=0`, Vulkan `pvr.rend=4`) × Frame pacing On/Off × Auto frame skip Off/Normal/Max (`pvr.AutoSkipFrame=0/1/2`). Six OSD samples per condition: 24 conditions, 144 samples total. Each sample was taken at a 3-second interval after a 12-second launch warm-up.

The sequence is not deterministic: camera, traffic, and demo actions change between launches. Interpret medians as indicative; they are not paired frame-for-frame measurements. Raw readings are in [GBCAST_FRAME_PACING_SKIP_RGROTATE_2026-11-01.csv](GBCAST_FRAME_PACING_SKIP_RGROTATE_2026-11-01.csv).

## Median results

Frame pacing columns pool 18 samples each (six per skip mode). The skip columns pool 12 samples each (six with pacing On and six with pacing Off).

| Game / renderer | Pacing On FPS / speed | Pacing Off FPS / speed | Skip Off FPS | Skip Normal FPS | Skip Max FPS |
|---|---:|---:|---:|---:|---:|
| Crazy Taxi / GLES | 58.90 / 98.15% | 59.65 / 99.40% | 58.70 | 59.40 | 59.70 |
| Crazy Taxi / Vulkan | 58.95 / 98.30% | 59.60 / 99.40% | 59.70 | 59.10 | 59.35 |
| Sega Rally 2 / GLES | 59.75 / 99.60% | 60.60 / 101.00% | 60.35 | 60.10 | 60.45 |
| Sega Rally 2 / Vulkan | 59.75 / 99.60% | 60.20 / 100.30% | 59.90 | 59.70 | 60.65 |

OSD GPU samples ranged from 25.8–46.9% for Crazy Taxi GLES, 27.8–46.7% for Crazy Taxi Vulkan, 26.7–39.5% for Sega Rally 2 GLES, and 26.6–40.3% for Sega Rally 2 Vulkan. These readings show substantial GPU headroom in the tested scenes.

## Interpretation

- **Frame pacing:** Off measured 0.45–0.85 FPS higher in pooled medians, with speed about 0.7–1.4 percentage points higher. Several Off runs read slightly above 60 FPS/100% speed, which is not a real increase in the emulated game's target rate. This is consistent with removing the display pacing wait. These 3-second OSD samples do not measure per-frame presentation intervals, so they cannot quantify judder or prove which mode looks smoother. Keep On for controlled frame delivery; disabling it is not a demonstrated gameplay-performance improvement.
- **Auto frame skip:** Normal and Max differences were inconsistent across the games/renderers and stayed within 1 FPS in the pooled medians. Max was highest only for Sega Rally 2/Vulkan; it was not a cross-renderer or cross-game win. Auto frame skip reduces graphics presentation when its lag conditions are met; it does not increase emulated CPU throughput. With GPU use below 47%, these attract scenes did not provide evidence for leaving it enabled by default.
- **Practical profile:** Keep Frame pacing On and Auto frame skip Off at 3x for the measured scenes. If a heavier gameplay segment falls below target, Normal is the less aggressive first option to test; Max can drop frames for graphics-only lag and may look less smooth. A fixed gameplay state and frame-time/presentation-interval capture would be needed to validate that case.

No app or GBFrame code was changed during this test.
