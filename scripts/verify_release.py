"""Regression guard for the Android Class.getPackage() crash in the minified APK."""
from pathlib import Path
import sys
import zipfile

mapping = Path(sys.argv[1]).read_text(encoding="utf-8")
# Verify TypeDescription specifically, which was the direct cause of the ct1 crash.
type_desc_matches = [line for line in mapping.splitlines() if line.startswith("org.yaml.snakeyaml.TypeDescription -> ")]
if len(type_desc_matches) != 1:
    raise RuntimeError("Missing class mapping: org.yaml.snakeyaml.TypeDescription")
type_desc_target = type_desc_matches[0].split(" -> ")[1].removesuffix(":")
if not type_desc_target.startswith("org.yaml.snakeyaml."):
    raise RuntimeError(f"Package removed: org.yaml.snakeyaml.TypeDescription -> {type_desc_target}")
print(f"Package retained: org.yaml.snakeyaml.TypeDescription -> {type_desc_target}")

# Verify that NO SnakeYAML classes were flattened into the root package or had their package stripped.
retained_snakeyaml_count = 0
for line in mapping.splitlines():
    if " -> " in line and line.strip().endswith(":"):
        orig, target = line.split(" -> ")
        target = target.removesuffix(":")
        if orig.startswith("org.yaml.snakeyaml."):
            if target.startswith("R8$$REMOVED$$CLASS") or "REMOVED" in target:
                continue
            retained_snakeyaml_count += 1
            if not target.startswith("org.yaml.snakeyaml."):
                raise RuntimeError(f"Package removed for {orig} -> {target}")

if retained_snakeyaml_count == 0:
    raise RuntimeError("No SnakeYAML classes found in release mapping")
print(f"Verified {retained_snakeyaml_count} SnakeYAML classes retained package prefix")

with zipfile.ZipFile(sys.argv[2]) as apk:
    for name in ("GeoIP.dat", "GeoSite.dat", "geoip.metadb", "ASN.mmdb"):
        info = apk.getinfo("assets/geodata/" + name)
        assert info.file_size > 0, name
        print(f"Bundled {name}: {info.file_size} bytes")
