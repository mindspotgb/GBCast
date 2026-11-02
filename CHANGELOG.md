# Changelog

## Unreleased

- Move shared renderer, enhanced-resolution, filtering, shader, frame-skip, recompiler, and CD-load controls into GBFrame; add measured per-game recommendations with global settings as fallback.
- Remove Integer scaling, Rotate 90°, and crash-report upload controls from Android settings.
- Keep CD read speed separate from Fast CD load and clarify Dreamcast RAM, region, and renderer behavior.
- Use GBFrame's shared widescreen game hacks toggle in Video settings and the in-game Video menu.
- Move supported 60 FPS patches to the in-game Performance menu; enable them by default for compatible games, except in RetroAchievements Hardcore.

## 0.4.0
First release of GBCast, the Sega Dreamcast, Naomi, Naomi 2 and Atomiswave emulator for Android of the GB family (Flycast core, GBFrame interface).

- **Signed with the permanent GBCast release key.** Every later version installs over this one. Test builds installed before this release used another key: back up your data (Export, or copy the `GBCast` data folder) and uninstall them once before installing 0.4.0.
- Game library from a folder you pick (no storage permission): Showcase, Grid and All in One layouts, box art from libretro-thumbnails (or a web/local search), play time, save-state screenshots and the last selected game remembered.
- Controller first: A/B/X/Y/Start/Select work in every menu and dialog; the touch controls are hidden while a physical controller is connected (also during the BIOS boot).
- In-game quick menu (Back or the menu button): save/load states (5 slots plus quick and automatic ones), Video (display mode 4:3, 16:9, Super widescreen, Fullscreen; renderer; resolution), Audio, Performance, Overlays, disc swap, restart and per-game settings.
- Audio: volume and fast-forward audio (Default, Speed up, Mute) like the other GB apps, then Flycast's DSP, latency, buffer and VMU sound.
- Hotkeys and button mapping on a drawing of the Dreamcast controller; combinations with L3 work every time.
- Cheats: 294 offline databases from libretro-database plus GameShark codes, and the 60 FPS codes enabled together when a game supports them.
- RetroAchievements, with an offline cache for casual mode.
- Loading-screen, transition and save-wait skips; automatic save and load; fast boot.
- Frontends (ES-DE, Daijishou, Pegasus) can start a game directly through an intent with the disc's URI or a `ROM` path extra.
- Performance overlay (FPS, speed, CPU, GPU, renderer, resolution, frame time) and the overlays at the top of the screen from the first frame.
