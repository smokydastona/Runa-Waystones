# Texture Sizes Guide (Runa Waystones / WaystoneButtonInjector)

This repo ships a handful of GUI textures (mostly under `assets/waystoneinjector/…`) plus a few textures under `assets/waystones/…` that are used for UI integration.

The most common reason for “missing” or ugly UI textures in Minecraft is **the wrong dimensions** (especially when a texture is used as an icon and gets scaled down hard).

## Quick Reference

| Pixel size | Used for | Where | Examples |
|---:|---|---|---|
| **16×16** | Curios slot icon textures (recommended), small UI icons | `assets/<namespace>/textures/slot/` (Curios) and other UI folders | `assets/waystones/textures/gui/inventory_button.png` (16×16) |
| **16×80** | “Mystical” vertical strip textures | `assets/waystoneinjector/textures/gui/mystical/` | `mystic_1.png` … `mystic_26.png` (all 16×80) |
| **16×512** | Sharestone portal color strips | `assets/waystoneinjector/textures/gui/sharestone_portals/` | `red.png`, `blue.png`, … (all 16×512) |
| **64×32** | Waystone menu button textures | `assets/waystoneinjector/textures/gui/buttons/` | `warp_stone.png`, `sharestone.png`, … (all 64×32) |
| **220×36** | Waystone menu overlay textures | `assets/waystoneinjector/textures/gui/overlays/` | `warp_stone.png`, `sharestone.png`, … (all 220×36) |
| **256×256** | GUI item art + UI panels | `assets/waystoneinjector/textures/gui/` and `assets/waystones/textures/gui/` | `warp_stone.png`, `warp_scroll.png`, `waystones/textures/gui/checkbox.png` |
| **256×4096** | Portal animation strips (vertical) | `assets/waystoneinjector/textures/gui/` | `portal_animation.png`, `portstone_portal.png` |
| **273×273** | One-off special waystone art | `assets/waystoneinjector/textures/gui/` | `waystone_mossy.png` |
| **1024×1024** | Large waystone background art | `assets/waystoneinjector/textures/gui/` | `waystone_regular.png`, `waystone_endstone.png`, `sharestone.png` |

## Curios Slot Icons (Important)

Curios expects the slot icon to be a texture located at:

- `assets/<namespace>/textures/slot/<name>.png`

…and referenced from the Curios slot json as:

- `"icon": "<namespace>:slot/<name>"`

Example in this repo:

- Slot JSON: `data/waystoneinjector/curios/slots/waystones.json`
- Icon reference: `waystoneinjector:slot/waystones`
- Texture file: `assets/waystoneinjector/textures/slot/waystones.png`

### Recommended size

- **16×16** is the recommended Curios slot icon size.

### Current note

- The current `assets/waystoneinjector/textures/slot/waystones.png` is **20×20** (it will usually still render, but 16×16 avoids clipping/blur).

## Portal Animations

These are vertical strips (same width as a frame, with frames stacked top-to-bottom):

- `assets/waystoneinjector/textures/gui/portal_animation.png` → **256×4096**
- `assets/waystoneinjector/textures/gui/portstone_portal.png` → **256×4096**

Both are driven by their `.png.mcmeta` files:

- `portal_animation.png.mcmeta`
- `portstone_portal.png.mcmeta`

Current animation settings:

- `frametime: 2`
- `interpolate: true`

Since $4096 / 256 = 16$, these textures are effectively **16 frames** of **256×256** each.

## Waystone Menu Buttons & Overlays

These are the textures used by the mod’s GUI overlays/buttons:

- Buttons: `assets/waystoneinjector/textures/gui/buttons/*.png` → **64×32**
- Overlays: `assets/waystoneinjector/textures/gui/overlays/*.png` → **220×36**

## Sharestone Portal Color Strips

- `assets/waystoneinjector/textures/gui/sharestone_portals/*.png` → **16×512**

These are vertical strips used for portal color effects.

## How To Verify Texture Sizes (PowerShell)

If you add/replace images and want to confirm dimensions before committing:

```powershell
cd "<repo>\\WaystoneButtonInjector"
Add-Type -AssemblyName System.Drawing
Get-ChildItem "src\\main\\resources\\assets" -Recurse -File -Filter *.png |
  ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    [PSCustomObject]@{ Path = $_.FullName; W = $img.Width; H = $img.Height }
    $img.Dispose()
  } |
  Sort-Object W,H,Path |
  Format-Table -AutoSize
```

If a PNG won’t open with `System.Drawing` (some PNG variants), use the WPF decoder:

```powershell
Add-Type -AssemblyName PresentationCore
$p = Resolve-Path "src\\main\\resources\\assets\\waystoneinjector\\textures\\slot\\waystones.png"
$bmp = New-Object System.Windows.Media.Imaging.BitmapImage
$bmp.BeginInit(); $bmp.UriSource = New-Object System.Uri($p); $bmp.CacheOption = 'OnLoad'; $bmp.EndInit()
"$p : $($bmp.PixelWidth)x$($bmp.PixelHeight)"
```
