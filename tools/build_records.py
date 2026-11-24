#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/records.tsv.ibk: the records the encyclopedia names, found by what they are.

"What is the highest mountain in the world?" names no article: the answer (Mount Everest) is in an article whose title
Ibiko would have to know already. This reads every article of the encyclopedia once, at build time, and keeps each
sentence that names a record ("Mount Everest is the highest mountain on Earth", "The Nile is the longest river in
Africa", "Russia is the largest country in the world by area"), keyed by what it is: the superlative, the thing, and
where. Ibiko reads a question the same way and looks the record up, offline, at once.

One line a record: superlative (its base word: high, long, large…), the thing (singular), the scope ("world" for Earth
or the world, "-" when none is said), the article's title, the sentence, and where the thing itself is ("Africa" for "a
river in Africa"), so the world's record holder answers for the place it is in. Coded like the rest (tools/encode_text.py).

Usage: tools/build_records.py
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import encode_encyclopedia as enc  # noqa: E402  the decoder the encyclopedia was coded with

SRC = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "encyclopedia")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "records.tsv")

SUPER = {
    "highest": "high", "tallest": "tall", "longest": "long", "largest": "large", "biggest": "large", "deepest": "deep",
    "smallest": "small", "oldest": "old", "youngest": "young", "hottest": "hot", "coldest": "cold", "fastest": "fast",
    "heaviest": "heavy", "lightest": "light", "shortest": "short", "busiest": "busy", "richest": "rich", "poorest": "poor",
    "wettest": "wet", "driest": "dry", "lowest": "low", "most populous": "populous", "most populated": "populous",
    "most spoken": "spoken", "most visited": "visited", "most widely spoken": "spoken", "strongest": "strong",
}
SUP = "|".join(sorted((re.escape(k) for k in SUPER), key=len, reverse=True))
# "<Title> is (one of )?the (world's |Earth's )?<sup> <thing> (in|on|of) <scope>"
RECORD = re.compile(r"\b(?:is|was|are|and)\s+(one of\s+)?the\s+(?:(world's|Earth's|Africa's|Asia's|Europe's)\s+)?"
                    r"(?:(second|third|fourth|fifth)[- ])?(" + SUP + r")\s+([a-z][a-z-]*(?: (?!(?:in|on|of|that|which|who|by|and|to|with|for|at|except|than|ever|known|from)\b)[a-z][a-z-]*){0,2})"
                    r"(?:\s+(?:in|on|of)\s+(the world|Earth|the Earth|the planet|the universe|the Solar System|the solar system|"
                    r"[A-Z][\w'-]+(?: [A-Z][\w'-]+){0,3}))?(?=[ ,.;(]|$)")

WORLD = {"the world", "earth", "the earth", "the planet", "world's", "earth's"}


def singular(w):
    w = w.strip()
    if w.endswith("ies") and len(w) > 4:
        return w[:-3] + "y"
    if re.search(r"(ches|shes|sses|xes)$", w):
        return w[:-2]
    if w.endswith("s") and not w.endswith("ss") and len(w) > 3:
        return w[:-1]
    return w


def articles():
    vocab_raw = open(os.path.join(SRC, "vocab.ibk"), "rb").read()
    vocab = []
    i = 5
    n, i = varint(vocab_raw, i)
    for _ in range(n):
        ln, i = varint(vocab_raw, i)
        vocab.append(vocab_raw[i:i + ln].decode("utf-8"))
        i += ln
    for shard in range(enc.SHARDS):
        path = os.path.join(SRC, "%03d.ibk" % shard)
        if not os.path.exists(path):
            continue
        b = open(path, "rb").read()
        i = 4
        count, i = varint(b, i)
        for _ in range(count):
            kl, i = varint(b, i)
            key = b[i:i + kl].decode("utf-8")
            i += kl
            kind = b[i]
            i += 1
            how = (kind >> 1) & 3
            title = None
            if how == 0:
                tl, i = varint(b, i)
                title = b[i:i + tl].decode("utf-8")
                i += tl
            elif how == 1:
                title = key[:1].upper() + key[1:]
            elif how == 2:
                title = " ".join(w[:1].upper() + w[1:] for w in key.split(" "))
            pl, i = varint(b, i)
            payload = b[i:i + pl]
            i += pl
            if kind & 1 or title is None:
                continue  # another name for an article
            yield title, enc.decode(payload, vocab)


def varint(b, i):
    n = shift = 0
    while True:
        x = b[i]
        i += 1
        n |= (x & 0x7F) << shift
        shift += 7
        if not x & 0x80:
            return n, i


def record(m, sentence, title, text):
    """The record a match names: (key, row), or None when it isn't a record to keep."""
    if m.group(1) or m.group(3):
        return None  # "one of the", "second longest": not the record itself
    sup = SUPER[m.group(4)]
    thing = singular(m.group(5).strip())
    if len(thing) < 3 or thing in ("one", "part", "number", "amount"):
        return None
    after = sentence[m.end():]
    if re.match(r"\s+(?:that|which|who|where|in which|to)\b", after):
        return None  # "the largest lake that is entirely inside Canada": a narrower record than it seems
    scope = (m.group(6) or m.group(2) or "").strip()
    if not scope and re.match(r"\s+(?:in|on|of)\s", after):
        return None  # a scope it can't read ("in the U.S."): not taken for the world's
    scope = "world" if scope.lower() in WORLD else scope.replace("'s", "") if scope else "-"  # "-": none said
    s = sentence.strip().replace("\t", " ")
    if len(s) > 300:
        s = s[:300].rsplit(" ", 1)[0] + "…"
    w = re.search(r"\bis an? [a-z ]{0,30}?\b(?:in|on|of) ((?:[A-Z][\w'-]+ ?){1,3})", text[:600])
    where = w.group(1).strip() if w else ""  # where the thing itself is ("a river in Africa"), for the scope asked
    return (sup, thing, scope.lower()), "\t".join([sup, thing, scope, title, s, where])


def main():
    seen = set()
    rows = []
    for title, text in articles():
        for sentence in re.split(r"(?<=[.!?])\s+", text)[:6]:  # the opening says what a thing is
            if not sentence.startswith(title.split(" (")[0][:12]) and not sentence.lower().startswith(("it ", "the ", "this ")):
                continue
            # every record a sentence names: "the second largest city in Africa and the most populous city in Nigeria"
            for m in RECORD.finditer(sentence):
                found = record(m, sentence, title, text)
                if found and found[0] not in seen:  # the first article that says it is kept
                    seen.add(found[0])
                    rows.append(found[1])
    rows.sort()
    if "--dry" in sys.argv:
        print("%d records" % len(rows))
        for r in rows:
            if re.search(r"\t(mountain|river|country|desert|ocean|lake|planet|city)\t", r):
                print(r[:200])
        return
    with open(OUT, "w") as f:
        f.write("# Records the encyclopedia names (tools/build_records.py): superlative, thing, scope ('-': none said), title, sentence, where it is\n")
        f.write("\n".join(rows) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d records" % len(rows))


if __name__ == "__main__":
    main()
