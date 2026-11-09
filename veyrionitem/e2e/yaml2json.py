import sys, json, yaml
json.dump(yaml.safe_load(open(sys.argv[1], encoding="utf-8")), open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False)
