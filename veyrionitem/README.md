# VeyrionItem

Custom items, blocks, furniture, font images and GUIs for Paper, plus a resource pack generator that outputs
one pack per Minecraft version (1.21.1 - 1.21.11).

## Build (Maven, Java 21)
    mvn clean package
    -> target/VeyrionItem-0.1.0.jar   (compiles against Paper 1.21.1)

## Folder layout on the server
    plugins/VeyrionItem/
    ├── config.yml
    ├── contents/
    │   └── <namespace>/
    │       ├── configs/*.yml                         items, blocks, furniture, font_images, menus
    │       └── resourcepack/<namespace>/             textures/, models/, sounds ... (ItemsAdder layout)
    │           (or resource_pack/assets/<namespace>/ , same thing)
    ├── data/ids.yml                                  permanent ids (custom_model_data, block states, glyphs)
    └── output/pack-1.21.1.zip ... pack-1.21.11.zip + manifest.txt

## Reading ItemsAdder contents
Drop an ItemsAdder `contents/<namespace>/` folder into `plugins/VeyrionItem/contents/` and `/vi reload`.
`configs/*.yml` written in ItemsAdder style (info.namespace, items with `resource:`, font_images with `path:`) are
detected per file and read directly; native VeyrionItem files can sit next to them.
Understood: display_name/lore (legacy & and &#hex colors), resource.material/textures/model_path/generate,
blocks (placed_model REAL_NOTE, others become note blocks), furniture (+hitbox), hat, font_images.
Everything else is listed by `/vi report` (and in the log) instead of being dropped silently.
Verified only against one real sample (naed_cosmetics) and a hand-written one.

## Commands (alias /veyrionitem)
    /vi give <player> <ns:id> [amount]
    /vi menu <ns:id> [player]
    /vi list | report | reload | versions
    /vi generate [version|all]

## Tests (no server needed, Java 21 only)
    # compile the pure-Java pack module, then:
    java -cp <classes> e2e/PackE2E.java          # 22 cases, writes packs to /tmp/vi-e2e
    python3 e2e/validate_packs.py /tmp/vi-e2e/main   # independent JSON + reference check
    java -cp <classes> e2e/ImportE2E.java       # 17 cases, ItemsAdder import (needs e2e/yaml2json.py output, see header)
In-game cases are listed in replica/test-plan.md (manual, not run yet).
