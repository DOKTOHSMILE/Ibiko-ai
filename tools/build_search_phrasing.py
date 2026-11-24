#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/language/search_phrasing.tsv.ibk: how to phrase a web search for the most results.

A search engine matches words, not sentences. "Can you please tell me who wrote Things Fall Apart?" finds fewer good
pages than "Things Fall Apart author": the asking words ("can you please tell me") match nothing useful, and the
question's own shape ("who wrote X") is said better as the thing and what is wanted of it ("X author"). Ibiko v1
(ai.ibiko.ibiko1.formulas.SearchPhrase) turns a question into search words with these rules, and when a search finds
nothing it tries again broader: the subject alone, then the question as it was asked.

One rule a line, kind first:
  DROP   an opening that asks without meaning anything to a search ("can you tell me", "please", "I want to know")
  SHAPE  a question's shape (a regular expression, matched on the whole question, any case) and the search words for
         it ($1, $2 are what the brackets caught): "who wrote (.+)" -> "$1 author"
  FRESH  a word that means "now" ("latest", "current", "today"): the search gets this year, so old pages rank lower
  STOP   words that carry no meaning in a search ("the", "is", "of"): left out of the search words
SHAPE rules are tried in order, the most particular first.

Coded like the rest of the knowledge (tools/encode_text.py). Add a line to teach Ibiko a better way to search.

Usage: tools/build_search_phrasing.py
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "language", "search_phrasing.tsv")

DROP = [
    r"(?:hey|hi|hello|ok|okay|so|um|well),?\s+(?:ibiko,?\s+)?",
    r"ibiko,?\s+",
    r"(?:can|could|would|will) you (?:please )?(?:tell me|let me know|find out|find|search(?: the web)?(?: for)?|look up|check|google|explain(?: to me)?)\s+",
    r"(?:please )?(?:tell me|let me know|find out|search(?: the web)?(?: for)?|look up|google|check)\s+",
    r"(?:i (?:want|would like|'d like|need) to know|i wonder|i was wondering|i'm curious|do you know|any idea)\s*(?:,|if|whether)?\s*",
    r"please,?\s+",
]

# (shape, search words): the most particular first
SHAPE = [
    # meanings and differences
    (r"what does (.+?) mean", "$1 meaning"),
    (r"what is the meaning of (.+)", "$1 meaning"),
    (r"what'?s the meaning of (.+)", "$1 meaning"),
    (r"(?:what is|what's) the difference between (.+?) and (.+)", "$1 vs $2"),
    (r"(?:is|which is) (.+?) (?:better|faster|cheaper|bigger) than (.+)", "$1 vs $2"),
    (r"how do you (?:say|spell) (.+)", "how to say $1"),
    # people and their works
    (r"when (?:was|is) (.+?) born", "$1 date of birth"),
    (r"where (?:was|is) (.+?) born", "$1 birthplace"),
    (r"when did (.+?) die", "$1 death date"),
    (r"how did (.+?) die", "$1 cause of death"),
    (r"how old is (.+)", "$1 age"),
    (r"who (?:wrote|authored|penned) (.+)", "$1 author"),
    (r"who (?:invented|created|made|built|designed) (.+)", "$1 inventor"),
    (r"who discovered (.+)", "$1 discoverer"),
    (r"who (?:founded|started|established) (.+)", "$1 founder"),
    (r"who (?:directed) (.+)", "$1 director"),
    (r"who (?:sang|sings|performed) (.+)", "$1 singer"),
    (r"who (?:owns|bought) (.+)", "$1 owner"),
    (r"who won (.+)", "$1 winner"),
    (r"who (?:is|was) the (president|prime minister|king|queen|governor|ceo|founder|owner|captain|coach|leader|chairman|minister of \w+) of (.+)", "$2 $1"),
    # things about places and things
    (r"(?:what is|what's) the (population|capital|currency|language|area|size|height|length|weight|price|cost|history|origin|meaning|climate|age|value|speed|distance|temperature|address|time zone|national anthem|flag|motto) of (.+)", "$2 $1"),
    (r"how many people (?:live|are there) in (.+)", "$1 population"),
    (r"(?:what|which) (?:language|languages) (?:is|are) spoken in (.+)", "$1 languages spoken"),
    (r"(?:what|which) currency (?:is used|do they use) in (.+)", "$1 currency"),
    (r"how (?:tall|high) is (.+)", "$1 height"),
    (r"how long is (.+)", "$1 length"),
    (r"how far is (.+?) from (.+)", "$1 to $2 distance"),
    (r"how far is (.+)", "$1 distance"),
    (r"how deep is (.+)", "$1 depth"),
    (r"how (?:big|large) is (.+)", "$1 size"),
    (r"how heavy is (.+)", "$1 weight"),
    (r"how much (?:does|do|is|are) (.+?)(?: cost| costs| worth)?", "$1 price"),
    (r"where (?:is|are) (.+?)(?: located)?", "$1 location"),
    (r"when (?:is|was|will be|does) (.+?)(?: start| begin| happen| take place| held)?", "$1 date"),
    (r"when did (.+?) (?:start|begin|happen|end|take place)", "$1 date"),
    # how and why
    (r"how (?:do|can|should|would) (?:i|you|we|one) (.+)", "how to $1"),
    (r"how to (.+)", "how to $1"),
    (r"how does (.+?) work", "how $1 works"),
    (r"what causes (.+)", "$1 causes"),
    (r"what (?:are|is) the (?:symptoms|signs) of (.+)", "$1 symptoms"),
    (r"what (?:are|is) the (?:benefits|advantages|uses) of (.+)", "$1 benefits"),
    (r"what (?:are|is) the (?:side effects|risks|dangers|disadvantages) of (.+)", "$1 side effects"),
    (r"(?:what is|what's) the best (.+)", "best $1"),
    (r"(?:what are|what're) (?:some )?(?:good|the best) (.+)", "best $1"),
    (r"why (?:is|are|do|does|did|was|were|can't|cannot|don't|doesn't) (.+)", "why $1"),
    # events
    (r"what (?:happened|has happened) (?:to|in|at|with|on) (.+)", "$1 what happened"),
    (r"(?:what is|what's) (?:happening|going on) (?:in|at|with) (.+)", "$1 latest news"),
    (r"(?:what is|what's) the (?:latest|news) (?:on|about|in) (.+)", "$1 latest news"),
    # what a thing is: the thing alone finds most
    (r"(?:what|who) (?:is|are|was|were) (?:an? |the )?(.+)", "$1"),
    (r"(?:what's|who's|whats|whos) (?:an? |the )?(.+)", "$1"),
    (r"(?:define|explain|describe) (?:an? |the )?(.+)", "$1"),
]

FRESH = ["latest", "current", "currently", "today", "now", "right now", "this year", "recent", "recently", "newest",
         "so far", "at the moment", "these days", "still"]

STOP = ("a an the is are was were be been being am do does did of in on at to for from by with about as into than "
        "that this these those it its it's what which who whom whose when where why how there their they them "
        "i me my you your we our us he she his her him and or but if so can could would should will shall may "
        "might must have has had just really very please tell know any some").split()


def main():
    lines = ["# How to phrase a web search for the most results (tools/build_search_phrasing.py): kind, rule, search words."]
    for d in DROP:
        re.compile("(?i)^(?:" + d + ")")
        lines.append("DROP\t" + d + "\t")
    seen = set()
    for shape, words in SHAPE:
        assert shape not in seen, shape
        seen.add(shape)
        p = re.compile("(?i)^(?:" + shape + ")$")
        n = max([int(x) for x in re.findall(r"\$(\d)", words)] or [0])
        assert n <= p.groups, (shape, words)
        lines.append("SHAPE\t" + shape + "\t" + words)
    for f in FRESH:
        lines.append("FRESH\t" + f + "\t")
    lines.append("STOP\t" + " ".join(STOP) + "\t")
    assert all(l.count("\t") == 2 for l in lines[1:])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d ways to drop, %d shapes, %d fresh words, %d stop words" % (len(DROP), len(SHAPE), len(FRESH), len(STOP)))


if __name__ == "__main__":
    main()
