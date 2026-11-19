#!/usr/bin/env python3
"""
Codes Ibiko 1's encyclopedia (the .tsv shards from build_encyclopedia.py) into the Ibiko v1 format (IBK1): every word
a number, so the files are smaller and a lookup reads less.

    python3 tools/encode_encyclopedia.py <tsv-shard-folder> app/src/main/assets/knowledge/encyclopedia

vocab.ibk     "IBK1V", varint count, then each word: varint length, UTF-8 bytes. The most used words first (smallest
              numbers). Only words used MIN_USES times or more are in it; the rest are written out where they stand.
NNN.ibk       "IBK1", varint count, then each entry:
                varint key length, key (UTF-8)       the normalised title, as Encyclopedia.key
                byte kind                             bit 0: 0 an article, 1 another name for one (a redirect);
                                                      bits 1-2, how the title is made: 0 written next, 1 the key with
                                                      its first letter capital, 2 each word capital, 3 none (a redirect)
                [varint title length, title (UTF-8)]  only when bits 1-2 are 0
                varint payload length, payload        article: token codes; redirect: the target title (UTF-8)

A text is cut into tokens: runs of letters and digits, and single other characters, each with whether a space comes
before it. A token's code is (v << 3) | (case << 1) | space:
    v      0 = the token itself follows (varint length, UTF-8); otherwise word v-1 of the vocabulary
    case   0 = as in the vocabulary, 1 = first letter capital, 2 = all capitals
    space  1 = a space before it
Every coded text is decoded again here and checked against the original; a token whose case can't be rebuilt
exactly is written out instead.
"""
import os
import re
import sys
from collections import Counter

SHARDS = 512
MIN_USES = 3
TOKEN = re.compile(r"\w+|[^\w\s]", re.UNICODE)


def varint(n, out):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def tokens(text):
    """(token, space before) pairs."""
    out = []
    last = 0
    for m in TOKEN.finditer(text):
        out.append((m.group(0), m.start() > last and text[last:m.start()].strip() == "" and m.start() > 0))
        last = m.end()
    return out


def capital(form):
    """First letter capital, as Java's Character.toUpperCase does it (one letter for one: a letter whose capital is
    two letters, like ß, is left, so the rebuilt word won't match and is written out instead)."""
    first = form[:1]
    up = first.upper()
    return (up if len(up) == 1 else first) + form[1:]


def split_case(t):
    """(vocabulary form, case) for a token."""
    low = t.lower()
    if t == low:
        return t, 0
    if len(t) > 0 and capital(low) == t:
        return low, 1
    if len(t) > 1 and low.upper() == t:
        return low, 2
    return t, 0


def read_shards(folder):
    for i in range(SHARDS):
        path = os.path.join(folder, "%03d.tsv" % i)
        rows = []
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    parts = line.rstrip("\n").split("\t", 2)
                    if len(parts) == 3 and parts[0]:
                        rows.append(parts)
        yield i, rows


def decode(payload, vocab):
    """The text back from its codes (as Encyclopedia does in Java)."""
    s = []
    i = 0
    first = True
    while i < len(payload):
        n = 0
        shift = 0
        while True:
            b = payload[i]
            i += 1
            n |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                break
        v, case, space = n >> 3, (n >> 1) & 3, n & 1
        if v == 0:
            ln = 0
            shift = 0
            while True:
                b = payload[i]
                i += 1
                ln |= (b & 0x7F) << shift
                shift += 7
                if not b & 0x80:
                    break
            t = payload[i:i + ln].decode("utf-8")
            i += ln
        else:
            t = vocab[v - 1]
            if case == 1:
                t = capital(t)
            elif case == 2:
                t = t.upper()
        if space and not first:
            s.append(" ")
        s.append(t)
        first = False
    return "".join(s)


def normalised(text):
    """The text as the codes keep it: single spaces, and no space where there was none."""
    return re.sub(r"\s+", " ", text).strip()


def main(src, dst):
    os.makedirs(dst, exist_ok=True)
    counts = Counter()
    for _, rows in read_shards(src):
        for k, title, body in rows:
            if not body.startswith("=>"):
                for t, _ in tokens(body):
                    counts[split_case(t)[0]] += 1
    vocab = [w for w, c in counts.most_common() if c >= MIN_USES]
    index = {w: i + 1 for i, w in enumerate(vocab)}
    out = bytearray(b"IBK1V")
    varint(len(vocab), out)
    for w in vocab:
        b = w.encode("utf-8")
        varint(len(b), out)
        out += b
    with open(os.path.join(dst, "vocab.ibk"), "wb") as f:
        f.write(out)
    raw = coded = articles = literal = 0
    for i, rows in read_shards(src):
        best = {}
        for k, title, body in rows:
            if k in best and best[k][1].startswith("=>") is False and body.startswith("=>"):
                continue  # an article wins over another name
            best[k] = (title, body)
        out = bytearray(b"IBK1")
        varint(len(best), out)
        for k, (title, body) in best.items():
            kb = k.encode("utf-8")
            varint(len(kb), out)
            out += kb
            redirect = body.startswith("=>")
            if redirect:
                rule = 3  # a redirect's own title is never shown
            elif title == capital(k):
                rule = 1
            elif title == " ".join(capital(w) for w in k.split(" ")):
                rule = 2
            else:
                rule = 0
            out.append((rule << 1) | (1 if redirect else 0))
            if rule == 0:
                tb = title.encode("utf-8")
                varint(len(tb), out)
                out += tb
            if redirect:
                p = body[2:].encode("utf-8")
            else:
                p = bytearray()
                for t, space in tokens(body):
                    form, case = split_case(t)
                    v = index.get(form)
                    if v is not None:
                        rebuilt = form if case == 0 else capital(form) if case == 1 else form.upper()
                        if rebuilt != t:
                            v = None
                    if v is None:
                        varint((0 << 3) | (int(space) & 1), p)
                        b = t.encode("utf-8")
                        varint(len(b), p)
                        p += b
                        literal += 1
                    else:
                        varint((v << 3) | (case << 1) | int(space), p)
                p = bytes(p)
                back = decode(p, vocab)
                if back != normalised(body):
                    raise SystemExit("round trip failed for %r:\n%r\n%r" % (title, normalised(body)[:200], back[:200]))
                raw += len(body.encode("utf-8"))
                coded += len(p)
                articles += 1
            varint(len(p), out)
            out += p
        with open(os.path.join(dst, "%03d.ibk" % i), "wb") as f:
            f.write(out)
    total = sum(os.path.getsize(os.path.join(dst, n)) for n in os.listdir(dst) if n.endswith(".ibk"))
    print("vocab", len(vocab), "articles", articles, "text", raw, "coded", coded,
          "ratio %.2f" % (raw / max(1, coded)), "literal tokens", literal, "files", total)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
