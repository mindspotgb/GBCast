# GBCast — Privacy Policy

_Last updated: November 1, 2026_

GBCast is an emulator for Android. GBCast has no ads, analytics or user tracking, and does not sell personal data.

## Data kept on your device

GBCast keeps app settings, save data and save states, cheats, play time, downloaded cover images, the local ownership
state for an optional Google Play purchase, the BIOS files you select, and the location of your games folder on the
device. Game images are read from the folder you choose and are not uploaded by the library. If you use RetroAchievements,
the app also keeps the login token supplied by that service
in Android's no-backup storage and locally queues casual-mode achievement unlocks while offline. The token is not
copied by Android backup or device transfer.

Crash dumps and diagnostic logs may be written to app-private storage after a crash. They are deleted automatically
on the next app start and are not uploaded by GBCast.

Android may include eligible app-private files in its platform backup when device backup is enabled. Clearing the app's
data or uninstalling it removes app-private data, except for files that you exported or otherwise placed in a folder of
your choice.

## Optional network connections

- **RetroAchievements** (`retroachievements.org` and its media service): used only when you enable the feature and sign
  in. Login credentials are sent to RetroAchievements; the password is not stored by GBCast. Later requests can include
  the issued token, a game-disc hash, profile information and achievement unlocks.
- **Cover art** (`thumbnails.libretro.com`): requested when a game has no local cover; the normalized game title is part
  of the image address.
- **Google Play Billing**: used only if you choose to buy or restore the optional visual customization pack. Google Play
  processes payment. GBCast receives purchase status and a Play purchase token used to confirm the transaction; it does
  not receive card credentials or send purchase data to a developer server.
- **Online and network-emulation features**: broadband-adapter emulation, Naomi networking, battle cable and GGPO can
  communicate with hosts selected or configured by the user when enabled.
Those remote services can receive standard connection information such as the device's IP address and have their own
privacy practices.

## Optional visual customization purchase

GBCast offers one optional, non-consumable purchase through Google Play. It unlocks the **Grid** and **All in One**
library layouts and the ability to choose custom cover images. The default **Showcase** layout, automatically sourced
cover art, emulation, and RetroAchievements remain available without this purchase. Google Play handles payment; GBCast
stores only the resulting ownership state locally and excludes that cache from Android backup. Purchases can be restored
from the same Google Play account. We do not receive payment credentials or send purchase data to a developer server.

## Permissions

- **Internet**: for the optional services and network features listed above.
- **Microphone**: when a Dreamcast microphone is configured, GBCast captures microphone input for games that use that
  emulated peripheral. The samples are passed to the emulator and are not uploaded by GBCast.
- **Wi-Fi/Bluetooth**: for optional online/network emulation and connected input devices.
- **Folder/file access**: Android's system picker grants access only to game, BIOS, cheat or other files and folders the
  user selects.

## Children

GBCast is not directed at children and does not knowingly collect personal data from them.

## Changes and contact

Policy changes are published in this source repository. Questions can be opened at
<https://github.com/mindspot7/GBCast/issues>.
