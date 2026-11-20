# Ibiko v1: the neural layer (build map)

Ibiko v1 understands language with rules and a dictionary (phrase structure, morphology, WordNet meanings). Language
models understand it with **subword tokens**, **embedding vectors** and **attention**. This plan gives Ibiko v1 those
too, on the phone, in a few megabytes, and says plainly what stays with the downloadable models.

## What is built, and what is not

| Layer | Size on the phone | Built here | What it gives Ibiko |
|---|---|---|---|
| Subword tokenizer (BPE) | ~0.3 MB | **Yes** (N1) | Any word, even one never seen, misspelled or made up, is split into known pieces |
| Word embeddings (PPMI + SVD, int8) | ~6 MB | **Yes** (N2) | Meaning as geometry: words used alike sit close together |
| Subword vectors | in the same file | **Yes** (N2) | A vector for a word that isn't in the vocabulary, from its pieces |
| Self-attention over a sentence | code only | **Yes** (N3) | Each word's vector shifted by the words around it: meaning in context |
| Wired into Ibiko's reasoning | code only | **Yes** (N4) | Paraphrase matching, word sense, reverse dictionary, passage questions |
| Evaluation by simulation | tests only | **Yes** (N5) | Thousands of word and sentence pairs scored on every build |
| A generative transformer of its own | 300 MB to GBs | **No** (N6) | Training one that writes well needs GPU clusters and billions of words; the app already runs downloadable ones (Qwen3 0.6B, Llama 3.2 1B) through llama.cpp |

## N1. The tokenizer

* **Algorithm:** byte-pair encoding (BPE) over words. Start from characters plus an end-of-word mark `</w>`; repeatedly
  merge the most frequent adjacent pair; stop at **8,000 merges**. Unknown characters fall back to single characters, so
  every input encodes.
* **Training text:** the encyclopedia's 271,450 article openings plus the dictionary's 198,273 definitions, lower-cased,
  split into words (letters, digits, inner apostrophes and hyphens).
* **Built by** `tools/build_neural.py` → `assets/knowledge/neural/bpe.ibk`:
  `IBKB`, version 1, varint merge count, then each merge as two UTF-8 strings (varint length + bytes).
* **On the phone:** `neural/Tokenizer.java` loads the merges into a rank table and encodes a word greedily by the
  lowest-ranked pair (the standard BPE encode), caching each word's pieces.

## N2. The embeddings

* **Vocabulary:** the 50,000 most frequent words (seen at least 5 times).
* **Counts:** co-occurrence in a window of 5 words each side, weighted 1/distance.
* **PPMI:** `max(0, log(p(w,c) / (p(w) p(c)^0.75)))`, the context distribution smoothed by 0.75.
* **SVD:** truncated to **128 dimensions**; vectors = `U·√Σ`, then each row normalised to length 1.
* **Subword vectors:** each BPE piece's vector is the frequency-weighted mean of the vectors of the words it appears in,
  normalised. A word outside the vocabulary gets the normalised mean of its pieces' vectors.
* **Storage:** int8 per value with one float scale per row, coded as `IBKN`:
  `IBKN`, version 1, dims, word count, piece count, then each entry: varint length + UTF-8, float scale, `dims` bytes.
  About 6 MB for 50,000 words and their pieces.
* **On the phone:** `neural/Embeddings.java` loads it lazily into one `byte[]` plus scales, and keeps a hash index.
  Cosine similarity is a dot product of de-quantised rows.

## N3. Attention

For a sentence of words w1…wn with vectors e1…en (from N2):

```
score(i, j)   = (e_i · e_j) / sqrt(d)        d = 128; i ≠ j, stop words weighted down
a(i, ·)       = softmax over j of score(i, j)
h_i           = normalise( e_i + Σ_j a(i, j) · e_j )      the residual keeps the word itself
sentence      = normalise( Σ_i weight(w_i) · h_i )        weight = inverse frequency (rare words say more)
```

This is one self-attention layer with identity projections: no training is needed for it, because the embedding
geometry already makes words that belong together attend to each other. "bank" next to "river" moves towards water
words, and next to "money" towards finance words.

## N4. Wiring it into Ibiko v1

1. **Same meaning, said another way:** the sentence vectors' cosine joins the WordNet synonym alignment in
   `Meanings.compare`. Two sentences that share no words but mean the same score high.
2. **Word sense:** each sense's gloss is encoded into a vector, and the sense closest to the word's contextual vector
   `h_i` wins. This is combined with the Lesk overlap score.
3. **Reverse dictionary:** the top candidates are re-ranked by cosine between the description's vector and each gloss
   vector.
4. **Memory:** a question said another way still finds what the person confirmed (`Knowledge`), by sentence vector.
5. **Pasted text:** a passage's sentences are ranked for a question by sentence vector as well as by shared words.
6. **The words engine in chat:** "how similar are X and Y", "words close to X", and "what's the odd one out: …".

## N5. Evaluation by simulation (run on every build)

* **Synonym pairs vs random pairs:** thousands drawn from WordNet. The measure is AUC (how often a synonym pair scores
  above a random one), and it must stay above 0.80.
* **Paraphrase pairs from `Paraphrase` and `ManyWaysTest`:** each pair must score above unrelated sentences.
* **Word sense:** "bank" with river words vs money words, plus a set of ambiguous words with labelled contexts.
* **Speed:** the encoder must embed a sentence in under 2 ms on the test machine.

## N6. Not here: a generative model of its own

Writing fluent new text needs a transformer trained on billions of words: weeks of GPU time and gigabytes on the
phone. The app already downloads and runs such models on the device (llama.cpp), and Ibiko v1 learns from their
answers. Its own composer (`Letters`, templates, `InnerVoice`) covers short, checked replies. When the phone has a
model, the neural layer above makes the hand-off better: Ibiko understands the question itself, gives the model only
what it needs, and checks the answer against what it knows.

## Results (first build)

Trained on 469,723 texts (19,993,843 words, 445,263 distinct) in about two minutes on four CPU cores:
`bpe.ibk` 76 KB (8,000 merges), `vectors.ibk` 8.2 MB (50,000 words, 8,081 pieces, 128 dimensions).

| Simulation (NeuralTest) | Result |
|---|---|
| Synonym pairs vs random pairs, 5,000 trials | AUC **0.91** (target 0.80) |
| Definition finds its own word, 3,000 trials | AUC **0.92** |
| Paraphrase pairs above every unrelated pairing | **10/12** |
| "bank" moved towards water by "river", towards money by "loan" | yes |
| Pasted passage: question worded unlike the text finds its sentence | 3/3 |
| Encode one sentence | **0.07 ms** (target 2 ms) |
| Reverse dictionary top-3, with vs without vectors | 13/20 vs 13/20 |

Nearest words, straight from the file: doctor → physician, surgeon, specialist, nurse; Lagos → Quito, Pretoria,
Kampala, Nairobi, Abuja; car → truck, automobile, vehicle; football → soccer, club, goalkeeper.

What the build taught (and what it changed):

* **Inflections sit apart.** Vectors built from use keep "earn" and "earned" at 0.41, so the word alignment joins words
  of the same stem (`Words.key`).
* **Question words drown the meaning.** "how", "much", "did" weighed as much as content words; they now weigh 0.02
  (`Attention.FRAME`), and the smoothing constant is 1e-4.
* **Every sentence shares one direction.** Unrelated sentences scored 0.83; the common component (the mean of the
  frequent words) is removed from each sentence vector.
* **Relatedness is not meaning.** "selling" sits next to "buy", and "fast" next to "slow": the vectors say what is
  *about* the same thing, not what says the same thing. So opposites are checked by the dictionary before vectors may
  match two words; passage answers also check the kind of answer asked for (an amount, a time, a name); and the
  reverse dictionary stays led by WordNet, with the vectors only re-ranking by how well a definition matches.

## Order of work

1. `tools/build_neural.py`: corpus → BPE → co-occurrence → PPMI → SVD → subword vectors → `bpe.ibk`, `vectors.ibk`.
2. `neural/Tokenizer`, `neural/Embeddings`, `neural/Attention` (Java, pure), with tests against the shipped files.
3. The evaluation tests (N5), with their thresholds.
4. The wiring (N4), each with its own test, then the full suite.
