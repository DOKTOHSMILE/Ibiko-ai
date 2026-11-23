#!/usr/bin/env python3
"""Writes the "files" list of /api/v1/knowledge/manifest for knowledge files to upgrade on phones.

Usage: tools/knowledge_manifest.py <version> <url-prefix> <file>...  (paths as the app knows them: knowledge/...)
Each file may be plain (knowledge/facts.tsv) or coded (knowledge/facts.tsv.ibk): the app reads a plain file before
its coded form, so new knowledge needs no encoding. Serve each file at <url-prefix>/<path>; the phone checks the
SHA-256 before it uses it, and takes a file only when its version is newer than the one it has.
"""
import hashlib
import json
import sys


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    version, prefix, paths = int(argv[1]), argv[2].rstrip("/"), argv[3:]
    files = []
    for path in paths:
        if not path.startswith("knowledge/") or ".." in path:
            raise SystemExit("not a knowledge path: " + path)
        with open(path, "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        files.append({"path": path, "version": version, "sha256": digest, "url": prefix + "/" + path})
    print(json.dumps({"files": files}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
