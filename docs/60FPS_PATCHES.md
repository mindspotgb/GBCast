# Dreamcast frame-rate patches

GBCast bundles the GameShark codes listed in the **60 FPS Cheats** column of the community-maintained [Flycast Widescreen Compatibility and Cheat Chart](https://github.com/nexus382/Flycast-Widescreen-Compatability-And-Cheat-Chart/blob/main/Dreamcast%20Widescreen.md). The bundled snapshot is maintained in `DreamcastFrameRatePatches.kt`.

The app offers the toggle only when the game title and region match a listed entry. The toggle adds the codes to the game's existing Flycast cheat file and enables or disables the whole set together. It does not modify game images. Codes are community reports, not GBCast compatibility guarantees; they can cause instability or change game speed. The toggle starts off.

When updating the list, preserve the chart's region distinctions and verify each code's syntax with Flycast's GameShark parser. Do not include `NATIVE`, unknown (`???`), or entries that do not contain a runtime GameShark code.
