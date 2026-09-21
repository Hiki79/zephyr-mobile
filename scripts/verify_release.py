"""Regression guard for the Android Class.getPackage() crash in the minified APK."""
from pathlib import Path
import sys
import zipfile

mapping = Path(sys.argv[1]).read_text(encoding="utf-8")
for name in ("org.yaml.snakeyaml.TypeDescription", "org.yaml.snakeyaml.introspector.PropertySubstitute"):
    matches = [line for line in mapping.splitlines() if line.startswith(name + " -> ")]
    if len(matches) != 1:
        raise RuntimeError(f"Missing class mapping: {name}")
    target = matches[0].split(" -> ")[1].removesuffix(":")
    if not target.startswith(name.rsplit(".", 1)[0] + "."):
        raise RuntimeError(f"Package removed: {name} -> {target}")
    print(f"Package retained: {name} -> {target}")

with zipfile.ZipFile(sys.argv[2]) as apk:
    for name in ("GeoIP.dat", "GeoSite.dat", "geoip.metadb", "ASN.mmdb"):
        info = apk.getinfo("assets/geodata/" + name)
        assert info.file_size > 0, name
        print(f"Bundled {name}: {info.file_size} bytes")
