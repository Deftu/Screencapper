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

With Screencapper, a preview of each screenshot pops up as soon as you take it. You can have it copied to your clipboard or uploaded automatically, or use the buttons on the chat message to view, copy, upload, open or delete it.

Screencapper needs [OneConfig][oneconfig], and its settings are in the OneConfig menu. It also adds a Screenshots page to the OneConfig sidebar where you can browse, search, edit, copy, upload and delete every screenshot you've taken, and zoom in on any of them full screen. Run `/screenshots` to open it.

### Uploading
Imgur and Catbox work out of the box. You can also post to a Discord channel through a webhook, or send screenshots to your own Immich or Nextcloud server. Google Photos and Flickr work too, once you sign in with your own Google Cloud app or Flickr API key; the setup screen walks you through it. Any other host that takes a file upload can be added by hand or from its ShareX `.sxcu` file.

Screencapper remembers every link a screenshot was uploaded to. If the host allows it, you can delete an upload from the Screenshots page or straight from the chat message. Files that are too big for a host are made smaller first. Saved keys and passwords are stored encrypted.

### Taking screenshots
Tap the screenshot key for a normal screenshot. It's saved in the background, so the game doesn't freeze while the file is written, and a shutter sound lets you know it worked.

Hold the key instead and a capture wheel opens. Point at an option and let go of the key to take it. By default the wheel has:

- Clean, which hides the HUD and your hand
- No chat, which keeps the HUD but hides chat
- 4K, a high-res shot at 3840 × 2160
- Panorama, a 360° shot you can set as your title screen background
- Timer, which waits 3 seconds and then takes a clean shot

You can change, reorder or add options in the settings (8K or a size of your own, for example) and give each one its own compression.

Screencapper can also take a panorama every time you leave a world and use it as your title screen, or take a screenshot when you earn an advancement. Both are off by default.

### Editing
Open a screenshot from the Screenshots page and press Edit. You can crop it (freely or to a ratio like 16:9), rotate and flip it, draw on it, add arrows, boxes and numbered steps, write text, blur out anything private, or cut out a piece and move it. Everything you add stays editable until you save, and the original is never touched: the edit is saved as a new copy next to it, ready to copy or upload.

### Naming and organising
File names follow a template you can change, using things like the date, world, dimension or player. A `/` in the template sorts screenshots into folders. You can rename screenshots, tag them, put them in albums and add notes, then search by `tag:` or `album:`.

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
- Zetvue
  - The shutter sound, made for Screencapper
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
