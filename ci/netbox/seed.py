#!/usr/bin/env python3
"""Seed deterministic, disposable records for the Android E2E journey."""

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
        {"slug": "ci-e2e-manufacturer"},
        {"name": "CI E2E Manufacturer", "slug": "ci-e2e-manufacturer"},
    )
    device_type = create_or_get(
        base_url,
        token,
        "api/dcim/device-types/",
        {"slug": "ci-e2e-device-type"},
        {
            "manufacturer": manufacturer["id"],
            "model": "CI E2E Device Type",
            "slug": "ci-e2e-device-type",
        },
    )
    role = create_or_get(
        base_url,
        token,
        "api/dcim/device-roles/",
        {"slug": "ci-e2e-role"},
        {"name": "CI E2E Role", "slug": "ci-e2e-role", "color": "9e9e9e"},
    )
    site = create_or_get(
        base_url,
        token,
        "api/dcim/sites/",
        {"slug": "ci-e2e-site"},
        {"name": "CI E2E Site", "slug": "ci-e2e-site", "status": "active"},
    )
    device = create_or_get(
        base_url,
        token,
        "api/dcim/devices/",
        {"name": "CI E2E Device"},
        {
            "name": "CI E2E Device",
            "device_type": device_type["id"],
            "role": role["id"],
            "site": site["id"],
            "status": "active",
            "asset_tag": "CI-E2E-001",
        },
    )
    print(
        json.dumps(
            {
                "manufacturer": manufacturer["id"],
                "device_type": device_type["id"],
                "role": role["id"],
                "site": site["id"],
                "device": device["id"],
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
        print(f"NetBox E2E seed failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
