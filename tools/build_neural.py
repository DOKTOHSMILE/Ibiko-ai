#!/usr/bin/env python3
"""
Builds Ibiko v1's neural layer (docs/IBIKO_NEURAL.md, N1 and N2) from its own text: the encyclopedia's article openings
(the .tsv shards of build_encyclopedia.py) and the dictionary's definitions (lexicon.tsv).

    python3 tools/build_neural.py <encyclopedia-tsv-folder> <lexicon.tsv> app/src/main/assets/knowledge/neural

  bpe.ibk      the subword tokenizer: "IBKB", version 1, varint merge count, each merge as two strings
               (varint length + UTF-8). A word is its characters plus "</w>", merged by these in order.
  vectors.ibk  the embeddings: "IBKN", version 1, varint dims, varint words, varint pieces, then each entry:
               varint length + UTF-8, float32 scale (big-endian), dims signed bytes (value = byte * scale).
               Words first (most frequent first), then the BPE pieces (prefixed "##").

Steps: words counted; BPE learned (MERGES merges); the VOCAB most frequent words kept; co-occurrence in a window of
WINDOW words each side, weighted 1/distance; positive PMI with the context smoothed by 0.75; a randomized truncated SVD
to DIMS dimensions; rows = U * sqrt(S), normalised; each BPE piece the frequency-weighted mean of the words it is in.
Needs numpy and scipy.
"""
import collections
import os
import re
import struct
import sys
import time

import numpy as np
import scipy.sparse as sp

VOCAB = 50000
MIN_COUNT = 5
WINDOW = 5
DIMS = 128
MERGES = 8000
ALPHA = 0.75
WORD = re.compile(r"[^\W_]+(?:['’-][^\W_]+)*", re.UNICODE)


def varint(n, out):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def put_string(s, out):
    b = s.encode("utf-8")
    varint(len(b), out)
    out += b


def texts(enc_dir, lexicon):
    for i in range(512):
        path = os.path.join(enc_dir, "%03d.tsv" % i)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t", 2)
                if len(parts) == 3 and not parts[2].startswith("=>"):
                    yield parts[2]
    with open(lexicon, encoding="utf-8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) > 2:
                # the word and its meaning together: the definition is the word's context
                yield c[0] + " " + c[2] + " " + c[3].replace(",", " ")


def words(text):
    return [w.lower().replace("’", "'") for w in WORD.findall(text)]


# ------------------------------------------------------------------ BPE

def learn_bpe(counts, merges):
    """Byte-pair merges over a word-count table (only words seen twice or more), incrementally updated."""
    vocab = {}
    for w, c in counts.items():
        if c >= 2 and len(w) <= 40:
            vocab[tuple(list(w) + ["</w>"])] = c
    words_list = list(vocab.items())
    syms = [list(w) for w, _ in words_list]
    freq = [c for _, c in words_list]
    pairs = collections.Counter()
    where = collections.defaultdict(set)
    for i, s in enumerate(syms):
        for a, b in zip(s, s[1:]):
            pairs[(a, b)] += freq[i]
            where[(a, b)].add(i)
    out = []
    for m in range(merges):
        if not pairs:
            break
        best, n = pairs.most_common(1)[0]
        if n < 2:
            break
        out.append(best)
        a, b = best
        joined = a + b
        for i in list(where[best]):
            s = syms[i]
            f = freq[i]
            # take this word's pairs out, merge, put its pairs back
            for x, y in zip(s, s[1:]):
                pairs[(x, y)] -= f
                if pairs[(x, y)] <= 0:
                    del pairs[(x, y)]
                where[(x, y)].discard(i)
            k = 0
            merged = []
            while k < len(s):
                if k + 1 < len(s) and s[k] == a and s[k + 1] == b:
                    merged.append(joined)
                    k += 2
                else:
                    merged.append(s[k])
                    k += 1
            syms[i] = merged
            for x, y in zip(merged, merged[1:]):
                pairs[(x, y)] += f
                where[(x, y)].add(i)
        if (m + 1) % 1000 == 0:
            print("  bpe merges", m + 1, flush=True)
    return out


def encode_bpe(word, ranks, cache):
    if word in cache:
        return cache[word]
    s = list(word) + ["</w>"]
    while len(s) > 1:
        best, at = None, -1
        for k in range(len(s) - 1):
            r = ranks.get((s[k], s[k + 1]))
            if r is not None and (best is None or r < best):
                best, at = r, k
        if best is None:
            break
        s = s[:at] + [s[at] + s[at + 1]] + s[at + 2:]
    cache[word] = s
    return s


# ------------------------------------------------------------------ embeddings

def randomized_svd(m, k, oversample=16, iters=3, seed=7):
    rng = np.random.default_rng(seed)
    n = m.shape[1]
    omega = rng.standard_normal((n, k + oversample)).astype(np.float32)
    y = m @ omega
    for _ in range(iters):
        y, _ = np.linalg.qr(y)
        y = m @ (m.T @ y)
    q, _ = np.linalg.qr(y)
    b = (m.T @ q).T
    ub, s, _ = np.linalg.svd(b, full_matrices=False)
    u = q @ ub
    return u[:, :k], s[:k]


def main(enc_dir, lexicon, out_dir):
    t0 = time.time()
    os.makedirs(out_dir, exist_ok=True)
    counts = collections.Counter()
    docs = []
    for t in texts(enc_dir, lexicon):
        ws = words(t)
        counts.update(ws)
        docs.append(ws)
    total_tokens = sum(len(d) for d in docs)
    print("texts", len(docs), "tokens", total_tokens, "distinct words", len(counts), "%.0fs" % (time.time() - t0), flush=True)

    # N1: the tokenizer
    merges = learn_bpe(counts, MERGES)
    out = bytearray(b"IBKB")
    out.append(1)
    varint(len(merges), out)
    for a, b in merges:
        put_string(a, out)
        put_string(b, out)
    with open(os.path.join(out_dir, "bpe.ibk"), "wb") as f:
        f.write(out)
    print("bpe", len(merges), "merges, %.0fs" % (time.time() - t0), flush=True)

    # N2: the embeddings
    vocab = [w for w, c in counts.most_common() if c >= MIN_COUNT][:VOCAB]
    index = {w: i for i, w in enumerate(vocab)}
    n = len(vocab)
    ids = np.fromiter((index.get(w, -1) for d in docs for w in d + [None]), dtype=np.int32)  # None: a break between texts
    print("vocab", n, "ids", len(ids), flush=True)
    co = sp.csr_matrix((n, n), dtype=np.float32)
    for d in range(1, WINDOW + 1):
        a, b = ids[:-d], ids[d:]
        ok = (a >= 0) & (b >= 0)
        # a break (-1) between texts in between stops the window: approximate by the direct pairs only
        r, c = a[ok], b[ok]
        w = np.full(len(r), 1.0 / d, dtype=np.float32)
        m = sp.coo_matrix((w, (r, c)), shape=(n, n)).tocsr()
        co = co + m + m.T
        print("  window", d, "pairs", len(r), flush=True)
    co.sum_duplicates()
    total = co.sum()
    row = np.asarray(co.sum(axis=1)).ravel()
    col = np.asarray(co.sum(axis=0)).ravel() ** ALPHA
    col = col / col.sum()
    coo = co.tocoo()
    pmi = np.log((coo.data / total) / ((row[coo.row] / total) * col[coo.col]))
    keep = pmi > 0
    ppmi = sp.csr_matrix((pmi[keep].astype(np.float32), (coo.row[keep], coo.col[keep])), shape=(n, n))
    print("ppmi nonzero", ppmi.nnz, "%.0fs" % (time.time() - t0), flush=True)
    u, s = randomized_svd(ppmi, DIMS)
    vec = (u * np.sqrt(s)).astype(np.float32)
    vec /= np.maximum(np.linalg.norm(vec, axis=1, keepdims=True), 1e-8)
    print("svd done %.0fs" % (time.time() - t0), flush=True)

    # subword vectors: each piece the frequency-weighted mean of the words it is in
    ranks = {p: i for i, p in enumerate(merges)}
    cache = {}
    piece_sum = collections.defaultdict(lambda: np.zeros(DIMS, dtype=np.float64))
    piece_w = collections.Counter()
    for i, w in enumerate(vocab):
        f = counts[w]
        for p in encode_bpe(w, ranks, cache):
            piece_sum[p] += vec[i] * f
            piece_w[p] += f
    pieces = sorted(piece_sum, key=lambda p: -piece_w[p])
    pvec = np.array([piece_sum[p] / max(1e-8, np.linalg.norm(piece_sum[p])) for p in pieces], dtype=np.float32)

    out = bytearray(b"IBKN")
    out.append(1)
    varint(DIMS, out)
    varint(n, out)
    varint(len(pieces), out)
    for name, row_v in list(zip(vocab, vec)) + list(zip(["##" + p for p in pieces], pvec)):
        put_string(name, out)
        scale = float(np.max(np.abs(row_v))) / 127.0 or 1e-8
        out += struct.pack(">f", scale)
        out += np.clip(np.round(row_v / scale), -127, 127).astype(np.int8).tobytes()
    with open(os.path.join(out_dir, "vectors.ibk"), "wb") as f:
        f.write(out)

    # a quick look: the nearest words to a few
    def near(word, k=8):
        if word not in index:
            return []
        sims = vec @ vec[index[word]]
        return [vocab[j] for j in np.argsort(-sims)[1:k + 1]]

    for w in ["king", "river", "money", "happy", "doctor", "lagos", "football", "computer", "fast", "car"]:
        print("  near", w, ":", ", ".join(near(w)))
    print("done: words", n, "pieces", len(pieces), "bytes", os.path.getsize(os.path.join(out_dir, "vectors.ibk")),
          "%.0fs" % (time.time() - t0))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
