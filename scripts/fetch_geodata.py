"""Bundle upstream rule databases at an immutable revision, with verified digests."""
import hashlib
from pathlib import Path
from urllib.request import urlopen

REVISION = "d78778f8285be4d644349443017d273290a37c30"
FILES = {
    "geoip.dat": ("GeoIP.dat", "f3370cf391831bb01e1e662df88596164d136e7d9f81a91c00bae26587e02d72"),
    "geosite.dat": ("GeoSite.dat", "dc1657fd28db7e8df305f6363b65a822736a2225a620f8dcd777d7a71ff41f54"),
    "geoip.metadb": ("geoip.metadb", "cbd369ea501c66e8f4926995157f74d284e2649510ab62bdb5c0ab124aa065e0"),
    "GeoLite2-ASN.mmdb": ("ASN.mmdb", "7dcc428e82ef1e9514de68e7413afbf58cccfa56de760448a69768a0b08f5950"),
}

def main():
    dest = Path(__file__).resolve().parents[1] / "app/src/main/assets/geodata"
    dest.mkdir(parents=True, exist_ok=True)
    for source, (name, digest) in FILES.items():
        path = dest / name
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == digest:
            continue
        url = f"https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/{REVISION}/{source}"
        with urlopen(url, timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError(f"Checksum mismatch: {source}")
        path.write_bytes(data)
        print(f"Verified {name} ({len(data)} bytes)")

if __name__ == "__main__":
    main()
