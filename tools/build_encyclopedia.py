#!/usr/bin/env python3
"""
Builds Ibiko 1's offline encyclopedia from a Simple English Wikipedia dump (CC BY-SA 4.0).

    python3 tools/build_encyclopedia.py simplewiki-latest-pages-articles.xml.bz2 app/src/main/assets/knowledge/encyclopedia

For every article (namespace 0) it keeps the opening paragraphs as plain text (templates, references, tables,
files and markup taken out), cut at a sentence end; redirects are kept as aliases ("USA" -> "United States").
Lists, disambiguation pages and near-empty stubs are left out. The result is split into SHARDS files by a hash of
the normalised title, the same hash Java's String.hashCode() gives, so the app opens one small file per lookup:

    key<TAB>Title<TAB>text            an article
    key<TAB>Title<TAB>=>Target        a redirect

Needs mwparserfromhell (pip install mwparserfromhell).
"""
import bz2
import os
import re
import sys
import xml.etree.ElementTree as ET

import mwparserfromhell

SHARDS = 512
MOST_CHARS = 1400  # the opening of an article: enough to explain the thing, not the whole page
LEAST_CHARS = 60


def key(title):
    """The normalised title: lower case, letters and digits, single spaces (as Encyclopedia.key in Java)."""
    t = title.lower()
    t = re.sub(r"[^\w]+", " ", t, flags=re.UNICODE)
    return re.sub(r"\s+", " ", t).strip()


def java_hash(s):
    """Java's String.hashCode(): over UTF-16 code units, 32-bit wrap."""
    h = 0
    data = s.encode("utf-16-be")
    for i in range(0, len(data), 2):
        h = (31 * h + ((data[i] << 8) | data[i + 1])) & 0xFFFFFFFF
    return h


def shard(k):
    return (java_hash(k) & 0x7FFFFFFF) % SHARDS


SKIP_TITLE = re.compile(r"^(List of|Lists of|Timeline of|Index of)\b|\(disambiguation\)$")
DROP_TEMPLATE = re.compile(r"(?i)^(infobox|taxobox|automatic taxobox|speciesbox|navbox|sidebar|cite|reflist|refimprove|stub|"
                           r"coord|short description|use |about|for|other uses|redirect|distinguish|main|see also|clear|"
                           r"commons|wiktionary|portal|toc|disambig)")


def clean(wikitext):
    """The opening paragraphs as plain text, or None."""
    text = wikitext
    # the lead: everything before the first heading
    m = re.search(r"^==[^=].*?==\s*$", text, flags=re.M)
    if m:
        text = text[:m.start()]
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<ref[^>/]*/>", "", text)
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    text = re.sub(r"\{\|.*?\|\}", "", text, flags=re.S)  # tables
    try:
        code = mwparserfromhell.parse(text)
        for t in code.filter_templates(recursive=False):
            try:
                code.remove(t)
            except ValueError:
                pass
        for link in code.filter_wikilinks():
            target = str(link.title).strip().lower()
            if target.startswith(("file:", "image:", "category:", "media:")) or re.match(r"^[a-z]{2,3}(-[a-z]+)?:", target):
                try:
                    code.remove(link)
                except ValueError:
                    pass
        plain = code.strip_code(normalize=True, collapse=True)
    except Exception:
        return None
    plain = re.sub(r"<[^>]+>", "", plain)
    plain = plain.replace("'''", "").replace("''", "")
    paras = []
    for p in re.split(r"\n\s*\n", plain):
        p = re.sub(r"\s+", " ", p).strip()
        if not p or p.startswith(("*", "#", ":", ";", "|", "{", "}")) or len(p) < 25:
            continue
        paras.append(p)
    if not paras:
        return None
    out = ""
    for p in paras:
        if len(out) + len(p) + 1 > MOST_CHARS:
            if not out:
                out = p
            break
        out = (out + " " + p).strip()
    if len(out) > MOST_CHARS:
        cut = out.rfind(". ", 0, MOST_CHARS)
        out = out[:cut + 1] if cut > MOST_CHARS // 2 else out[:MOST_CHARS].rsplit(" ", 1)[0] + "…"
    out = re.sub(r"\(\s*[,;]?\s*\)", "", out)          # brackets emptied by removed templates
    out = re.sub(r"\s+([,.;:])", r"\1", out)
    out = re.sub(r"\s{2,}", " ", out).strip()
    if len(out) < LEAST_CHARS or "may refer to" in out[:200].lower():
        return None
    return out.replace("\t", " ").replace("\n", " ")


def main(dump, outdir):
    os.makedirs(outdir, exist_ok=True)
    files = [open(os.path.join(outdir, "%03d.tsv" % i), "w", encoding="utf-8") for i in range(SHARDS)]
    articles = redirects = 0
    chars = 0
    ns = None
    title = text = None
    redirect_to = None
    page_ns = None
    with bz2.open(dump, "rb") as f:
        for event, el in ET.iterparse(f, events=("end",)):
            tag = el.tag.rsplit("}", 1)[-1]
            if tag == "title":
                title = el.text or ""
            elif tag == "ns":
                page_ns = el.text
            elif tag == "redirect":
                redirect_to = el.get("title")
            elif tag == "text":
                text = el.text or ""
            elif tag == "page":
                if page_ns == "0" and title and not SKIP_TITLE.search(title):
                    k = key(title)
                    if k:
                        if redirect_to:
                            files[shard(k)].write("%s\t%s\t=>%s\n" % (k, title, redirect_to))
                            redirects += 1
                        else:
                            body = clean(text or "")
                            if body:
                                files[shard(k)].write("%s\t%s\t%s\n" % (k, title, body))
                                articles += 1
                                chars += len(body)
                                if articles % 20000 == 0:
                                    print("articles", articles, "redirects", redirects, "MB", round(chars / 1e6, 1), flush=True)
                title = text = redirect_to = page_ns = None
                el.clear()
    for f in files:
        f.close()
    total = sum(os.path.getsize(os.path.join(outdir, "%03d.tsv" % i)) for i in range(SHARDS))
    print("done: articles", articles, "redirects", redirects, "bytes", total)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
