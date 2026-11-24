#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/lexicon_more.tsv.ibk: words the dictionary lacks, with their meanings.

Open English WordNet (knowledge/lexicon.tsv) has the content words of English but not its small words ("they",
"which", "would"), irregular forms ("went", "children"), Nigerian English and Pidgin ("wahala", "abeg", "japa"),
African foods ("garri", "kenkey") or modern words ("selfie", "chatbot", "covid"). This adds them from Wiktionary,
through kaikki.org's machine-readable extract (https://kaikki.org, CC BY-SA 4.0 and GFDL, see WIKTIONARY_LICENSE.txt).

The words are tools/lexicon_more_words.txt: a hand-picked part, then the built-in encyclopedia's most used words that
WordNet lacks. Names (a person's, a place's) are left out: they are the encyclopedia's.

The lines have the dictionary's own columns, so Ibiko v1 reads them the same way (ai.ibiko.ibiko1.words.MeaningIndex,
after the dictionary's own senses, so every word already there keeps its place and its code in the knowledge packs):
  word <TAB> part of speech <TAB> definition <TAB> synonyms <TAB> opposites <TAB> broader term
A region or register Wiktionary gives ("Nigeria", "slang", "Internet") is said at the start of the definition.

Usage: tools/build_lexicon_more.py [--cache DIR]   (each word's entry is fetched once and kept in the cache)
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = os.path.join(HERE, "lexicon_more_words.txt")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "lexicon_more.tsv")
POS = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "pron": "pronoun", "prep": "preposition",
       "conj": "conjunction", "det": "determiner", "article": "determiner", "intj": "interjection", "num": "numeral",
       "particle": "particle", "contraction": "contraction", "abbrev": "abbreviation", "phrase": "phrase", "suffix": None,
       "prefix": None, "name": None, "character": None, "symbol": None, "affix": None, "infix": None}
SAY_TAGS = {"Nigeria", "Nigerian", "Ghana", "Kenya", "South-Africa", "West-Africa", "Africa", "slang", "Internet", "informal",
            "colloquial", "British", "US", "UK", "Australia", "India", "dated", "humorous", "vulgar", "offensive", "derogatory"}
SKIP_TAGS = {"obsolete", "archaic", "rare", "misspelling", "nonstandard-spelling", "historical"}
MOST = 3  # senses kept for each word and part of speech, the most common first, as the dictionary keeps them


def fetch(word, cache):
    path = os.path.join(cache, urllib.parse.quote(word, safe="") + ".jsonl")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return f.read()
    w = word  # as written: kaikki's pages keep the case ("me, myself and I")
    url = "https://kaikki.org/dictionary/English/meaning/%s/%s/%s.jsonl" % (
        urllib.parse.quote(w[0]), urllib.parse.quote(w[:2]), urllib.parse.quote(w))
    text, answered = "", False
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "dictionary-builder/1.0"})
            with urllib.request.urlopen(req, timeout=20) as r:
                text = r.read().decode("utf-8")
            answered = True
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                answered = True  # no English entry: an answer too
                break
            time.sleep(3 * (attempt + 1))  # too many requests, or the server busy: wait and ask again
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            time.sleep(3 * (attempt + 1))
    if answered:  # a failure isn't kept, so the next run asks again
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    time.sleep(0.15)  # polite: one request at a time
    return text


def clean(gloss):
    g = re.sub(r"\s+", " ", gloss).strip()
    g = g.replace("\t", " ")
    # "A group of …" as the dictionary says it, "a group of …"; an acronym ("COVID-19") stays
    return g[:1].lower() + g[1:] if len(g) > 1 and not g[1].isupper() else g


def rows_for(word, text):
    out = []
    seen = set()
    for line in text.splitlines():
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get("lang_code") != "en" or d.get("word", "").lower() != word.lower():
            continue
        pos = POS.get(d.get("pos"), "other")
        if pos is None:
            continue
        kept = 0
        for s in d.get("senses", []):
            tags = set(s.get("tags", []))
            glosses = s.get("glosses") or []
            if not glosses or tags & SKIP_TAGS:
                continue
            gloss = clean(glosses[-1])
            if not gloss or len(gloss) > 300 or (pos, gloss) in seen:
                continue
            seen.add((pos, gloss))
            said = sorted(t for t in tags if t in SAY_TAGS)
            definition = ("(" + ", ".join(t.replace("-", " ") for t in said) + ") " if said else "") + gloss
            syn = [x.get("word", "") for x in s.get("synonyms", []) if x.get("word")][:6]
            ant = [x.get("word", "") for x in s.get("antonyms", []) if x.get("word")][:4]
            out.append("\t".join([word.lower(), pos, definition, ", ".join(syn), ", ".join(ant), ""]).replace("\n", " "))
            kept += 1
            if kept >= MOST:
                break
    return out


def main():
    cache = sys.argv[sys.argv.index("--cache") + 1] if "--cache" in sys.argv else os.path.join("/tmp", "kaikki-cache")
    os.makedirs(cache, exist_ok=True)
    words = []
    for line in open(WORDS, encoding="utf-8"):
        w = line.strip()
        if w and not w.startswith("#"):
            words.append(w)
    words = list(dict.fromkeys(w.lower() for w in words))
    # four at a time: kaikki serves static files, and this stays gentle on it
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=4) as pool:
        texts = list(pool.map(lambda w: fetch(w, cache), words))
    lines, found = [], 0
    for i, w in enumerate(words):
        rows = rows_for(w, texts[i])
        if rows:
            found += 1
            lines.extend(rows)
        if i % 250 == 0:
            print("%d / %d words, %d with meanings" % (i, len(words), found), flush=True)
    lines.sort(key=lambda l: l.split("\t", 1)[0])
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d words with %d meanings (of %d asked)" % (found, len(lines), len(words)))


if __name__ == "__main__":
    main()
