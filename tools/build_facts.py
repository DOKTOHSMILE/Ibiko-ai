#!/usr/bin/env python3
"""
Builds Ibiko 1's fact tables from the information boxes of a Simple English Wikipedia dump (CC BY-SA 4.0): for each
article, the plain facts its box gives (when and where someone was born, a city's population, who wrote a book, when
a company was founded), coded in the Ibiko v1 form with the encyclopedia's own vocabulary.

    python3 tools/build_facts.py simplewiki-latest-pages-articles.xml.bz2 app/src/main/assets/knowledge/encyclopedia

It reads <out>/vocab.ibk (made by encode_encyclopedia.py) and writes <out>/facts/:

relations.ibk  "IBK1R", varint count, then each relation's name (varint length, UTF-8): relation n is the n-th
facts/NNN.ibk  "IBK1F", varint count, then each article (keyed and sharded as Encyclopedia.key / shard):
                 varint key length, key (UTF-8)
                 varint fact count, then each fact: byte relation, varint payload length, payload (token codes, as an
                 article's text in encode_encyclopedia.py)

Needs mwparserfromhell (pip install mwparserfromhell).
"""
import bz2
import html
import os
import re
import sys
import xml.etree.ElementTree as ET

import mwparserfromhell
from mwparserfromhell.nodes import Comment, ExternalLink, HTMLEntity, Tag, Template, Text, Wikilink

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_encyclopedia import SKIP_TITLE, key, shard, SHARDS  # noqa: E402
from encode_encyclopedia import decode, normalised, split_case, capital, tokens, varint  # noqa: E402

# the relations kept, and the box fields each is read from (the first one with a value wins)
RELATIONS = [
    ("born", ["birth_date", "date_of_birth", "born", "birthdate"]),
    ("birth_place", ["birth_place", "place_of_birth", "birthplace"]),
    ("died", ["death_date", "date_of_death", "died", "deathdate"]),
    ("death_place", ["death_place", "place_of_death", "deathplace"]),
    ("birth_name", ["birth_name"]),
    ("occupation", ["occupation", "occupations", "profession"]),
    ("nationality", ["nationality", "citizenship"]),
    ("spouse", ["spouse", "spouses"]),
    ("children", ["children"]),
    ("parents", ["parents"]),
    ("party", ["party"]),
    ("office", ["office", "office1"]),
    ("known_for", ["known_for"]),
    ("education", ["alma_mater", "education"]),
    ("genre", ["genre", "genres"]),
    ("instrument", ["instrument", "instruments"]),
    ("years_active", ["years_active", "yearsactive"]),
    ("label", ["label", "labels"]),
    ("capital", ["capital"]),
    ("largest_city", ["largest_city"]),
    ("population", ["population_total", "population", "population_estimate", "population_census", "pop_est", "pop"]),
    ("population_year", ["population_as_of", "population_date", "population_census_year", "population_estimate_year",
                         "pop_est_as_of", "census_year"]),
    ("area", ["area_total_km2", "area_km2", "area_total", "area"]),
    ("elevation", ["elevation_m", "elevation", "elevation_max_m", "highest_elevation"]),
    ("country", ["country", "nation"]),
    ("state", ["state", "region", "province"]),
    ("leader", ["leader_name", "leader_name1", "mayor", "leader"]),
    ("leader_title", ["leader_title", "leader_title1"]),
    ("currency", ["currency"]),
    ("language", ["official_languages", "languages", "language"]),
    ("government", ["government_type", "government"]),
    ("demonym", ["demonym", "population_demonym"]),
    ("time_zone", ["timezone", "time_zone"]),
    ("motto", ["motto", "national_motto"]),
    ("anthem", ["national_anthem", "anthem"]),
    ("founded", ["established_date", "founded", "foundation", "formation", "established", "opened", "built",
                 "founded_date", "inception"]),
    ("founder", ["founder", "founders"]),
    ("headquarters", ["headquarters", "hq_location", "location_city"]),
    ("key_people", ["key_people"]),
    ("industry", ["industry"]),
    ("products", ["products"]),
    ("employees", ["num_employees", "employees"]),
    ("author", ["author", "authors"]),
    ("writer", ["writer", "writers"]),
    ("director", ["director", "directors"]),
    ("producer", ["producer", "producers"]),
    ("starring", ["starring"]),
    ("music", ["music", "composer"]),
    ("released", ["released", "release_date", "first_aired", "airdate", "publication_date", "published", "release"]),
    ("developer", ["developer", "developers"]),
    ("publisher", ["publisher", "publishers"]),
    ("artist", ["artist"]),
    ("runtime", ["runtime", "length", "duration"]),
    ("budget", ["budget"]),
    ("gross", ["gross"]),
    ("location", ["location"]),
    ("height", ["height", "height_m"]),
    ("team", ["currentclub", "current_club", "club", "team"]),
    ("position", ["position"]),
    ("scientific_name", ["binomial", "species_name"]),
    ("mouth", ["mouth", "river_mouth", "mouth_location"]),
    ("source", ["source", "source1", "source1_location"]),
    ("length_km", ["length_km"]),
    ("religion", ["religion"]),
    ("website", []),  # never kept: a placeholder so the ids stay as they are if one is ever added
]
NAMES = [r for r, _ in RELATIONS]
FIELDS = {}
for _r, fields in RELATIONS:
    for _f in fields:
        FIELDS.setdefault(_f, _r)

BOX = re.compile(r"(?i)^\s*(infobox|taxobox|speciesbox|automatic taxobox)")
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
          "November", "December"]
DATE_TEMPLATES = re.compile(r"(?i)^(birth[ _]date|birth[ _]date[ _]and[ _]age|bda|b-da|dob|death[ _]date|death[ _]date[ _]and[ _]age|"
                            r"dda|dod|start[ _]date|start[ _]date[ _]and[ _]age|end[ _]date|film[ _]date|release[ _]date|"
                            r"date|birth[ _]year[ _]and[ _]age|death[ _]year[ _]and[ _]age|birth[ _]year|death[ _]year|"
                            r"start[ _]date[ _]and[ _]years[ _]ago|date[ _]and[ _]age|birthdate|deathdate)$")
LIST_TEMPLATES = re.compile(r"(?i)^(plainlist|plain[ _]list|flatlist|flat[ _]list|ubl|unbulleted[ _]list|hlist|"
                            r"collapsible[ _]list|bulleted[ _]list|ublist|bulleted list|indented plainlist|startplainlist)$")
INNER_TEMPLATES = re.compile(r"(?i)^(nowrap|nobr|small|big|formatnum|nobold|noitalic|resize|lang-?\w*|transl|"
                             r"marriage|married|abbr|nihongo|flag|flagcountry|flagu|flagicon[ _]image|ill|interlanguage[ _]link|"
                             r"sortname|sort|hs|nts|ntsh|player|fb|fbw|tooltip|nastaliq|unicode|sic|ma|money|"
                             r"us\$|usd|gbp|eur|inflation|okina|age|wikt|lower|upper|plural|smallcaps|cite|"
                             r"native[ _]phrase|native[ _]name|space|spaces)$")
UNITS = {"m": "m", "ft": "ft", "km": "km", "mi": "miles", "km2": "km²", "sqmi": "sq mi", "kg": "kg", "lb": "lb",
         "cm": "cm", "in": "in", "ha": "ha", "acre": "acres", "km/h": "km/h", "mph": "mph", "m2": "m²", "m3/s": "m³/s",
         "cuft/s": "cu ft/s", "t": "tonnes", "ml": "ml", "l": "l", "acres": "acres"}


def numbers(params):
    """The positional values of a template that are numbers."""
    out = []
    for p in params:
        if not p.showkey:
            v = str(p.value).strip()
            if re.fullmatch(r"-?\d{1,4}", v):
                out.append(int(v))
    return out


def date_text(t):
    name = str(t.name).strip().lower().replace("_", " ")
    n = numbers(t.params)
    if not n:
        # {{birth date|mf=yes|1918|7|18}} without numbers, or written out: take the text
        for p in t.params:
            if not p.showkey and str(p.value).strip():
                return render(p.value)
        return ""
    if "year" in name or len(n) < 3:
        return str(n[0]) if len(n) < 2 or not (1 <= n[1] <= 12) else "%s %d" % (MONTHS[n[1] - 1], n[0])
    y, m, d = n[0], n[1], n[2]
    if 1 <= m <= 12 and 1 <= d <= 31:
        return "%d %s %d" % (d, MONTHS[m - 1], y)
    return str(y)


def convert_text(t):
    pos = [str(p.value).strip() for p in t.params if not p.showkey]
    if not pos:
        return ""
    num = pos[0]
    unit = ""
    for v in pos[1:]:
        if v in ("-", "to", "and", "–", "or", "+/-"):
            continue
        if re.fullmatch(r"-?[\d.,]+", v):
            continue
        unit = UNITS.get(v, v)
        break
    if re.fullmatch(r"\d{4,}", num):
        num = "{:,}".format(int(num))
    return (num + " " + unit).strip()


def render_template(t):
    name = str(t.name).strip()
    low = name.lower().replace("_", " ")
    if DATE_TEMPLATES.match(low):
        return date_text(t)
    if low in ("convert", "cvt", "convinfobox", "height"):
        if low == "height":
            parts = [(str(p.name).strip(), str(p.value).strip()) for p in t.params if p.showkey]
            return " ".join("%s %s" % (v, UNITS.get(k, k)) for k, v in parts if v)
        return convert_text(t)
    pos = [p for p in t.params if not p.showkey]
    if LIST_TEMPLATES.match(low):
        items = []
        for p in pos:
            r = render(p.value)
            items.extend(x for x in re.split(r"\s*\n\s*\*?\s*|\s*;\s*", r) if x.strip())
        return ", ".join(x.strip(" *") for x in items if x.strip(" *"))
    if INNER_TEMPLATES.match(low) or low.startswith("lang-"):
        if not pos:
            return ""
        if low.startswith("lang") and len(pos) > 1 and low == "lang":
            return render(pos[1].value)
        if low in ("sort", "sortname"):
            return " ".join(render(p.value) for p in pos[:2]) if low == "sortname" else render(pos[-1].value)
        return render(pos[0].value)
    if low in ("increase", "decrease", "steady", "efn", "refn", "sfn", "ref", "r", "citation needed", "cn", "dubious",
               "fact", "when", "flagicon", "flag icon", "nbsp", "-", "·", "dot", "•", "ndash", "mdash"):
        return {"nbsp": " ", "ndash": "–", "mdash": "—", "·": " · ", "dot": " · ", "•": " · "}.get(low, "")
    return ""


def render(code):
    out = []
    for node in code.nodes:
        if isinstance(node, Text):
            out.append(str(node.value))
        elif isinstance(node, Wikilink):
            target = str(node.title).strip()
            if re.match(r"(?i)^(file|image|category|media):", target):
                continue
            out.append(render(node.text) if node.text is not None else target.split("#")[0])
        elif isinstance(node, Template):
            out.append(render_template(node))
        elif isinstance(node, Tag):
            tag = str(node.tag).lower()
            if tag == "br":
                out.append("\n")
            elif tag in ("ref", "references", "sup", "math", "gallery", "timeline"):
                continue
            elif node.contents is not None:
                out.append(render(node.contents))
        elif isinstance(node, HTMLEntity):
            out.append(node.normalize())
        elif isinstance(node, ExternalLink):
            if node.title is not None:
                out.append(render(node.title))
        elif isinstance(node, Comment):
            continue
        else:
            out.append(str(node))
    return "".join(out)


def tidy(text):
    """A box value as one clean line, or "" when it isn't a plain fact."""
    t = html.unescape(re.sub(r"<[^>]+>", "", text)).replace("\u00a0", " ")
    t = re.sub(r"\bm\.\s*\d{4}(\s*[-–]\s*\d{4})?(end)?", "", t)  # marriage years ("m. 1543-1545")
    t = t.replace("'''", "").replace("''", "")
    lines = [re.sub(r"^[*#:\s]+", "", x).strip(" ,;") for x in t.split("\n")]
    t = ", ".join(x for x in lines if x)
    t = re.sub(r"\[\[|\]\]|\{\{|\}\}", "", t)
    t = re.sub(r"\(\s*\)", "", t)
    t = re.sub(r"\s+([,.;:)])", r"\1", t)
    t = re.sub(r"\(\s+", "(", t)
    t = re.sub(r",\s*,+", ",", t)
    t = re.sub(r"\s{2,}", " ", t).strip(" ,;:")
    if not t or len(t) > 220 or "|" in t or "=" in t or t.lower() in ("none", "n/a", "unknown", "-", "?", "yes", "no"):
        return ""
    return t


def finish(rel, value, raw_field):
    """The value said the way the relation wants it."""
    if rel in ("population", "employees"):
        m = re.match(r"^(\d{4,})(\b.*)$", value.replace(",", "")) if re.fullmatch(r"[\d,]+(\s.*)?", value) else None
        if m:
            value = "{:,}".format(int(m.group(1))) + m.group(2)
    if rel == "area" and re.fullmatch(r"[\d.,]+", value):
        value = (("{:,}".format(int(value)) if value.isdigit() else value) + " km²") if raw_field != "area" or True else value
    if rel == "elevation" and re.fullmatch(r"-?[\d.,]+", value) and raw_field.endswith("_m"):
        value += " m"
    if rel == "length_km" and re.fullmatch(r"[\d.,]+", value):
        value += " km"
    if rel == "height" and re.fullmatch(r"[\d.]+", value) and raw_field == "height_m":
        value += " m"
    return value


def facts(wikitext):
    """The box's facts: [(relation, value)], in RELATIONS order."""
    if "{{" not in wikitext:
        return []
    m = re.search(r"\{\{\s*(?:Infobox|Taxobox|Speciesbox|Automatic taxobox)", wikitext, re.I)
    if not m:
        return []
    start = m.start()
    try:
        code = mwparserfromhell.parse(wikitext[start:start + 20000])
    except Exception:
        return []
    box = None
    for t in code.filter_templates(recursive=False):
        if BOX.match(str(t.name)):
            box = t
            break
    if box is None:
        return []
    got = {}
    for p in box.params:
        field = str(p.name).strip().lower().replace(" ", "_")
        rel = FIELDS.get(field)
        if rel is None or rel == "website":
            continue
        order = [f for r, fs in RELATIONS if r == rel for f in fs].index(field)
        if rel in got and got[rel][0] <= order:
            continue
        try:
            v = tidy(render(p.value))
        except Exception:
            v = ""
        if v:
            got[rel] = (order, finish(rel, v, field))
    if "population_year" in got and "population" not in got:
        del got["population_year"]
    if "leader_title" in got and "leader" not in got:
        del got["leader_title"]
    return [(r, got[r][1]) for r in NAMES if r in got]


def code_text(text, index):
    """A text as token codes (as encode_encyclopedia.py codes an article)."""
    p = bytearray()
    for t, space in tokens(text):
        form, case = split_case(t)
        v = index.get(form)
        if v is not None:
            rebuilt = form if case == 0 else capital(form) if case == 1 else form.upper()
            if rebuilt != t:
                v = None
        if v is None:
            varint(int(space) & 1, p)
            b = t.encode("utf-8")
            varint(len(b), p)
            p += b
        else:
            varint((v << 3) | (case << 1) | int(space), p)
    return bytes(p)


def read_vocab(path):
    with open(path, "rb") as f:
        b = f.read()
    assert b[:5] == b"IBK1V"
    p = [5]

    def vi():
        n = s = 0
        while True:
            x = b[p[0]]
            p[0] += 1
            n |= (x & 0x7F) << s
            s += 7
            if not x & 0x80:
                return n

    words = []
    for _ in range(vi()):
        ln = vi()
        words.append(b[p[0]:p[0] + ln].decode("utf-8"))
        p[0] += ln
    return words


def main(dump, encdir):
    vocab = read_vocab(os.path.join(encdir, "vocab.ibk"))
    index = {w: i + 1 for i, w in enumerate(vocab)}
    shards = [dict() for _ in range(SHARDS)]
    boxes = count = 0
    pages = 0
    title = text = page_ns = redirect = None
    with bz2.open(dump, "rb") as f:
        for _, el in ET.iterparse(f, events=("end",)):
            tag = el.tag.rsplit("}", 1)[-1]
            if tag == "title":
                title = el.text or ""
            elif tag == "ns":
                page_ns = el.text
            elif tag == "redirect":
                redirect = True
            elif tag == "text":
                text = el.text or ""
            elif tag == "page":
                if page_ns == "0" and title and not redirect and not SKIP_TITLE.search(title):
                    pages += 1
                    k = key(title)
                    got = facts(text or "") if k else []
                    if got:
                        shards[shard(k)][k] = got
                        boxes += 1
                        count += len(got)
                        if boxes % 20000 == 0:
                            print("boxes", boxes, "facts", count, "pages", pages, flush=True)
                title = text = page_ns = redirect = None
                el.clear()
    out_dir = os.path.join(encdir, "facts")
    os.makedirs(out_dir, exist_ok=True)
    rel = bytearray(b"IBK1R")
    varint(len(NAMES), rel)
    for n in NAMES:
        b = n.encode("utf-8")
        varint(len(b), rel)
        rel += b
    with open(os.path.join(out_dir, "relations.ibk"), "wb") as f:
        f.write(rel)
    rid = {n: i for i, n in enumerate(NAMES)}
    raw = coded = 0
    for i, entries in enumerate(shards):
        out = bytearray(b"IBK1F")
        varint(len(entries), out)
        for k, got in entries.items():
            kb = k.encode("utf-8")
            varint(len(kb), out)
            out += kb
            varint(len(got), out)
            for r, v in got:
                p = code_text(v, index)
                back = decode(p, vocab)
                if back != normalised(v):
                    raise SystemExit("round trip failed: %r %r" % (v, back))
                out.append(rid[r])
                varint(len(p), out)
                out += p
                raw += len(v.encode("utf-8"))
                coded += len(p)
        with open(os.path.join(out_dir, "%03d.ibk" % i), "wb") as f:
            f.write(out)
    total = sum(os.path.getsize(os.path.join(out_dir, n)) for n in os.listdir(out_dir))
    print("done: articles with facts", boxes, "facts", count, "text", raw, "coded", coded, "files", total)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
