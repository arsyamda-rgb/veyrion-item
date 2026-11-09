# Test plan: VeyrionItem

Build: working tree (no git)  Date: 2026-11-08  Env: pack generator run locally (Java 21), plugin NOT run (no Paper server / API offline)

Playwright does not apply here (Minecraft plugin, no web UI). Automated = `e2e/PackE2E.java` + `e2e/validate_packs.py`.
Run: `java /tmp/Compile.java` equivalent (compile src/main/java/.../pack), then `java -cp <classes> e2e/PackE2E.java`
and `python3 e2e/validate_packs.py /tmp/vi-e2e/main`.

Result column: PASS / FAIL / blank = not run yet.

## Automated (pack generator)

| case | flow | type | steps | expected | auto | result |
| --- | --- | --- | --- | --- | --- | --- |
| F01-H1 | generate packs | happy | generate 1.21.1..1.21.11 | 11 zips + manifest row each | e2e | PASS |
| F01-H2 | | happy | inspect each zip | legacy models <=1.21.3, items/ >=1.21.4, never both | e2e | PASS |
| F01-H3 | | happy | read pack.mcmeta | correct pack_format; min/max_format only >=1.21.9 | e2e | PASS |
| F01-H4 | | happy | python validator on all zips | all JSON valid, every referenced model/texture/font file exists | python | PASS |
| F01-E1 | | edge: run twice | generate twice | byte-identical zips | e2e | PASS |
| F01-E2 | | edge: shared formats | compare SHA-1 | same pack_format = same bytes, 8 distinct formats | e2e | PASS |
| F01-E3 | | edge: empty content | no items/blocks/fonts | valid pack, no items/ file | e2e | PASS |
| F01-E4 | | edge: emoji/accents/CJK/quotes | description with them | survives round trip | e2e+python | PASS |
| F01-E5 | | edge: shuffled input | items in random cmd order | overrides/thresholds sorted | e2e | PASS |
| F01-E6 | | edge: user pack.mcmeta | file in resource_pack/ | ignored, generated wins | e2e | PASS |
| F01-E7 | | edge: user font default.json | user file with own provider | user provider kept | e2e | PASS (was FAIL, BUG-001) |
| F01-E8 | | edge: merged fonts | user + generated | both + space provider, valid JSON | e2e | PASS |
| F01-N1 | | negative: bad versions | reversed range, unknown, spaces | clear errors / trimmed | e2e | PASS |
| F01-N2 | | negative: missing source file | path does not exist | IOException, no silent pack | e2e | PASS |
| F01-N3 | | negative: item w/o texture+model | | error names the item | e2e | PASS |
| F01-N4 | | negative: malformed user font JSON | | IOException mentioning default.json | e2e | PASS |
| F01-N5 | | Json.parse | escapes, surrogates, truncated, trailing data | round trip / rejected | e2e | PASS |
| F01-E9 | | edge: user models/item/<base>.json | user override file | kept/merged | e2e | FAIL (BUG-002, S3, not yet in suite: repro script only) |
| F03-H1 | custom blocks | happy | read note_block.json | 1150 variants, custom mapped for powered true+false | e2e | PASS |
| F03-E1 | | edge: pool | states 0..549 | unique, harp unused | e2e | PASS |
| F03-E2 | | edge: capacity | state 549 / 550 | 549 ok, 550 throws (loader guards) | e2e | PASS |
| F03-E3 | | edge: own block model | | used as is, no cube generated | e2e | PASS |
| F05-H1 | font images | happy | Spaces.of(n), n -3000..3000 | cursor moves exactly n px | e2e | PASS |
| F05-H2 | | happy | font provider | 1 code point per glyph + space provider | e2e | PASS |

## Automated (ItemsAdder import, e2e/ImportE2E.java)

Fixture: e2e/fixtures/ia_sample.yml (hand-written, IA style). Real sample: naed_cosmeticsv1.zip uploaded by the user (1 item, Blockbench model, hat).

| case | flow | type | steps | expected | auto | result |
| --- | --- | --- | --- | --- | --- | --- |
| I-H1..H8 | import | happy | fixture | detection, namespace, counts, colors, .png fix, block, furniture+hitbox, font image, hat | e2e | PASS |
| I-E1 | | edge: unsupported keys | durability, armor, hardness, REAL_WIRE, 2 textures, furniture.solid, broken, recipes | every one reported in notes | e2e | PASS |
| I-E2 | | edge: native doc | | not detected as IA | e2e | PASS |
| I-E3 | | edge: garbage | null/str items, numeric namespace | no crash | e2e | PASS |
| I-E4 | | edge: mixed file | IA + native item | both loaded | e2e | PASS |
| I-E5 | | edge: legacy colors | section sign, uppercase, bad hex, trailing & | correct | e2e | PASS |
| R-H1 | real sample | happy | resourcepack/<ns>/... | mapped to assets/<ns>/... | e2e | PASS |
| R-H2 | | happy | config | ns naed_cosmetics, model item/eye_scar, hat, name | e2e | PASS |
| R-E1 | | edge | | permission + categories reported | e2e | PASS |
| R-H3 | | happy | 11 versions | model+texture in zip, wired to custom_model_data; validator OK | e2e+python | PASS |

## Static check (plugin code)

| case | what | result |
| --- | --- | --- |
| S-1 | compile all sources without Paper API, ignore Bukkit-symbol errors | no mismatch between our own classes found; Bukkit signatures NOT verified |

## Manual, in game (needs Paper server + client; NOT run)

Run each on servers 1.21.1 (base), 1.21.4, 1.21.8, 1.21.11 with the matching `pack-<version>.zip`.

| case | flow | type | steps | expected | auto | result |
| --- | --- | --- | --- | --- | --- | --- |
| F09-H1 | ItemsAdder import | happy | copy naed_cosmetics to plugins/VeyrionItem/contents/, /vi reload, /vi give <me> naed_cosmetics:eye_scar | item with the Blockbench model, "Dragon Eye Scar" in red | manual | |
| F09-H2 | | happy | right click with it / click it onto the helmet slot | worn on head with the model's head display | manual | |
| F09-E1 | | edge: /vi report | | lists permission + categories as not supported | manual | |
| F09-E2 | | edge: stack of 2, helmet occupied | | one worn, swap works, nothing lost | manual | |
| F09-E3 | | edge: pack for 1.21.4+ | /vi generate 1.21.4 | model shows (items/paper.json range_dispatch) | manual | |
| F00-H1 | startup | happy | build jar, start server, check log | "Loaded N items..." no stack trace, demo folder created | manual | |
| F00-N1 | | negative: texture missing in config | rename a png | warning names the file, rest still loads | manual | |
| F02-H1 | items | happy | /vi give <me> demo:ruby | ruby texture, name + lore | manual | |
| F02-E1 | | edge: rename in anvil / stack two | | look stays, items stack | manual | |
| F02-E2 | | edge: amount 0, 65, "abc" | /vi give ... | clamped 1..64, "Amount must be a number" | manual | |
| F02-N1 | | negative: unknown item / offline player | | message, no exception | manual | |
| F03-H1 | blocks | happy | place demo:ruby_block | shows ruby cube, not a note block | manual | |
| F03-E1 | | edge: break survival / creative | | survival drops custom item, creative none | manual | |
| F03-E2 | | edge: place/remove block above+below | | look unchanged | manual | |
| F03-E3 | | edge: right click block, place against it | | no retune, placing still works | manual | |
| F03-E4 | | edge: redstone power | | look unchanged, no sound | manual | |
| F03-E5 | | edge: vanilla note block | place a normal note block | still a working harp note block | manual | |
| F03-E6 | | edge: explosion / piston / water | | check what drops and what survives | manual | |
| F04-H1 | furniture | happy | place armchair | model visible, hitbox solid to click | manual | |
| F04-E1 | | edge: punch / rotate | | removed + item drops / turns 45 deg | manual | |
| F04-E2 | | edge: restart server, unload chunk | | furniture still there | manual | |
| F04-E3 | | edge: place on non-passable spot, creative | | refused / item not consumed | manual | |
| F04-E4 | | edge: two players punch at once | | single drop, no dupe | manual | |
| F05-H1 | font images | happy | chat ":heart:" | icon shown | manual | |
| F05-N1 | | negative: no itemforge.fontimage permission | | stays plain text | manual | |
| F06-H1 | GUI | happy | /vi menu demo:shop | background aligned with chest, title readable | manual | |
| F06-E1 | | edge: shift-click, drag, number keys, hotbar swap | | nothing can be taken out | manual | |
| F06-E2 | | edge: background id wrong | | opens without background | manual | |
| F06-N1 | | negative: console /vi menu without player | | message | manual | |
| F07-H1 | reload / ids | happy | /vi reload, restart | same cmd/state/glyph (data/ids.yml) | manual | |
| F07-E1 | | edge: add item above others in yml | | existing items keep their look | manual | |
| F07-E2 | | edge: delete data/ids.yml | | documented: ids reassign, old items change look | manual | |
| F08-N1 | commands | negative: non-op /vi | | "No permission." | manual | |
| F08-E1 | | edge: tab completion each subcommand | | sensible suggestions | manual | |
