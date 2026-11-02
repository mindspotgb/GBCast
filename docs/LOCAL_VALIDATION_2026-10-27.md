# Local validation: shared data folder, monitoring and Android Back

## Shared video/performance settings and measured profile (2026-10-31)

- GBFrame now owns the reusable GBCast rows for renderer, enhanced resolution,
  core texture filtering, GBFrame shader/output filter, Dynarec, auto frame skip,
  Fast CD load, CD read speed, Dreamcast region/broadcast/RAM, and the recommended
  settings toggle. GBCast only maps these controls to Flycast values and JNI.
- The new profile path reads the app's GBFrame `game_profiles.json` before launch,
  then applies temporary Flycast option overrides after core per-game settings are
  loaded. Existing saved per-game settings take precedence and remain unmodified;
  unmatched games retain global settings.
- The RG Rotate measured profile matched Crazy Taxi at GBFrame tier `low` and
  queued Vulkan. Boot logs confirmed `renderer=4`, native `resolution=480`, and
  `autoSkip=0`; the user savestate loaded and the game remained in
  `NativeGLActivity`. This verifies profile lookup/application and boot, not a new
  frame-rate benchmark. Prior validation recorded 60.6 FPS / 101.1% in the Vulkan
  title/attract sequence; only the renderer is encoded because that is the only
  profile setting supported by recorded measurements.
- Local release build 10 was signed with the configured GBCast release key and
  installed as an update on RG Rotate without clearing app data. The profile
  toggle defaults on. No GitHub workflow was triggered.
- Settings reference: `docs/SETTINGS_REFERENCE.md`. No separate Threaded audio or
  CPU Sync controls were added because Flycast exposes neither as a user setting;
  Oboe already supplies callback-driven audio, and Threaded rendering only
  separates PVR work from the emulation thread.

- GBFrame is the source for `AppDataFolder`, `DataFolderSync`, the wizard/System
  controls and `MonitoringSettings` (main Video and quick-menu Overlays).
- GBCast mounts native `data` and library `covers`. Optional custom textures use
  `data/textures/<game ID>`; the Video toggle controls the native replacement flag.
  Games remain a separate read-only SAF tree. Preferences/account tokens are not
  copied. Native files use the local working copy and synchronize after writes.
- Tested on RG Rotate, ADB `ROTATE05716608`: selected `Documents/GBCastData` through
  the system picker, retained read/write permission after process restart, restored
  a fixture and synchronized a completed local write back to the selected folder.
  Renaming the selected folder produced a visible warning; restoring it and using
  Sync now completed the pending write. Local data was retained throughout.
- During Crazy Taxi: Android Back opened and retained the paused shared quick
  menu. Back from Overlays returned to the root. Enabling Show fast-forward button
  removed the root fast-forward action. The common monitoring toggles were present.
- Local app unit tests (18), shared synchronization/monitoring tests (10) and app-ID
  consistency passed. The adaptive icon removed the white backing on this launcher
  and used its black compatibility background.
- A real texture replacement pack was not provided; pack rendering is not claimed
  as visually validated. Cloud APK builds remain manual/explicit-trigger only.


## Direct authoritative data storage - build 43

- Shared GBFrame SAF Kotlin/native adapters replace the background mirror. The chosen tree is the actual data store. Ordinary startup does not hash/synchronize file contents.
- One-time migration verified destination data and retired the obsolete local native data and covers directories. Verified on RG Rotate that both physical local directories are absent after migration and after a native save.
- Existing saves/covers remain accessible. Crazy Taxi launched; saved to previously empty manual Slot 5, displayed its screenshot, and loaded that slot from Documents/GBCastData. Native VMU/NVRAM also use the selected tree.
- Renamed the selected folder temporarily while the app was stopped: startup reported unavailable storage and offered the shared chooser without opening the core or falling back to local files. Restored the folder afterwards.
- On-device instrumentation: DirectDataFolderTest verifies direct Kotlin/native reads/writes, external edits, metadata, replacement, listing, mmap, traversal rejection, no local mirror, and no fallback to a deliberately stale local save (2 tests).
- Local unit tests: 18 app tests and 12 shared tests, including default-storage File adapter regression checks. App-ID check passed.
- Existing destination files are retained when changing folders; unavailable previous storage is reported explicitly. Migration diagnostics/backups are under ignored test-output/direct-data-before on the PC.
- No new storage permissions. Uses ACTION_OPEN_DOCUMENT_TREE, persistent URI grants and ContentResolver descriptors; no MANAGE_EXTERNAL_STORAGE or guessed shared-storage paths.
- Texture directory access uses the same native adapter; a real replacement texture pack was not supplied for visual testing.
- Folder-change device test: used the Android picker to move from GBCastData to a fresh GBCastDirectCheck tree, reopened with the new tree, ran Check access, then selected the original GBCastData again. Existing states/covers and direct access survived; the temporary test tree was removed after verifying the restored selection.


## Shared System layout - build 44

- Uses GBFrame SystemSettings: Data (Data folder, Games folder, Bios folder), BIOS emulation/HLE, automatic save states, Loading Times, then Dreamcast-specific settings.
- Folder rows show effective paths when configured; the BIOS row lists dc_boot.bin and dc_flash.bin separately as Loaded/Missing and supports the existing boot .bak alternative. Missing firmware is not labelled Loaded because HLE is available.
- Removed duplicate Paths/Dreamcast BIOS groups and wizard paragraphs from System. The shared BIOS selection dialog offers folder import or individual files.
- Fast GD-ROM load moved from the main Performance page into Loading Times. No unsupported Saturn Skip Engine controls were added to GBCast.
- RG Rotate: verified the three Data rows, wrapped BIOS path and both Missing states (no Dreamcast BIOS installed), shared BIOS dialog/folder picker, HLE placement, Save states/Loading Times/Dreamcast order, and touch scrolling on 720x720.
- Local app tests (18), shared tests (14), and app-ID checks passed. No storage permissions or installation IDs changed.

## Shared automatic Skip Engine - local build 45

- Implements Skip loading screens, Skip transitions and Skip save waits in System > Loading Times using GBFrame's shared native detector. All three new persisted flags default off and remain independent of manual fast-forward and Fast GD-ROM load.
- Actual inputs: tiny renderer-owned game pictures, unmuted AICA samples, disc reads through hardware and HLE paths, VMU blocks and Naomi EEPROM. No Android screen-capture permission or changes to storage access.
- Pausing, opening menus, loading states, disc/network changes and missing/stale pictures suppress/reset automatic acceleration. Online/multiboard and manual fast-forward suppress it. Rendering acceleration preserves render-to-texture and framebuffer writes.
- RG Rotate, Android 12: Crazy Taxi reached its title/attract sequence with real loading/VMU detection in GLES and Vulkan, returning to normal speed with audio/moving pictures. GLES sample: 4,907 emulated frames, 1,345 picture samples, 11,898 disc sectors, 27,136 backup bytes, 638 accelerated frames; mean capture 2.06 ms. Vulkan final sample: 1,836 frames, 469 samples, 8,682 sectors, 27,136 backup bytes, 645 accelerated frames; mean capture 1.97 ms. Three startup samples unavailable before renderer initialization in each session. Observed Vulkan title at 60.6 FPS / 101.1%.
- Fixed an experimental Vulkan pacing regression found during device testing: automatic skips keep swapchain and Swappy configuration stable and only drop eligible display renders. No emulation or backup writes are skipped.
- With all options off, diagnostic picture/activity counters stayed unchanged. Restored new flags off and original GLES renderer after testing; retained existing Fast GD-ROM preference and user data.
- Shared native ARM64 tests: 17 passed on device (three detectors, cancellation, defaults, streaming, concurrent callbacks and background saves). Device instrumentation: 1 passed for native option binding and independence from disc/manual switches. App unit tests: 18; shared unit tests: 14. App-ID and whitespace checks passed.
- Fast GD-ROM load remains a separate option: getGDROMTicks() shortens DMA scheduling to 512 cycles; it does not handle scene transitions or save waits. Both features can coexist but are not equivalent. HLE disc reads are detected by the shared Skip Engine even where the hardware DMA timing option does not apply.
- Detection is heuristic. The standalone native tests cover all three reasons; real-game logs also observed transition detection during Vulkan investigation. No claim of universal game compatibility or a measured percentage reduction in loading time.
- Local builds only; GitHub synchronization uses [skip ci].

## Lean manual packaging workflow

- Keeps one workflow: Build GBCast, in .github/workflows/build.yml. Manual dispatch only; no push/PR-triggered tests or builds. Android version code is supplied explicitly to preserve update ordering when workflow numbering changes.
- Removes separate test/lint/app-ID/diagnostic workflows. Unit/device/native tests and local diagnostics remain in the repository; cloud packaging does not execute tests, build a test APK, boot an Android emulator or generate screenshots/profiling reports.
- Retains signing, compiler/dependency caches, release APK packaging and the native Android 16 KB package validator. Local install helpers now use build.yml.
- All three workflow files validated locally with actionlint 1.7.12. Local device-tool PowerShell syntax and app-ID checks passed. GBCast's release-only Gradle task graph was checked locally with no unit/instrumentation/debug or Lint tasks.
