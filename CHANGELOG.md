# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.2.0] - 2026-11-21

### Added

- Rename screenshots from the Screenshots page.
- File name templates for every screenshot, with tokens for the date, time, world, dimension, player and game version. A `/` in the template sorts screenshots into folders.
- A full-screen viewer, opened with the full screen button or a double-click, plus zoom and pan in both viewers.
- Copy several selected screenshots at once.
- Tags, albums and notes for screenshots, searchable with `tag:` and `album:`.
- The capture wheel can now be edited: pick its shots and their order, and set each one to hide the HUD, chat or hand, or to use a compression preset.
- Panorama shots from the capture wheel, which can be set as the title screen background.
- An option to take a panorama when you leave a world and make it your title screen background.
- A shutter sound when a screenshot is taken.
- An option to take a screenshot when you earn an advancement. It's off by default.
- A screenshot editor in the gallery. Crop freely or to a ratio like 16:9, rotate and flip, draw with a pen or a see-through marker, add arrows, boxes, circles, lines and numbered steps, add text, blur parts out, or cut out an area and move it. Pick any colour or grab one from the picture. Text works like a design app: drag the corners to scale it, the sides to change its width. Anything you add can be moved, recoloured or deleted later. Edits save as a new copy next to the original, and you can copy or upload it right away.
- Discord webhook and Immich uploader presets.
- Nextcloud uploader: sign in with your browser or an app password, save to a folder, and copy a public link if you want one.
- Google Photos uploader. Sign in with your own Google Cloud app; uploads can go to a Screencapper album, with an optional link only you can open.
- A Flickr uploader. Sign in with your own Flickr API key, choose public or private, and delete uploads from Screencapper.
- Uploads can be deleted from the host when it supports it, including ShareX `DeletionURL`.
- Uploader URLs, headers and form fields accept `{uuid}`, `{fileName}`, `{createdAt}`, `{modifiedAt}` and `{timestamp}`.
- Uploaders can record an upload that has no public link.

### Changed

- Screencapper now needs OneConfig 1.9.0 and PolyCompose 3.0.0.
- The screenshot preview uses Poppins by default instead of the Minecraft font.
- The preview HUD is now named "Screenshot Preview".
- The capture wheel now starts with Clean, No chat, 4K, Panorama and Timer. 8K and custom sizes can still be added.
- The mod list and a new About page credit everyone whose work Screencapper builds on, including Zetvue for the shutter sound.

### Fixed

- Profilers such as Spark no longer show Screencapper as taking most of the frame time.
- The preview could show an older screenshot when two were taken close together.
- Two screenshots taken in the same second could overwrite each other.
- Failed uploads now show the host's reason, not only the HTTP status.
- Imgur uploads send the image file directly instead of as base64 text.

## [2.1.2] - 2026-11-17

- Updated to OneConfig 1.8.0.

## [2.1.1] - 2026-11-11

- Fixed a crash on launch on 1.8.9 when Mellow is installed.
- Screencapper now works alongside other mods that change the same parts of the game, which should prevent similar crashes with other mods.
- Fixed holding the screenshot key taking a burst of screenshots on 1.8.9 when a key repeat mod such as Keyyyyyyyy is installed.
- Fixed high-res screenshots on 1.8.9 showing the full view in every tile when Argentum is installed.
- The capture wheel now uses your physical left and right mouse buttons when they're swapped in Windows.
- Quick taps of Escape or a number key in the capture wheel are no longer missed, and holding Escape no longer opens the pause menu after the wheel closes.
- The capture wheel closes if a menu opens while it's showing.
- The screenshot key can now be changed from Screencapper's settings, under Capture. It stays in sync with the key in Controls.

## [2.1.0] - 2026-11-10

- Screenshots save much faster. The file is written in the background with a quicker PNG encoder, so the game no longer freezes for a second or more after you press the key.
- Hold the screenshot key to open a capture wheel. It takes high-res shots at 4K, 8K or a size you set, a shot with the HUD hidden, or a shot on a 3 second timer. Tapping the key still takes a normal screenshot.
- High-res shots work on every supported version, including Vulkan on 26.2 and newer. On 1.21.4 and older they're rendered in tiles and still include the HUD and your hand.
- Screenshots can be saved smaller with presets, from lossless PNG to small JPEGs. Normal shots, high-res shots and uploads each have their own setting, and Custom lets you choose the format, quality, colours and size. You can keep an untouched copy of every shot as well.
- Screenshots can store when and where they were taken: the world or server, dimension, biome, and optionally your coordinates and recent chat. The Screenshots page can search all of it. Coordinates and chat are off by default, and uploads strip private details unless you allow them.
- Catbox is now a built-in uploader. Files too big for a host are made smaller before uploading, and you can set a size limit for your own hosts.
- The screenshot viewer now fits each screenshot exactly, and the film strip pages through your screenshots with arrows.
- Settings are split into General, Capture, Chat, Uploading, Compression and Metadata.
- Screencapper now refuses to load alongside Fabrishot, Screenshot Compression and Screenshot Message Enhancer, which conflict with it.
- New mod icon.

## [2.0.1] - 2026-11-08

- Fixed the chat card's actions not being clickable on 1.8.9.
- Copy link in the chat card now also works on 1.8.9.

## [2.0.0] - 2026-11-08

- Screencapper now runs on OneConfig and needs it installed. All of its settings are in the OneConfig menu.
- Added a Screenshots page to the OneConfig sidebar, where you browse, search, sort, view, copy, upload, open and delete your screenshots, one at a time or in bulk.
- The screenshot preview is now a HUD. Move, resize and style it in the OneConfig HUD editor. With chat open, hover it to copy, upload or delete, or click it to view the screenshot.
- The chat message for a new screenshot is now a card showing its time, resolution and size, with its actions on a second line. Picking an uploader, confirming a delete and uploading all happen on that line instead of sending new messages.
- You can set up several uploaders, pick a default and upload to any of them. Imgur is built in, any host that takes an image over HTTP works, and ShareX `.sxcu` files can be imported.
- Every link a screenshot is uploaded to is remembered across restarts, and you can upload it again at any time.
- Upload, copy and delete results now show as notifications.
- Added `/screencapper` and `/screenshots`.
- Settings from older versions, including your custom upload host, move over on first launch.
- Supports Minecraft 1.21 to 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11 and 26.1 to 26.3 on Fabric, and 1.8.9 on Ornithe. Minecraft 1.18.2 to 1.20.6 are no longer supported.
- Screencapper is now licensed under AGPL-3.0-or-later.
