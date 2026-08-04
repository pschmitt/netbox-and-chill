#!/usr/bin/env python3
"""Seed a small, realistic-looking (but obviously fake) rack of devices for Play Store
screenshots against the disposable NetBox fixture. Deliberately separate from seed.py, which
`.github/workflows/android-e2e.yaml` relies on for exact-match assertions ("CI E2E Device") - this
script is free to use nicer names without touching that contract.
"""

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request


def request(base_url, token, path, method="GET", payload=None):
    url = f"{base_url.rstrip('/')}/{path.lstrip('/')}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {token}" if token.startswith("nbt_") else f"Token {token}",
        "Content-Type": "application/json",
    }
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned {error.code}: {body}") from error


def create_or_get(base_url, token, endpoint, lookup, payload):
    query = urllib.parse.urlencode(lookup)
    page = request(base_url, token, f"{endpoint}?{query}")
    results = page.get("results", [])
    if results:
        return results[0]
    return request(base_url, token, endpoint, method="POST", payload=payload)


def seed(base_url, token):
    manufacturer = create_or_get(
        base_url,
        token,
        "api/dcim/manufacturers/",
        {"slug": "acme-networks"},
        {"name": "Acme Networks", "slug": "acme-networks"},
    )
    site = create_or_get(
        base_url,
        token,
        "api/dcim/sites/",
        {"slug": "berlin-data-center"},
        {"name": "Berlin Data Center", "slug": "berlin-data-center", "status": "active"},
    )
    rack = create_or_get(
        base_url,
        token,
        "api/dcim/racks/",
        {"slug": "rack-a1", "site_id": site["id"]},
        {
            "name": "Rack A1",
            "slug": "rack-a1",
            "site": site["id"],
            "status": "active",
            "width": 19,
            "u_height": 42,
        },
    )

    roles = {}
    for name, slug, color in (
        ("Core Switch", "core-switch", "2196f3"),
        ("Edge Router", "edge-router", "4caf50"),
        ("Firewall", "firewall", "f44336"),
    ):
        roles[slug] = create_or_get(
            base_url,
            token,
            "api/dcim/device-roles/",
            {"slug": slug},
            {"name": name, "slug": slug, "color": color},
        )

    device_types = {}
    for model, slug in (
        ("CoreSwitch 9500", "coreswitch-9500"),
        ("EdgeRouter 3200", "edgerouter-3200"),
        ("SecureWall 100", "securewall-100"),
    ):
        device_types[slug] = create_or_get(
            base_url,
            token,
            "api/dcim/device-types/",
            {"slug": slug},
            {"manufacturer": manufacturer["id"], "model": model, "slug": slug, "u_height": 1},
        )

    devices = {}
    for name, type_slug, role_slug, position, asset_tag in (
        ("core-sw-01", "coreswitch-9500", "core-switch", 42, "ACME-1001"),
        ("core-sw-02", "coreswitch-9500", "core-switch", 41, "ACME-1002"),
        ("edge-rtr-01", "edgerouter-3200", "edge-router", 40, "ACME-1003"),
        ("fw-01", "securewall-100", "firewall", 39, "ACME-1004"),
    ):
        devices[name] = create_or_get(
            base_url,
            token,
            "api/dcim/devices/",
            {"name": name},
            {
                "name": name,
                "device_type": device_types[type_slug]["id"],
                "role": roles[role_slug]["id"],
                "site": site["id"],
                "rack": rack["id"],
                "position": position,
                "face": "front",
                "status": "active",
                "asset_tag": asset_tag,
            },
        )

    interfaces = {}
    for device_name, interface_names in {
        "core-sw-01": ("uplink-1", "uplink-2"),
        "core-sw-02": ("uplink-1",),
        "edge-rtr-01": ("uplink-1", "uplink-2"),
        "fw-01": ("uplink-1",),
    }.items():
        for interface_name in interface_names:
            interfaces[(device_name, interface_name)] = create_or_get(
                base_url,
                token,
                "api/dcim/interfaces/",
                {"device_id": devices[device_name]["id"], "name": interface_name},
                {
                    "device": devices[device_name]["id"],
                    "name": interface_name,
                    "type": "1000base-t",
                },
            )

    for left, right, label in (
        (("core-sw-01", "uplink-1"), ("core-sw-02", "uplink-1"), "Core interconnect"),
        (("core-sw-01", "uplink-2"), ("edge-rtr-01", "uplink-1"), "Edge uplink"),
        (("edge-rtr-01", "uplink-2"), ("fw-01", "uplink-1"), "Firewall uplink"),
    ):
        create_or_get(
            base_url,
            token,
            "api/dcim/cables/",
            {"label": label},
            {
                "a_terminations": [
                    {"object_type": "dcim.interface", "object_id": interfaces[left]["id"]}
                ],
                "b_terminations": [
                    {"object_type": "dcim.interface", "object_id": interfaces[right]["id"]}
                ],
                "status": "connected",
                "type": "cat6",
                "label": label,
                "length": 3,
                "length_unit": "m",
            },
        )

    create_or_get(
        base_url,
        token,
        "api/plugins/documents/documents/",
        {"name": "Network overview"},
        {
            "name": "Network overview",
            "external_url": "https://example.invalid/network-overview.pdf",
            "document_type": "diagram",
            "content_type": "dcim.device",
            "object_id": devices["core-sw-01"]["id"],
            "comments": "Demo document for the store listing screenshot fixture.",
        },
    )

    print(
        json.dumps(
            {
                "manufacturer": manufacturer["id"],
                "site": site["id"],
                "rack": rack["id"],
                "primary_device": devices["core-sw-01"]["id"],
                "primary_device_name": "core-sw-01",
                "primary_device_asset_tag": "ACME-1001",
                "topology_plugin": True,
                "documents_plugin": True,
            },
            sort_keys=True,
        )
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token", required=True)
    args = parser.parse_args()
    try:
        seed(args.base_url, args.token)
    except (OSError, RuntimeError, KeyError, json.JSONDecodeError) as error:
        print(f"Nyetbox screenshot seed failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
