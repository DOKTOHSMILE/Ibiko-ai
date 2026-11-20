#!/usr/bin/env python3
"""
Links Ibiko 1's fact tables to the other articles a name may mean: "Titanic" is the ship, but "Titanic (1997 movie)"
has the director. For every article titled "Name (kind)" that has facts, the plain name's entry gets a "see" fact
listing those titles ("Titanic (1997 movie)|Titanic (band)"), so a question the plain name's own facts can't answer
is tried on them. Run after build_facts.py:

    python3 tools/facts_variants.py <encyclopedia-tsv-folder> app/src/main/assets/knowledge/encyclopedia

<encyclopedia-tsv-folder> is the .tsv output of build_encyclopedia.py (for the articles' titles).
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_encyclopedia import SHARDS, key, shard  # noqa: E402
from build_facts import NAMES, code_text, read_vocab  # noqa: E402
from encode_encyclopedia import varint  # noqa: E402

KIND = re.compile(r"^(.*\S) \(([^()]+)\)$")
MOST = 8  # titles kept per name: the commonest kinds first


def vi(b, p):
    n = s = 0
    while True:
        x = b[p[0]]
        p[0] += 1
        n |= (x & 0x7F) << s
        s += 7
        if not x & 0x80:
            return n


def read_shard(path):
    """{key: [(relation id, payload bytes)]}, in order."""
    with open(path, "rb") as f:
        b = f.read()
    assert b[:5] == b"IBK1F", path
    p = [5]
    out = {}
    for _ in range(vi(b, p)):
        kl = vi(b, p)
        k = b[p[0]:p[0] + kl].decode("utf-8")
        p[0] += kl
        facts = []
        for _ in range(vi(b, p)):
            r = b[p[0]]
            p[0] += 1
            pl = vi(b, p)
            facts.append((r, b[p[0]:p[0] + pl]))
            p[0] += pl
        out[k] = facts
    return out


def write_shard(path, entries):
    out = bytearray(b"IBK1F")
    varint(len(entries), out)
    for k, facts in entries.items():
        kb = k.encode("utf-8")
        varint(len(kb), out)
        out += kb
        varint(len(facts), out)
        for r, p in facts:
            out.append(r)
            varint(len(p), out)
            out += p
    with open(path, "wb") as f:
        f.write(out)


KIND_ORDER = ["movie", "film", "TV series", "novel", "book", "album", "song", "band", "video game", "company",
              "singer", "musician", "rapper", "actor", "politician", "footballer"]


def rank(title):
    m = KIND.match(title)
    kind = m.group(2) if m else ""
    for i, k in enumerate(KIND_ORDER):
        if kind.endswith(k):
            return i
    return len(KIND_ORDER)


def main(tsv, encdir):
    facts_dir = os.path.join(encdir, "facts")
    shards = [read_shard(os.path.join(facts_dir, "%03d.ibk" % i)) for i in range(SHARDS)]
    has = set()
    for s in shards:
        has.update(s.keys())
    see = {}
    for i in range(SHARDS):
        with open(os.path.join(tsv, "%03d.tsv" % i), encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t", 2)
                if len(parts) < 3 or parts[2].startswith("=>"):
                    continue
                k, title = parts[0], parts[1]
                m = KIND.match(title)
                if m and k in has:
                    see.setdefault(key(m.group(1)), []).append(title)
    names = list(NAMES)
    if "see" not in names:
        names.append("see")
    rid = names.index("see")
    vocab = read_vocab(os.path.join(encdir, "vocab.ibk"))
    index = {w: i + 1 for i, w in enumerate(vocab)}
    linked = 0
    for base, titles in see.items():
        titles = sorted(set(titles), key=lambda t: (rank(t), t))[:MOST]
        s = shards[shard(base)]
        facts = [f for f in s.get(base, []) if f[0] != rid]
        facts.append((rid, code_text("|".join(titles), index)))
        s[base] = facts
        linked += 1
    for i, s in enumerate(shards):
        write_shard(os.path.join(facts_dir, "%03d.ibk" % i), s)
    rel = bytearray(b"IBK1R")
    varint(len(names), rel)
    for n in names:
        b = n.encode("utf-8")
        varint(len(b), rel)
        rel += b
    with open(os.path.join(facts_dir, "relations.ibk"), "wb") as f:
        f.write(rel)
    print("names linked", linked)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
