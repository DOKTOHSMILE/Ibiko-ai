#!/usr/bin/env python3
"""
Builds knowledge/wikidata_facts.tsv: more of what Ibiko v1 knows the moment it is installed, from Wikidata (CC0).
One line a fact: the thing, what about it, the value (the BuiltIn format).

    python3 tools/build_wikidata_facts.py <out.tsv>

  Nigeria: every local government area (its state), the states (created, area), towns and cities (state, population).
  The world: cities of 100,000 or more (country, population); the most-linked inventions and discoveries (inventor,
  year); the most notable people (born, died, nationality, occupation); mountains (height, country), rivers (length,
  country), lakes (area, country).

Values are checked (dates real, numbers positive) and the names are the English labels. The file is then coded into
the app's text code: python3 tools/encode_text.py <out.tsv> app/src/main/assets/knowledge/wikidata_facts.tsv.ibk
"""
import csv
import re
import io
import sys
import time
import urllib.parse
import urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
AGENT = "IbikoFactsBuilder/1.0 (offline knowledge pack for a phone assistant)"

QUERIES = {
    "lga": """
SELECT ?lgaLabel ?stateLabel WHERE {
  ?lga wdt:P31 wd:Q1639634; wdt:P131 ?state. ?state wdt:P31 wd:Q465842.
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } }""",
    "state": """
SELECT ?sLabel (MIN(YEAR(?inc)) AS ?y) (MAX(?area) AS ?a) WHERE {
  ?s wdt:P31 wd:Q465842. OPTIONAL { ?s wdt:P571 ?inc. } OPTIONAL { ?s p:P2046/psn:P2046/wikibase:quantityAmount ?areaM. BIND(?areaM/1000000 AS ?area) }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?sLabel""",
    "ngtown": """
SELECT ?tLabel ?stateLabel (MAX(?pop) AS ?p) WHERE {
  VALUES ?cls { wd:Q515 wd:Q3957 wd:Q532 wd:Q1549591 wd:Q7930989 }
  ?t wdt:P31 ?cls; wdt:P17 wd:Q1033; wdt:P131* ?state. ?state wdt:P31 wd:Q465842.
  OPTIONAL { ?t wdt:P1082 ?pop. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?tLabel ?stateLabel""",
    "city": """
SELECT ?cLabel ?countryLabel (MAX(?pop) AS ?p) WHERE {
  ?c wdt:P31/wdt:P279* wd:Q515; wdt:P1082 ?pop; wdt:P17 ?country. FILTER(?pop >= 100000)
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?cLabel ?countryLabel""",
    "invention": """
SELECT ?iLabel ?byLabel (MIN(YEAR(?when)) AS ?y) WHERE {
  ?i wdt:P61 ?by; wikibase:sitelinks ?links. FILTER(?links >= 25)
  ?by wdt:P31 wd:Q5.
  OPTIONAL { ?i wdt:P575 ?when. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?iLabel ?byLabel""",
    # people in two light queries (one heavy one timed out on the public endpoint)
    "person_dates": """
SELECT ?pLabel (MIN(?b) AS ?born) (MIN(?d) AS ?died) WHERE {
  ?p wikibase:sitelinks ?links. FILTER(?links >= 100) ?p wdt:P31 wd:Q5.
  OPTIONAL { ?p wdt:P569 ?b. } OPTIONAL { ?p wdt:P570 ?d. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?pLabel""",
    "person_roles": """
SELECT ?pLabel (SAMPLE(?n) AS ?nat) (SAMPLE(?o) AS ?occ) WHERE {
  ?p wikibase:sitelinks ?links. FILTER(?links >= 100) ?p wdt:P31 wd:Q5.
  OPTIONAL { ?p wdt:P27 ?n0. ?n0 rdfs:label ?n. FILTER(LANG(?n) = "en") }
  OPTIONAL { ?p wdt:P106 ?o0. ?o0 rdfs:label ?o. FILTER(LANG(?o) = "en") }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?pLabel""",
    # one kind of peak a query (all of them at once timed out): mountains, volcanoes, stratovolcanoes, massifs
    "mountain": """
SELECT ?mLabel ?countryLabel (MAX(?h) AS ?height) WHERE {
  ?m wdt:P31 wd:%s; wdt:P2044 ?h; wikibase:sitelinks ?links. FILTER(?links >= 15)
  OPTIONAL { ?m wdt:P17 ?country. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?mLabel ?countryLabel""",
    "river": """
SELECT ?rLabel ?countryLabel (MAX(?len) AS ?length) WHERE {
  ?r wdt:P31 wd:Q4022; wdt:P2043 ?len; wikibase:sitelinks ?links. FILTER(?links >= 20)
  OPTIONAL { ?r wdt:P17 ?country. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?rLabel ?countryLabel""",
    "lake": """
SELECT ?lLabel ?countryLabel (MAX(?ar) AS ?area) WHERE {
  ?l wdt:P31 wd:Q23397; p:P2046/psn:P2046/wikibase:quantityAmount ?arM; wikibase:sitelinks ?links. FILTER(?links >= 20)
  BIND(?arM/1000000 AS ?ar)
  OPTIONAL { ?l wdt:P17 ?country. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?lLabel ?countryLabel""",
}


# people, one occupation at a time (a query over every human times out on the public endpoint), and Nigerians
OCCUPATIONS = {"politician": "Q82955", "writer": "Q36180", "scientist": "Q901", "physicist": "Q169470", "chemist": "Q593644",
               "mathematician": "Q170790", "inventor": "Q205375", "musician": "Q639669", "singer": "Q177220", "actor": "Q33999",
               "footballer": "Q937857", "basketball player": "Q3665646", "athlete": "Q2066131", "painter": "Q1028181",
               "philosopher": "Q4964182", "businessperson": "Q43845", "engineer": "Q81096", "explorer": "Q11900058",
               "film director": "Q2526255", "economist": "Q188094", "journalist": "Q1930187", "military officer": "Q189290",
               "religious leader": "Q15995642", "boxer": "Q11338576", "rapper": "Q2252262", "lawyer": "Q40348"}

PEOPLE = """
SELECT ?pLabel (MIN(?b) AS ?born) (MIN(?d) AS ?died) (SAMPLE(?natLabel) AS ?nat) WHERE {
  ?p wdt:P106 wd:%s; wdt:P31 wd:Q5; wikibase:sitelinks ?links. FILTER(?links >= %d)
  OPTIONAL { ?p wdt:P569 ?b. } OPTIONAL { ?p wdt:P570 ?d. }
  OPTIONAL { ?p wdt:P27 ?n. ?n rdfs:label ?natLabel. FILTER(LANG(?natLabel) = "en") }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?pLabel"""

NIGERIANS = """
SELECT ?pLabel (MIN(?b) AS ?born) (MIN(?d) AS ?died) (SAMPLE(?occLabel) AS ?occ) WHERE {
  ?p wdt:P27 wd:Q1033; wdt:P31 wd:Q5; wikibase:sitelinks ?links. FILTER(?links >= 8)
  OPTIONAL { ?p wdt:P569 ?b. } OPTIONAL { ?p wdt:P570 ?d. }
  OPTIONAL { ?p wdt:P106 ?o. ?o rdfs:label ?occLabel. FILTER(LANG(?occLabel) = "en") }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". } } GROUP BY ?pLabel"""


# the official number of local government areas in each state (774 in all): Wikidata's lists disagree with it in
# places (towns or development areas filed as LGAs), so the count is said from here, and a state's list of names is
# kept only where it matches this count exactly
OFFICIAL_LGAS = {"Abia State": 17, "Adamawa State": 21, "Akwa Ibom State": 31, "Anambra State": 21, "Bauchi State": 20,
                 "Bayelsa State": 8, "Benue State": 23, "Borno State": 27, "Cross River State": 18, "Delta State": 25,
                 "Ebonyi State": 13, "Edo State": 18, "Ekiti State": 16, "Enugu State": 17, "Gombe State": 11, "Imo State": 27,
                 "Jigawa State": 27, "Kaduna State": 23, "Kano State": 44, "Katsina State": 34, "Kebbi State": 21,
                 "Kogi State": 21, "Kwara State": 16, "Lagos State": 20, "Nasarawa State": 13, "Niger State": 25,
                 "Ogun State": 20, "Ondo State": 18, "Osun State": 30, "Oyo State": 33, "Plateau State": 17,
                 "Rivers State": 23, "Sokoto State": 23, "Taraba State": 16, "Yobe State": 17, "Zamfara State": 14,
                 "Federal Capital Territory": 6}
# towns Wikidata files as LGAs of a state, which aren't (checked by hand)
NOT_LGAS = {("Delta State", "Warri"), ("Delta State", "Ozoro")}


def fetch(name, query, tries=4):
    url = ENDPOINT + "?" + urllib.parse.urlencode({"query": query})
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={"Accept": "text/csv", "User-Agent": AGENT})
            with urllib.request.urlopen(req, timeout=170) as r:
                rows = list(csv.DictReader(io.StringIO(r.read().decode("utf-8"))))
                print(name, len(rows), "rows", flush=True)
                return rows
        except Exception as e:  # a busy endpoint: wait and ask again
            print(name, "try", k + 1, "failed:", e, flush=True)
            time.sleep(10 * (k + 1))
    return []


def good(label):
    # an unlabelled item comes back as its id ("Q12345"): left out
    return label and not (label[0] == "Q" and label[1:].isdigit()) and "\t" not in label and len(label) < 120


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def whole(v):
    return f"{int(round(v)):,}"


def date(v):
    # "1879-03-14T00:00:00Z" → "14 March 1879"; only a full, real date
    if not v or len(v) < 10 or v.startswith("-"):
        return None
    y, m, d = v[:4], v[5:7], v[8:10]
    months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    try:
        mi, di = int(m), int(d)
        if not (1 <= mi <= 12 and 1 <= di <= 31):
            return None
        if mi == 1 and di == 1:
            return y  # 1 January is how Wikidata stores a year alone
        return f"{di} {months[mi - 1]} {int(y)}"
    except ValueError:
        return None


def main(out, only=None):
    facts = []
    seen = set()

    def add(s, rel, v):
        if not good(s) or v is None or str(v).strip() == "":
            return
        key = (s.lower(), rel, str(v).lower())
        if key not in seen:
            seen.add(key)
            facts.append((s, rel, str(v)))

    lgas = {}
    for r in fetch("lga", QUERIES["lga"]):
        if good(r.get("stateLabel")) and good(r.get("lgaLabel")) and (r["stateLabel"], r["lgaLabel"]) not in NOT_LGAS:
            add(r["lgaLabel"], "state", r["stateLabel"])
            lgas.setdefault(r["stateLabel"], set()).add(r["lgaLabel"])
    for state, n in OFFICIAL_LGAS.items():
        add(state, "local government area count", str(n))
        names = lgas.get(state, set())
        if len(names) == n:
            for lga in sorted(names):
                add(state, "local government area", lga)
    for r in fetch("state", QUERIES["state"]):
        y, a = r.get("y"), num(r.get("a"))
        if y and y.isdigit() and 1900 < int(y) < 2030:
            add(r["sLabel"], "created", y)
        if a and a > 100:
            add(r["sLabel"], "area", whole(a))
    for r in fetch("ngtown", QUERIES["ngtown"]):
        if good(r.get("stateLabel")) and not r["tLabel"].endswith(" State"):
            add(r["tLabel"], "state", r["stateLabel"])
            add(r["tLabel"], "country", "Nigeria")
            p = num(r.get("p"))
            if p and p >= 1000:
                add(r["tLabel"], "population", whole(p))
    for r in fetch("city", QUERIES["city"]):
        if good(r.get("countryLabel")):
            add(r["cLabel"], "country", r["countryLabel"])
            p = num(r.get("p"))
            if p:
                add(r["cLabel"], "population", whole(p))
    sky = re.compile(r"^(\(?\d|NGC |IC |HD |HIP |Arp |Messier |M\d|Kepler|K2-|TOI|WASP|HAT-|Gliese|GJ |C/|P/|\d+P/)|comet|asteroid|galaxy|nebula|cluster|exoplanet|\b(b|c|d)$", re.I)
    for r in fetch("invention", QUERIES["invention"]):
        if sky.search(r["iLabel"]):
            continue  # asteroids, galaxies, comets share Wikidata's "discoverer" field: not inventions
        if good(r.get("byLabel")):
            add(r["iLabel"], "inventor", r["byLabel"])
            if r.get("y") and r["y"].lstrip("-").isdigit() and int(r["y"]) > 0:
                add(r["iLabel"], "invented", r["y"])
    for r in fetch("nigerians", NIGERIANS):
        add(r["pLabel"], "born", date(r.get("born")))
        add(r["pLabel"], "died", date(r.get("died")))
        add(r["pLabel"], "nationality", "Nigeria")
        add(r["pLabel"], "occupation", r.get("occ") if good(r.get("occ")) else None)
    for job, qid in OCCUPATIONS.items():
        for r in fetch(job, PEOPLE % (qid, 45 if job in ("politician", "footballer", "actor", "singer") else 30)):
            add(r["pLabel"], "born", date(r.get("born")))
            add(r["pLabel"], "died", date(r.get("died")))
            add(r["pLabel"], "nationality", r.get("nat") if good(r.get("nat")) else None)
            add(r["pLabel"], "occupation", job)
    peaks = []
    for kind in ("Q8502", "Q8072", "Q169358", "Q1061151", "Q1325302"):
        peaks += fetch("mountain " + kind, QUERIES["mountain"] % kind)
    for r in peaks:
        h = num(r.get("height"))
        if h and 100 < h < 9000:
            add(r["mLabel"], "elevation", whole(h) + " m")
            if good(r.get("countryLabel")):
                add(r["mLabel"], "country", r["countryLabel"])
    for r in fetch("river", QUERIES["river"]):
        L = num(r.get("length"))
        if L and 5 < L < 8000:
            add(r["rLabel"], "length", whole(L) + " km")
            if good(r.get("countryLabel")):
                add(r["rLabel"], "country", r["countryLabel"])
    for r in fetch("lake", QUERIES["lake"]):
        A = num(r.get("area"))
        if A and 1 < A < 400000:
            add(r["lLabel"], "area", whole(A))
            if good(r.get("countryLabel")):
                add(r["lLabel"], "country", r["countryLabel"])
    facts.sort(key=lambda f: (f[0].lower(), f[1]))
    with open(out, "w", encoding="utf-8") as f:
        for s, rel, v in facts:
            f.write(f"{s}\t{rel}\t{v}\n")
    rels = {}
    for _, rel, _ in facts:
        rels[rel] = rels.get(rel, 0) + 1
    print("facts", len(facts), rels)


if __name__ == "__main__":
    main(sys.argv[1])
