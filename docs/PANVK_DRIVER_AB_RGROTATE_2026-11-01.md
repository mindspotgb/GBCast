# PanVK r8 quick A/B — GBCast on RG Rotate (2026-11-01)

## Result

PanVK loaded and Crazy Taxi ran without a crash, but this short sample was slower than the system Vulkan driver. At 3x internal resolution, six OSD samples gave a median of **39.8 FPS / 66.4% speed** with PanVK versus **46.4 FPS / 77.4%** with the system driver, a 14.2% lower median FPS. These are indicative results: Crazy Taxi's auto-loaded attract scene changes between readings.

The GBCast GPU overlay reported `--` with PanVK. The release notes list timestamp queries as unavailable on Bifrost, matching the missing timer-based GPU reading. The result does not justify enabling PanVK by default on this device.

## Device, driver, and method

- RG Rotate, Android 12 / API 31, Unisoc T618, Mali-G52 MC2; GBCast 0.4.0 build 59.
- Crazy Taxi, Vulkan, 3x (`rend.Resolution=1440`), Threaded Rendering On, Frame pacing On, Auto frame skip Off, Dynarec On, VSync On, DSP Off, automatic audio latency On, 64 ms audio buffer. Recommended per-game profiles were temporarily disabled so the test values remained fixed.
- Six OSD readings per driver, three seconds apart after a 12-second launch warm-up. The game's auto-loaded attract sequence is not deterministic, so the samples are not frame-paired.
- The r8 package SHA-256 was checked against the GitHub release: `d82d8f2dd4a368770b9b43566f7b5295d205060cb6ffd536b07afc33914858a1`. Its metadata declares API 31 and `libvulkan_panfrost.so`.
- PanVK was confirmed loaded from `/data/data/app.gbcast.emulator/files/gpu_driver/libvulkan_panfrost.so` in the running process memory map. The device advertises the Android/API level required by the package. The release lists Mali-G52 as supported; r8's test-device list does not include the RG Rotate/T618 combination.

## Measurements

Raw samples are in [PANVK_DRIVER_AB_RGROTATE_2026-11-01.csv](PANVK_DRIVER_AB_RGROTATE_2026-11-01.csv).

| Driver | Median FPS | Median speed | FPS range | GPU overlay |
|---|---:|---:|---:|---|
| System Vulkan | 46.4 | 77.4% | 38.8–55.7 | Median 84.3% |
| PanVK r8 | 39.8 | 66.4% | 27.8–41.8 | `--` |

The GPU overlay being unavailable with PanVK is consistent with its Bifrost timestamp-query limitation. PanVK's speed result was lower even though the same renderer, resolution, pacing, and frame skip values were applied.

## Cleanup and validation

- Restored the exact original `emu.cfg` (SHA-256 `655FD51A3368012A4D28F72EE134BB26006EF83903D856C5646AAAB513801535`) and original shared preferences, including Recommended settings per game On; both file hashes match their pre-test backups.
- Removed the app-private PanVK driver and staging files. A final stock-driver launch succeeded; the config showed custom driver Off and the original 2x resolution.
- Returned the RG Rotate display to sleep. No app source code or build artifact was changed.

## Test harness correction

The previous 3x frame pacing/frameskip report used a PowerShell replacement expression that removed edited keys. Its measurements are retained as history but are not validated; rerun the matrix before drawing conclusions. The local `Set-CfgValue` helper now preserves capture group 1 and verifies that the requested key/value remains in the output.