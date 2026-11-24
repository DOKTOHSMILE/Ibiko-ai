#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/language/expressions.tsv.ibk: idioms and sayings, and what they mean.

"Me, myself and I", "break a leg", "spill the beans", "no wahala": the words of a saying don't add up to what it means,
so it can't be worked out from the dictionary. Each is kept whole with its meaning: Wiktionary's (through kaikki.org,
CC BY-SA 4.0 and GFDL, see WIKTIONARY_LICENSE.txt) for the English ones, and a meaning written here for the Nigerian
ones Wiktionary lacks. A note can say more: what a saying implies when it's played with ("just the 3 of us").

One line a saying:  phrase <TAB> meaning <TAB> note
Ibiko v1 (ai.ibiko.ibiko1.formulas.Expressions) finds a saying in what's said and explains it when asked what it means
("what does this imply?"), or when the saying is all that was said.

Usage: tools/build_expressions.py [--cache DIR]
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_lexicon_more import fetch  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "language", "expressions.tsv")

# what a saying implies, beyond its meaning
NOTES = {
    "me, myself and i": "Three words, one person: “me”, “myself” and “I” are all you. So “just the 3 of us” is a joke: it's only you. "
                        "It can mean you're happy in your own company and rely on yourself, or, said with a sigh, that you're a bit lonely.",
    "break a leg": "It's said to wish someone luck, not harm: people in theatre thought wishing “good luck” outright was bad luck.",
    "it is what it is": "It accepts something that can't be changed: there's no use worrying about it.",
}

# Nigerian and Pidgin sayings: meanings written here (Wiktionary has few of them)
OWN = [
    ("no wahala", "No problem; it's fine; don't worry about it.", ""),
    ("no be small thing", "It's no small matter; it's a big deal.", ""),
    ("e go better", "It will get better; things will improve.", "Said to encourage someone in a hard time."),
    ("god dey", "God is there: things will work out.", "Said to encourage, or to put up with a hard time."),
    ("how far", "Hello, how are you? What's up?", "A Nigerian greeting, not a question about distance."),
    ("wetin dey happen", "What's happening? What's going on?", ""),
    ("i no send you", "I don't care about you; you don't matter to me.", "Dismissive: said to someone whose opinion is ignored."),
    ("shine your eye", "Be alert; be careful; watch out.", ""),
    ("e choke", "It's overwhelming or impressive.", "Said of something amazing, or too much."),
    ("na so", "That's how it is; exactly.", ""),
    ("abeg no vex", "Please don't be angry; I'm sorry.", ""),
    ("e don do", "That's enough; it's over.", ""),
    ("as e dey hot", "Right now, while it's fresh: the latest.", "Said of news: hot off the press."),
    ("sharp sharp", "Quickly; right away.", ""),
    ("small small", "Little by little; gradually.", ""),
    ("japa", "To leave Nigeria for a better life abroad.", "From Yoruba, “to run away”."),
    ("e be like say", "It seems that; it looks like.", ""),
    ("make we go", "Let's go.", ""),
    ("i dey", "I'm fine; I'm here.", "A reply to “how far?”."),
    ("na wa", "Wow; that's surprising (or shocking).", ""),
    ("o ga o", "This is serious; things are bad.", ""),
    ("long throat", "Greed; wanting more than one's share.", ""),
    ("bottom power", "Using sex or charm to get favours.", ""),
    ("long leg", "Connections: knowing people in power who can help.", "Like “having friends in high places”."),
    ("eye service", "Working hard only when the boss is watching.", ""),
    ("to put sand in someone's garri", "To spoil someone's good fortune or plans.", ""),
    ("the coast is clear", "There's no one around to see; it's safe now.", ""),
    # common English idioms Wiktionary files under another title: meanings written here
    ("cost an arm and a leg", "To be very expensive.", ""),
    ("the ball is in your court", "It's your turn to decide or act.", ""),
    ("get out of hand", "To become hard to control.", ""),
    ("hang in there", "Don't give up; keep going through a hard time.", ""),
    ("the last straw", "The last of a series of problems, which finally makes someone lose patience.", ""),
    ("the best of both worlds", "The advantages of two different things at once.", ""),
    ("a blessing in disguise", "Something that seems bad at first but turns out to be good.", ""),
    ("cry over spilled milk", "To be upset about something that's already happened and can't be changed.", "Usually said as advice: don't."),
    ("don't count your chickens before they hatch", "Don't rely on something good happening before it has happened.", ""),
    ("in hot water", "In trouble.", ""),
    ("it's raining cats and dogs", "It's raining very heavily.", ""),
    ("play it by ear", "To decide what to do as things happen, without a plan.", ""),
    ("take it with a grain of salt", "Don't believe it completely; be a little doubtful.", ""),
    ("the elephant in the room", "An obvious problem that everyone avoids talking about.", ""),
    ("cat got your tongue", "Why are you so quiet? Why don't you say something?", ""),
    ("give the benefit of the doubt", "To believe someone, or think well of them, when you can't be sure.", ""),
    ("hold your horses", "Wait; slow down; be patient.", ""),
    ("keep your chin up", "Stay cheerful in a hard time.", ""),
    ("like a fish out of water", "Uncomfortable in an unfamiliar place or situation.", ""),
    ("make a long story short", "To tell only the main point.", ""),
    ("method to the madness", "A sensible purpose behind what looks like strange behaviour.", ""),
    ("not my cup of tea", "Not something I like or enjoy.", ""),
    ("out of the frying pan into the fire", "From a bad situation into a worse one.", ""),
    ("pull yourself together", "Calm down and take control of your feelings.", ""),
    ("rome wasn't built in a day", "Important things take time.", ""),
    ("the pot calling the kettle black", "Criticising someone for a fault you have yourself.", ""),
    ("all in the same boat", "In the same difficult situation as others.", ""),
    ("apple of one's eye", "Someone who is loved very much.", ""),
    ("as easy as pie", "Very easy.", ""),
    ("blue in the face", "Exhausted from trying (\"talk till you're blue in the face\": no one will listen).", ""),
    ("burn bridges", "To ruin a relationship so you can't go back.", ""),
    ("can't hold a candle to", "Is not nearly as good as.", ""),
    ("don't put the cart before the horse", "Do things in the right order.", ""),
    ("get a taste of one's own medicine", "To be treated the way you have treated others.", ""),
    ("go cold turkey", "To stop a habit suddenly and completely.", ""),
    ("have a chip on one's shoulder", "To hold a grudge, or be easily offended about something.", ""),
    ("it's not rocket science", "It isn't hard to understand.", ""),
    ("know something like the back of one's hand", "To know something very well.", ""),
    ("my two cents", "My opinion.", ""),
    ("nip something in the bud", "To stop a problem early, before it grows.", ""),
    ("play devil's advocate", "To argue the other side, to test an idea.", ""),
    ("sleep on it", "To wait until tomorrow before deciding.", ""),
    ("slipped one's mind", "Was forgotten.", ""),
    ("start from scratch", "To begin again from the very start.", ""),
    ("think outside the box", "To think in a new, creative way.", ""),
    ("whole new ball game", "A completely different situation.", ""),
    ("you can't have your cake and eat it too", "You can't have two things that don't go together.", ""),
    ("don't bite the hand that feeds you", "Don't harm those who help or support you.", ""),
    ("look before you leap", "Think carefully before acting.", ""),
    ("you can lead a horse to water, but you can't make it drink", "You can give someone a chance, but you can't make them take it.", ""),
    ("fomo", "Fear of missing out: worry that others are having fun or chances without you.", ""),
]

# English idioms and sayings, meanings from Wiktionary
PHRASES = """me, myself and I
break a leg
spill the beans
piece of cake
under the weather
bite the bullet
hit the nail on the head
let the cat out of the bag
once in a blue moon
cost an arm and a leg
the ball is in your court
beat around the bush
cut corners
get out of hand
hang in there
call it a day
on the same page
pull someone's leg
speak of the devil
the last straw
the best of both worlds
a blessing in disguise
better late than never
bite off more than one can chew
burn the midnight oil
cry over spilled milk
cry wolf
curiosity killed the cat
don't count your chickens before they hatch
every cloud has a silver lining
get cold feet
give someone the cold shoulder
go the extra mile
hit the sack
in hot water
it's raining cats and dogs
jump on the bandwagon
keep an eye on
kill two birds with one stone
leave no stone unturned
miss the boat
no pain, no gain
on thin ice
out of the blue
over the moon
play it by ear
see eye to eye
sit on the fence
take it with a grain of salt
the elephant in the room
throw in the towel
time flies
under the radar
when pigs fly
you can't judge a book by its cover
actions speak louder than words
add insult to injury
at the drop of a hat
back to square one
barking up the wrong tree
beat a dead horse
between a rock and a hard place
birds of a feather flock together
blow off steam
break the ice
by the skin of one's teeth
call a spade a spade
cat got your tongue
caught red-handed
cold turkey
cut to the chase
devil's advocate
down to earth
easier said than done
every dog has its day
face the music
fit as a fiddle
get the ball rolling
get the hang of
give the benefit of the doubt
go down in flames
go with the flow
golden opportunity
good things come to those who wait
hit the books
hit the road
hold your horses
in a nutshell
it takes two to tango
keep your chin up
know the ropes
let sleeping dogs lie
like a fish out of water
make a long story short
method to the madness
money doesn't grow on trees
not my cup of tea
on cloud nine
on the fence
out of the frying pan into the fire
penny for your thoughts
pull yourself together
put all one's eggs in one basket
rain on someone's parade
read between the lines
rome wasn't built in a day
rule of thumb
second nature
set in stone
spill the tea
steal someone's thunder
take a rain check
the early bird catches the worm
the pot calling the kettle black
the whole nine yards
through thick and thin
throw caution to the wind
tie the knot
up in the air
wild goose chase
wrap one's head around
your guess is as good as mine
a dime a dozen
a penny saved is a penny earned
all ears
all in the same boat
apple of one's eye
as easy as pie
at the end of the day
bad apple
ballpark figure
beggars can't be choosers
behind closed doors
bend over backwards
best thing since sliced bread
big fish in a small pond
bite the dust
blood is thicker than water
blue in the face
bring home the bacon
burn bridges
bury the hatchet
by the book
can't hold a candle to
change of heart
clear the air
close but no cigar
come rain or shine
cool as a cucumber
couch potato
cross that bridge when one comes to it
cut someone some slack
dark horse
dead ringer
don't put the cart before the horse
draw the line
drop in the bucket
eat one's words
every now and then
fair and square
far cry
few and far between
fish out of water
from scratch
get a taste of one's own medicine
give someone a hand
go back to the drawing board
go cold turkey
green with envy
hands down
have a chip on one's shoulder
head over heels
heart of gold
high and dry
hit the jackpot
in the nick of time
it's not rocket science
jack of all trades
jump the gun
keep one's fingers crossed
kick the bucket
know something like the back of one's hand
let off steam
lose one's touch
make ends meet
make hay while the sun shines
mind one's own business
my two cents
neck and neck
nest egg
nip something in the bud
no strings attached
off the hook
off the record
on the ball
on the dot
once bitten, twice shy
out of the woods
over one's head
pay through the nose
pie in the sky
play devil's advocate
practice makes perfect
put a sock in it
put one's foot down
put one's foot in one's mouth
rings a bell
rock the boat
run out of steam
save face
see the light
shoot from the hip
sleep on it
slipped one's mind
smell a rat
snowball's chance in hell
spitting image
start from scratch
stick to one's guns
straight from the horse's mouth
take the bull by the horns
talk of the town
the apple doesn't fall far from the tree
the grass is always greener on the other side
there's no place like home
think outside the box
to each their own
tongue in cheek
turn a blind eye
two heads are better than one
under one's breath
up to one's ears
walk on eggshells
water under the bridge
wear one's heart on one's sleeve
whole new ball game
with flying colors
you can't have your cake and eat it too
a picture is worth a thousand words
absence makes the heart grow fonder
all good things must come to an end
an apple a day keeps the doctor away
don't bite the hand that feeds you
don't judge a book by its cover
great minds think alike
honesty is the best policy
if it ain't broke, don't fix it
ignorance is bliss
knowledge is power
laughter is the best medicine
look before you leap
the pen is mightier than the sword
there's no such thing as a free lunch
too many cooks spoil the broth
when in Rome, do as the Romans do
where there's smoke, there's fire
you can lead a horse to water, but you can't make it drink
it is what it is
yolo
fomo
ghost
spill the tea
no cap
low-key
high-key
on fleek
throw shade
glow up
"""


def main():
    cache = sys.argv[sys.argv.index("--cache") + 1] if "--cache" in sys.argv else os.path.join("/tmp", "kaikki-cache")
    os.makedirs(cache, exist_ok=True)
    import json
    lines = ["# Idioms and sayings (tools/build_expressions.py): phrase, meaning, note"]
    seen = set()
    for phrase, meaning, note in OWN:
        lines.append("\t".join([phrase, meaning, note]))
        seen.add(phrase)
    missing = []
    for p in [x.strip() for x in PHRASES.splitlines() if x.strip()]:
        key = p.lower()
        if key in seen:
            continue
        meaning = None
        for line in fetch(p, cache).splitlines():
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if d.get("lang_code") != "en":
                continue
            for s in d.get("senses", []):
                g = (s.get("glosses") or [None])[-1]
                if g and not set(s.get("tags", [])) & {"obsolete", "archaic", "rare"}:
                    meaning = " ".join(g.split())
                    break
            if meaning:
                break
        if not meaning:
            missing.append(p)
            continue
        seen.add(key)
        lines.append("\t".join([key, meaning.replace("\t", " "), NOTES.get(key, "")]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d sayings; not in Wiktionary: %s" % (len(lines) - 1, ", ".join(missing) or "none"))


if __name__ == "__main__":
    main()
