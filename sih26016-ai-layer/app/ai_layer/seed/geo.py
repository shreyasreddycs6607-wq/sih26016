"""Rough real-world bounding boxes for our four chosen districts, so parcel
coordinates land inside actual Karnataka geography rather than the ocean.
(lat_min, lat_max, lon_min, lon_max) — approximate, good enough for a demo
map, not survey-grade."""

import random

from shapely.geometry import Point

DISTRICT_BOUNDS = {
    "Bengaluru Rural": (13.05, 13.35, 77.35, 77.75),
    "Tumakuru": (13.10, 13.60, 76.90, 77.30),
    "Ramanagara": (12.60, 12.90, 77.10, 77.45),
    "Kolar": (12.95, 13.35, 78.05, 78.35),
}


def random_point_in_district(district_name: str, rng: random.Random) -> Point:
    lat_min, lat_max, lon_min, lon_max = DISTRICT_BOUNDS[district_name]
    lat = rng.uniform(lat_min, lat_max)
    lon = rng.uniform(lon_min, lon_max)
    return Point(lon, lat)  # shapely/PostGIS order is (x=lon, y=lat)
