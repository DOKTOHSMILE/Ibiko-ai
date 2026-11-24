#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/language/plain_words.tsv.ibk: hard words and their plain ones.

When someone asks for an answer "more simply", Ibiko v1 says it again with these: "utilise" becomes "use", "approximately"
becomes "about", "organisms" becomes "living things". One line a pair: the hard word or phrase, then the plain one. Longer
phrases are tried first. A plain word must be shorter or more common than the hard one, and never the same word.

Coded like the rest of the knowledge (tools/encode_text.py), read back by ai.ibiko.ibiko1.TextCode.

Usage: tools/build_plain_words.py
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "language", "plain_words.tsv")

PAIRS = """
utilise|use
utilize|use
utilised|used
utilized|used
approximately|about
additionally|also
subsequently|later
consequently|so
nevertheless|still
furthermore|also
therefore|so
however|but
commence|start
commenced|started
terminate|end
terminated|ended
facilitate|help
facilitates|helps
demonstrate|show
demonstrates|shows
demonstrated|showed
indicate|show
indicates|shows
obtain|get
obtained|got
obtains|gets
purchase|buy
require|need
requires|needs
required|needed
sufficient|enough
insufficient|not enough
numerous|many
a large number of|many
a majority of|most
in order to|to
prior to|before
subsequent to|after
in addition to|as well as
with regard to|about
in the event that|if
due to the fact that|because
at this point in time|now
assist|help
assists|helps
attempt|try
attempts|tries
construct|build
constructed|built
convert|turn
converts|turns
converted|turned
produce|make
produces|makes
produced|made
consume|use up
consumes|uses up
consumption|use
modify|change
modified|changed
modification|change
component|part
components|parts
individuals|people
individual|person
organisms|living things
organism|living thing
inhabitants|people who live there
inhabit|live in
inhabits|lives in
reside|live
resides|lives
residence|home
vehicle|car
vehicles|cars
beverage|drink
beverages|drinks
physician|doctor
physicians|doctors
initial|first
initially|at first
ultimately|in the end
frequently|often
occasionally|sometimes
rapidly|fast
rapid|fast
substantial|large
substantially|a lot
significant|important
significantly|a lot
primary|main
primarily|mainly
principal|main
fundamental|basic
essential|needed
optimal|best
maximum|most
minimum|least
comprehend|understand
comprehensive|full
acquire|get
acquired|got
endeavour|try
endeavor|try
anticipate|expect
inquire|ask
enquire|ask
regarding|about
concerning|about
transmit|send
transmits|sends
transmitted|sent
eliminate|remove
eliminates|removes
eliminated|removed
accumulate|build up
accumulates|builds up
diminish|shrink
diminishes|shrinks
enhance|improve
enhances|improves
encounter|meet
encounters|meets
expenditure|spending
remuneration|pay
residential|living
predominantly|mostly
approximate|rough
equivalent|equal
adjacent|next to
proximity|nearness
methodology|method
utilisation|use
utilization|use
implement|carry out
implemented|carried out
objective|aim
objectives|aims
the process by which|how
a process in which|how
the means by which|how
whereby|by which
thereby|so
chemical energy|energy stored in food
light energy|energy from light
glucose|sugar
chlorophyll|the green colouring in leaves
carbon dioxide|the gas we breathe out
oxygen is released|oxygen is given off
precipitation|rain or snow
evaporation|water turning into vapour
condensation|vapour turning back into water
""".strip()


def main():
    seen = set()
    lines = ["# Hard words and their plain ones (tools/build_plain_words.py): hard, plain"]
    for raw in PAIRS.splitlines():
        hard, plain = raw.split("|")
        hard, plain = hard.strip(), plain.strip()
        assert hard and plain and hard != plain, raw
        assert hard not in seen, "twice: " + hard
        assert "\t" not in hard + plain
        seen.add(hard)
        lines.append(hard + "\t" + plain)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d plain words" % (len(lines) - 1))


if __name__ == "__main__":
    main()
