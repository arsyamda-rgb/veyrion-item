"""Independent check of generated packs (does not use our JSON writer).
Usage: python3 e2e/validate_packs.py /tmp/vi-e2e/main
Checks: every .json/.mcmeta parses, pack_format matches manifest, every model/texture/font file referenced exists in the zip."""
import json, sys, zipfile, re, pathlib

out = pathlib.Path(sys.argv[1])
expected_desc = (out.parent / "expected_description.txt").read_text(encoding="utf-8")
manifest = {}
for line in (out / "manifest.txt").read_text().splitlines()[1:]:
    p = line.split()
    manifest[p[0]] = int(p[1])

def loc(ref, kind, ext):
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return ns, f"assets/{ns}/{kind}/{path}{ext}"

errors = 0
def err(msg):
    global errors; errors += 1; print("  ERR", msg)

for version, fmt in manifest.items():
    z = zipfile.ZipFile(out / f"pack-{version}.zip")
    names = set(z.namelist())
    docs = {}
    for n in names:
        if n.endswith(".json") or n.endswith(".mcmeta"):
            try: docs[n] = json.loads(z.read(n).decode("utf-8"))
            except Exception as e: err(f"{version}: {n} invalid JSON: {e}")
    mc = docs.get("pack.mcmeta", {}).get("pack", {})
    if mc.get("pack_format") != fmt: err(f"{version}: pack_format {mc.get('pack_format')} != manifest {fmt}")
    if mc.get("description") != expected_desc: err(f"{version}: description mangled: {mc.get('description')!r}")

    def need_model(ref, ctx):
        ns, path = loc(ref, "models", ".json")
        if ns != "minecraft" and path not in names: err(f"{version}: {ctx} -> missing model {path}")
    def need_texture(ref, ctx):
        ns, path = loc(ref, "textures", ".png")
        if ns != "minecraft" and path not in names: err(f"{version}: {ctx} -> missing texture {path}")

    for n, d in docs.items():
        if n.startswith("assets/minecraft/items/"):
            for e in d["model"]["entries"]: need_model(e["model"]["model"], n)
        elif n.startswith("assets/minecraft/models/item/"):
            for o in d.get("overrides", []): need_model(o["model"], n)
        elif n == "assets/minecraft/blockstates/note_block.json":
            for k, v in d["variants"].items(): need_model(v["model"], n)
        elif n == "assets/minecraft/font/default.json":
            for p in d["providers"]:
                if p["type"] == "bitmap":
                    ns, path = loc(p["file"], "textures", "")
                    if f"assets/{ns}/textures/{p['file'].split(':',1)[1]}" not in names: err(f"{version}: font file missing {p['file']}")
                    if len(p["chars"]) != 1 or len(p["chars"][0]) != 1: err(f"{version}: bad chars {p['chars']}")
        elif n.endswith(".json") and "/models/" in n:
            for t in d.get("textures", {}).values(): need_texture(t, n)
    print(f"ok  {version} (format {fmt}, {len(names)} files, {len(docs)} json)" if errors == 0 else f"... {version} checked")
print("VALIDATION", "FAILED" if errors else "PASSED", f"({errors} errors)")
sys.exit(1 if errors else 0)
