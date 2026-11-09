# Bugs

### BUG-001: user's own font file in resource_pack/ was silently replaced
- Severity: S3
- Flow / case: F01 / F01-E7
- Screen: pack generator
- Build: working tree 2026-11-08, Java 21

Steps
1. Put assets/minecraft/font/default.json with your own bitmap provider in contents/<ns>/resource_pack/
2. Generate packs

Expected: your provider and the generated ones (font images, space glyphs) are all in the pack
Actual: generated default.json overwrote the file, your provider was gone, no warning
Evidence: failing test F01-E7 (before fix), passing after; F01-E8, F01-N4, F01-N5 added
Suspected cause: PackBuilder wrote the generated font file over the copied one
Status: fixed (Json.parse added, providers merged; malformed user file now fails with a clear IOException)

### BUG-002: user's own vanilla-override files are silently replaced (models/item/<base>.json, items/<base>.json, blockstates/note_block.json)
- Severity: S3 (workaround: define the item through VeyrionItem instead of editing the vanilla file by hand)
- Flow / case: F01 / F01-E9
- Screen: pack generator
- Build: working tree 2026-11-08

Steps
1. Put assets/minecraft/models/item/paper.json with an override for custom_model_data 777 in resource_pack/
2. Generate the 1.21.1 pack and define one paper item

Expected: override 777 and the generated override both present
Actual: only the generated one ("user's override 777 survived: false")
Evidence: reproduction script output (see summary), no automated test yet
Status: open. Fix needs merging overrides (<=1.21.3), range_dispatch entries (>=1.21.4) and blockstate variants.

## To check (not reproduced, do not count as bugs)
- Base materials that are block items (STONE etc.): legacy (<=1.21.3) base file declares layer0 `minecraft:item/stone`; I believe vanilla has no such texture, so the default look may be broken. Reproduced only that the file is written that way (output seen), not the rendering.
- Font codepoints start at U+E000 and have no upper guard; space glyphs live at U+F800-F819, so ~6144 font images would collide.
- Explosions, pistons, water, fire on custom note blocks: they do not go through BlockBreakEvent, so they may drop a vanilla note block instead of the custom item.
- Two players punching the same furniture in the same tick may drop two items.
- PrePlayerAttackEntityEvent on Interaction entities, setCustomModelData(int) mapping to custom_model_data float 0 on 1.21.4+ servers, and the pack_format numbers 69/75: assumptions, not verified offline.
- The plugin classes (listeners, loader, commands) have never been compiled against the real Paper API.
