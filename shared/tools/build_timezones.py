"""Build shared/timezones/us-timezones.json from timezone-boundary-builder data.

The output holds simplified polygons for every US time zone (the 50 states, Puerto Rico, the US
Virgin Islands, Guam, the Northern Mariana Islands and American Samoa). The Android and iOS apps
use it to find a location's time zone offline, for the daily forecast warning, and to tell whether
a location is inside NWS coverage: a point in no US zone is outside it.

Usage, from the repository root:

    python3 -m venv .venv-tools && .venv-tools/bin/pip install -r shared/tools/requirements.txt
    .venv-tools/bin/python shared/tools/build_timezones.py

Downloads are cached (see --cache) and checked against pinned SHA-256 sums. To update to a new
release, change the release constants and sums below, rebuild, and review the diff. Every build is
checked against the known places in shared/fixtures/timezones.json, which the apps test against too.
"""

import argparse
import hashlib
import io
import json
import tarfile
import urllib.request
import zipfile
from pathlib import Path

import shapely
from shapely.geometry import MultiPolygon, Polygon, shape

BOUNDARY_RELEASE = "2026f"
BOUNDARY_URL = (
    "https://github.com/evansiroky/timezone-boundary-builder/releases/download/"
    f"{BOUNDARY_RELEASE}/timezones.geojson.zip"
)
BOUNDARY_SHA256 = "2d160501c844d382233f30b0cd0db2533fe17f93a118a3d38959fe73e3ac20c3"

TZDATA_RELEASE = "2026f"
TZDATA_URL = f"https://data.iana.org/time-zones/releases/tzdata{TZDATA_RELEASE}.tar.gz"
TZDATA_SHA256 = "01b4a91cec5167ed48c11e8ab78f2048cead78ce94b0cafcad0ae8c5a3044d3d"

# zone.tab country codes for the areas NWS covers
COUNTRY_CODES = {"US", "PR", "VI", "GU", "MP", "AS"}

# about 110 m north to south, less east to west; accepted near zone borders, since the apps only warn
SIMPLIFY_TOLERANCE_DEGREES = 0.001
COORDINATE_DECIMALS = 4

FORMAT_VERSION = 1


def fetch(url: str, sha256: str, cache: Path) -> bytes:
    """Returns the file at url, from the cache when present, after checking its SHA-256."""
    path = cache / url.rsplit("/", 1)[1]
    if not path.exists():
        cache.mkdir(parents=True, exist_ok=True)
        print(f"downloading {url}")
        with urllib.request.urlopen(url) as response:
            path.write_bytes(response.read())
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != sha256:
        raise SystemExit(f"SHA-256 mismatch for {path}: expected {sha256}, got {actual}")
    return data


def us_zone_ids(tzdata_tar: bytes) -> list[str]:
    """Zone ids that zone.tab lists for the covered country codes."""
    with tarfile.open(fileobj=io.BytesIO(tzdata_tar)) as tar:
        member = tar.extractfile("zone.tab")
        if member is None:
            raise SystemExit("zone.tab not found in tzdata")
        rows = member.read().decode().splitlines()
    fields = (row.split("\t") for row in rows if row and not row.startswith("#"))
    return sorted(f[2] for f in fields if f[0] in COUNTRY_CODES)


def load_boundaries(boundary_zip: bytes, zone_ids: list[str]) -> dict[str, MultiPolygon]:
    """The boundary of each zone id, as a MultiPolygon."""
    with zipfile.ZipFile(io.BytesIO(boundary_zip)) as archive:
        name = next(n for n in archive.namelist() if n.endswith("json"))
        features = json.loads(archive.read(name))["features"]
    by_id = {f["properties"]["tzid"]: f["geometry"] for f in features}
    missing = [z for z in zone_ids if z not in by_id]
    if missing:
        raise SystemExit(f"zones missing from boundary data: {missing}")
    return {z: as_multipolygon(shape(by_id[z])) for z in zone_ids}


def as_multipolygon(geometry) -> MultiPolygon:
    if geometry.is_empty:
        return MultiPolygon()
    if isinstance(geometry, Polygon):
        return MultiPolygon([geometry])
    if isinstance(geometry, MultiPolygon):
        return geometry
    raise SystemExit(f"unexpected geometry type {geometry.geom_type}")


def simplify(boundaries: dict[str, MultiPolygon]) -> dict[str, MultiPolygon]:
    """Simplifies all zones together, so borders shared by two zones stay shared, with no gaps."""
    ids = list(boundaries)
    simplified = shapely.coverage_simplify(
        [boundaries[z] for z in ids], SIMPLIFY_TOLERANCE_DEGREES, simplify_boundary=True
    )
    return {z: as_multipolygon(g) for z, g in zip(ids, simplified)}


def snap_to_grid(zones: dict[str, MultiPolygon]) -> dict[str, MultiPolygon]:
    """Rounds every point to COORDINATE_DECIMALS places.

    set_precision also removes the points that round onto their neighbor and the rings and polygons
    that collapse to nothing, and returns valid geometry. Points shared by two zones round the
    same way, so shared borders stay shared.
    """
    grid = 10.0**-COORDINATE_DECIMALS
    return {z: as_multipolygon(shapely.set_precision(g, grid)) for z, g in zones.items()}


def flat_ring(coords) -> list[float]:
    """A ring as a flat [lon, lat, lon, lat, ...] list, without the repeated closing point.

    The points are already on the grid; round() only keeps float noise such as
    -122.33330000000001 out of the file.
    """
    return [round(value, COORDINATE_DECIMALS) for point in list(coords)[:-1] for value in point]


def polygon_entry(polygon: Polygon) -> dict:
    min_lon, min_lat, max_lon, max_lat = (round(v, COORDINATE_DECIMALS) for v in polygon.bounds)
    return {
        "bbox": [min_lon, min_lat, max_lon, max_lat],
        "rings": [flat_ring(polygon.exterior.coords)] + [flat_ring(r.coords) for r in polygon.interiors],
    }


def build_output(zones: dict[str, MultiPolygon]) -> dict:
    empty = [z for z, g in zones.items() if g.is_empty]
    if empty:
        raise SystemExit(f"zones with no area left after simplifying: {empty}")
    entries = []
    for zone_id in sorted(zones):
        polygons = sorted((polygon_entry(g) for g in zones[zone_id].geoms), key=lambda p: p["bbox"])
        entries.append({"id": zone_id, "polygons": polygons})
    return {
        "description": (
            "US time zone boundaries for the RidgeCast apps, generated by "
            "shared/tools/build_timezones.py. Do not edit by hand. Each ring is a flat list "
            "[lon, lat, lon, lat, ...] without the closing point; the first ring of a polygon is "
            "its outer boundary and the rest are holes. bbox is [min lon, min lat, max lon, max lat]. "
            "Contains information from timezone-boundary-builder, which is made available under "
            "the Open Database License (ODbL)."
        ),
        "format_version": FORMAT_VERSION,
        "source": {
            "name": "timezone-boundary-builder",
            "release": BOUNDARY_RELEASE,
            "url": BOUNDARY_URL,
            "license": "ODbL-1.0",
        },
        "tzdata_release": TZDATA_RELEASE,
        "simplify_tolerance_degrees": SIMPLIFY_TOLERANCE_DEGREES,
        "zones": entries,
    }


def load_checks(path: Path) -> list[dict]:
    """The known places from shared/fixtures/timezones.json."""
    return json.loads(path.read_text())["cases"]


def points(ring: list[float]) -> list[tuple[float, float]]:
    """A flat [lon, lat, lon, lat, ...] ring as (lon, lat) pairs."""
    return list(zip(ring[0::2], ring[1::2]))


def polygon_from_entry(entry: dict) -> Polygon:
    """The Polygon an output entry describes: its first ring is the outer boundary, the rest holes."""
    outer, *holes = entry["rings"]
    return Polygon(points(outer), [points(h) for h in holes])


def spot_check(output: dict, checks: list[dict]) -> list[str]:
    """Descriptions of the checks that failed.

    Rebuilds the geometry from the output, so it tests what is written to the file, not the
    shapes before rounding and flattening.
    """
    zones = {
        z["id"]: MultiPolygon([polygon_from_entry(p) for p in z["polygons"]])
        for z in output["zones"]
    }
    for geometry in zones.values():
        shapely.prepare(geometry)

    def zone_at(lat: float, lon: float) -> str | None:
        return next((z for z, g in zones.items() if shapely.contains_xy(g, lon, lat)), None)

    return [
        f"{c['name']}: expected {c['zone']}, got {actual}"
        for c in checks
        if (actual := zone_at(c["latitude"], c["longitude"])) != c["zone"]
    ]


def write_output(output: dict, path: Path) -> None:
    """Writes one zone per line, so a rebuild shows up in a diff zone by zone."""
    header = {k: v for k, v in output.items() if k != "zones"}
    lines = [json.dumps(z, separators=(",", ":")) for z in output["zones"]]
    text = json.dumps(header, indent=2)[:-2] + ',\n  "zones": [\n' + ",\n".join(lines) + "\n  ]\n}\n"
    json.loads(text)  # the output must still be valid JSON
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def main() -> None:
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ridgecast-timezones")
    parser.add_argument("--output", type=Path, default=here.parent / "timezones" / "us-timezones.json")
    parser.add_argument("--checks", type=Path, default=here.parent / "fixtures" / "timezones.json")
    args = parser.parse_args()

    zone_ids = us_zone_ids(fetch(TZDATA_URL, TZDATA_SHA256, args.cache))
    boundaries = load_boundaries(fetch(BOUNDARY_URL, BOUNDARY_SHA256, args.cache), zone_ids)
    output = build_output(snap_to_grid(simplify(boundaries)))

    checks = load_checks(args.checks)
    failures = spot_check(output, checks)
    if failures:
        raise SystemExit("spot checks failed:\n  " + "\n  ".join(failures))

    write_output(output, args.output)
    vertices = sum(len(r) // 2 for z in output["zones"] for p in z["polygons"] for r in p["rings"])
    polygons = sum(len(z["polygons"]) for z in output["zones"])
    size_kb = args.output.stat().st_size / 1024
    print(f"wrote {args.output}: {len(zone_ids)} zones, {polygons} polygons, {vertices} vertices, {size_kb:.0f} KB")
    print(f"all {len(checks)} spot checks passed")


if __name__ == "__main__":
    main()
