#!/usr/bin/env python3
"""Prepare private CI signing files; accept raw values or backup-file assignments."""
import base64
import binascii
import os
from pathlib import Path
import re
import sys


def value(raw, name):
    raw = raw.strip()
    match = re.search(r"(?m)^" + re.escape(name) + r"\s*=\s*([^\r\n]*)", raw)
    result = match.group(1).strip() if match else raw
    if len(result) >= 2 and result[0] == result[-1] and result[0] in ("'", '"'):
        result = result[1:-1]
    return result


def prepare(directory, key, password):
    encoded = re.sub(r"\s+", "", value(key, "SPACE_SPATIAL_KEYSTORE_B64"))
    password = value(password, "SPACE_SPATIAL_KEYSTORE_PASSWORD")
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise ValueError("SPACE_SPATIAL_KEYSTORE_B64 is not valid Base64") from None
    if not decoded or not password:
        raise ValueError("Signing key or password is empty")
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    for name, data in (("spatial.p12", decoded), ("password.txt", password.encode())):
        path = directory / name
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)


if __name__ == "__main__":
    try:
        prepare(sys.argv[1], os.environ["KEY"], os.environ["PASSWORD"])
    except (KeyError, IndexError, ValueError):
        sys.exit("Unable to prepare signing files: check the keystore Base64 and password secrets.")
