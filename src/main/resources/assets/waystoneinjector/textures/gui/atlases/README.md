# Menu Atlases

This folder contains **one atlas PNG per menu/theme**.

## File naming

- `menu_<type>.png`
  - Examples: `menu_regular.png`, `menu_deepslate.png`, `menu_warp_scroll.png`

## Atlas layout (256x384)

All coordinates are pixel coordinates in the atlas.

- Background (256x256)
  - Region: `x=0..255`, `y=0..255`
  - Used for the main GUI background that gets drawn on top of the portal animation.

- Waystone list overlay (220x36)
  - Region: `x=0..219`, `y=256..291`
  - Used for each entry in the Waystones list.

- Themed server-transfer button (64x32)
  - Region: `x=0..63`, `y=292..323`
  - Used by the injected transfer buttons.

## Notes

- Mystical letters/frames remain separate in `../mystical/`.
- Portal animations remain separate in `../waystone_portals/`, `../sharestone_portals/`, and `../portstone_portal.png`.
- Code falls back to the legacy per-element PNGs if an atlas is missing.
