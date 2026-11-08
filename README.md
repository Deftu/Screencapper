# Screencapper

Screenshot previews, uploads and a screenshot manager for Minecraft.

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

Imgur works out of the box. You can also add your own hosts: any host that accepts an image over HTTP works, and a ShareX `.sxcu` file fills in the settings for you. Screencapper remembers every link a screenshot was uploaded to.

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

---

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/8fb6621b-811a-473b-9087-c8c42b50e74c.png)](https://s.deftu.dev/bisect)

---

**This project is licensed under [AGPL-3.0-or-later][agpl]****&copy; 2022 Deftu**

[essential]: https://essential.gg/
[oneconfig]: https://polyfrost.org/projects/oneconfig
[agpl]: https://www.gnu.org/licenses/agpl-3.0.en.html
