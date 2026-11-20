#!/usr/bin/env python3
"""
Codes a knowledge text file (a .tsv table, a .json pack) into the Ibiko v1 text form (IBK1T), exactly: decoding it
gives back the very same bytes. The app ships only the coded file (<name>.ibk next to where <name> was) and decodes
it where it is read (ai.ibiko.ibiko1.TextCode).

    python3 tools/encode_text.py app/src/main/assets/knowledge/lexicon.tsv [more files...] [--remove]

<name>.ibk  "IBK1T", varint word count, each word (varint length, UTF-8), then the token codes to the end.
A text is cut into tokens: runs of letters and digits, and single other characters (a tab and a line end are
tokens too). A token's code is (v << 3) | (case << 1) | space, as an encyclopedia article's (encode_encyclopedia.py):
    v      0 = the token itself follows (varint length, UTF-8); otherwise word v-1 of this file's own vocabulary
    case   0 = as written in the vocabulary, 1 = first letter capital, 2 = all capitals
    space  1 = one space before it
More than one space is kept as a written-out token of the extra spaces. The file is decoded again here and compared
byte for byte before it is written.

--remove deletes each original once its .ibk is written and checked.
"""
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from encode_encyclopedia import capital, split_case, varint  # noqa: E402

TOKEN = re.compile(r"\w+|[^\w ]| +", re.UNICODE)
MIN_USES = 2


def tokens(text):
    """(token, one space before) pairs; a run of spaces before a token keeps the extra ones as a token of its own."""
    out = []
    space = False
    for m in TOKEN.finditer(text):
        t = m.group(0)
        if t[0] == " ":
            if len(t) == 1:
                space = True
            else:
                out.append((t[:-1], False))  # the extra spaces, written out
                space = True
            continue
        out.append((t, space))
        space = False
    if space:
        out.append((" ", False))  # a space at the very end
    return out


def decode(b, at, vocab):
    out = []
    p = at
    n = len(b)
    while p < n:
        code = 0
        shift = 0
        while True:
            x = b[p]
            p += 1
            code |= (x & 0x7F) << shift
            shift += 7
            if not x & 0x80:
                break
        v, case, space = code >> 3, (code >> 1) & 3, code & 1
        if v == 0:
            ln = 0
            shift = 0
            while True:
                x = b[p]
                p += 1
                ln |= (x & 0x7F) << shift
                shift += 7
                if not x & 0x80:
                    break
            t = b[p:p + ln].decode("utf-8")
            p += ln
        else:
            t = vocab[v - 1]
            if case == 1:
                t = capital(t)
            elif case == 2:
                t = t.upper()
        if space:
            out.append(" ")
        out.append(t)
    return "".join(out)


def encode(path, remove):
    with open(path, "rb") as f:
        raw = f.read()
    text = raw.decode("utf-8")
    toks = tokens(text)
    counts = Counter(split_case(t)[0] for t, _ in toks)
    vocab = [w for w, c in counts.most_common() if c >= MIN_USES]
    index = {w: i + 1 for i, w in enumerate(vocab)}
    out = bytearray(b"IBK1T")
    varint(len(vocab), out)
    for w in vocab:
        wb = w.encode("utf-8")
        varint(len(wb), out)
        out += wb
    body_at = len(out)
    for t, space in toks:
        form, case = split_case(t)
        v = index.get(form)
        if v is not None:
            rebuilt = form if case == 0 else capital(form) if case == 1 else form.upper()
            if rebuilt != t:
                v = None
        if v is None:
            varint(int(space), out)
            tb = t.encode("utf-8")
            varint(len(tb), out)
            out += tb
        else:
            varint((v << 3) | (case << 1) | int(space), out)
    if decode(bytes(out), body_at, vocab).encode("utf-8") != raw:
        raise SystemExit("round trip failed for " + path)
    with open(path + ".ibk", "wb") as f:
        f.write(out)
    print("%s: %d -> %d bytes (%.2fx), %d words" % (path, len(raw), len(out), len(raw) / max(1, len(out)), len(vocab)))
    if remove:
        os.remove(path)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--remove"]
    for a in args:
        encode(a, "--remove" in sys.argv)
