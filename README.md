# Screencapper

Faster screenshots, high-res shots, uploads and a searchable screenshot manager for Minecraft.

---

[![Discord Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/social/discord-singular_64h.png)](https://s.deftu.dev/discord)
[![Ko-Fi Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/donate/kofi-singular_64h.png)](https://s.deftu.dev/kofi)
[![Modrinth Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/mod/screencapper)
[![CurseForge Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/available/curseforge_64h.png)](https://www.curseforge.com/minecraft/mc-mods/screencapper)

---

## What it does
In vanilla Minecraft, sharing a screenshot means opening the screenshots folder, finding the file, and copying it somewhere. If the place you're sending it to won't take an image directly, you also have to upload it to an image host first.

With Screencapper, a preview of each screenshot pops up as soon as you take it. You can have it copied to your clipboard or uploaded to your image host automatically, or use the buttons on the chat message to view, copy, upload, open or delete it.

Screencapper needs [OneConfig][oneconfig], and its settings are in the OneConfig menu. It also adds a Screenshots page to the OneConfig sidebar where you can browse, search, copy, upload, open and delete every screenshot you've taken. Run `/screenshots` to open it.

Imgur and Catbox are built in. To use another image host, add it in the settings or import its ShareX `.sxcu` file. Screencapper keeps every link a screenshot has been uploaded to, and if a file is too big for a host, it's made smaller before uploading.

### Taking screenshots
Tap the screenshot key for a normal screenshot. It's saved in the background, so the game doesn't freeze while the file is written.

Hold the key instead and a capture wheel opens. Point at an option with the mouse and let go of the key to take it. The wheel has high-res shots at 4K, 8K or a size you set, a shot with the HUD hidden, and a timer that counts down for 3 seconds before taking the shot.

### Saving smaller files
Screenshots can be saved with one of a few presets, ranging from lossless PNG to small JPEGs that are easy to share. Normal shots, high-res shots and uploads each have their own setting. Custom lets you set the format, quality, colours and size yourself, and you can also keep an untouched copy of every shot.

### Finding screenshots later
Screencapper can store details inside each screenshot file: when it was taken, the world or server, the dimension and the biome. You can also turn on your coordinates and recent chat, which are off by default. The Screenshots page searches all of these, and uploads strip the private ones unless you allow them.

---

## Why use this instead of Shotify?
Screencapper started from Shotify's code and has since been mostly rewritten. It runs a bit faster, has extra features, and can upload to your own image host.

Shotify itself has been abandoned and won't be updated to 1.19 or newer. Screencapper will keep getting updates.

---

## But [Essential][essential] has this built in, why should I use this?!
Essential's version is buggy and breaks other mods, including this one. I wouldn't recommend it.

---

## Credits

- [isXander](https://github.com/isXander)
  - The original [Shotify](https://curseforge.com/minecraft/mc-mods/shotify) mod, which Screencapper started from
- [ramidzkh](https://github.com/ramidzkh)
  - [Fabrishot](https://github.com/ramidzkh/fabrishot) (MIT), which high-res capture is based on
- [Polyfrost](https://github.com/Polyfrost)
  - PolyPlus's radial wheel (GPL-3.0), which the capture wheel is adapted from

---

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/8fb6621b-811a-473b-9087-c8c42b50e74c.png)](https://s.deftu.dev/bisect)

---

**This project is licensed under [AGPL-3.0-or-later][agpl]****&copy; 2022 Deftu**

[essential]: https://essential.gg/
[oneconfig]: https://polyfrost.org/projects/oneconfig
[agpl]: https://www.gnu.org/licenses/agpl-3.0.en.html
