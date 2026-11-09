# Architecture

- `pack/`   pure Java, no Bukkit. PackContent -> PackBuilder (one version) -> PackGenerator (many versions, zips + manifest.txt).
- `content/` loader for contents/<ns>/configs/**.yml + contents/<ns>/resource_pack/ (copied into every pack). Registry Stable ids in data/ids.yml (custom_model_data, note block state, glyph codepoint).
- `listener/` BlockListener (note block states), FurnitureListener (ItemDisplay + Interaction), ChatListener (:glyph:).
- `gui/` MenuService, chest menus with a font-image background in the title.
- `command/` /vi give | menu | list | reload | generate [version|all] | versions

Version strategy
- Compile against Paper 1.21.1 (base). Items use ItemMeta#setCustomModelData(int); newer servers map it to the first float of the custom_model_data component.
- Pack differs per version: <=1.21.3 "overrides" models, >=1.21.4 items/ range_dispatch, >=1.21.9 min/max_format.
