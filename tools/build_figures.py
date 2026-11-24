#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/language/figures.tsv.ibk: what the figures-of-speech engine needs to know
that a dictionary doesn't say.

Ibiko v1 (ai.ibiko.ibiko1.language.Figures) tells figures of speech apart with separate classifiers: an idiom isn't
a metaphor, irony isn't sarcasm. Most of each classifier is a rule (a simile is "as ADJ as a N" or "VERB like a N";
alliteration is the same first sound three times); this table holds the part that has to be known:

  euphemism      phrase <TAB> what it softens        "passed away"       died
  onomatopoeia   word                                "sizzle"
  hyperbole      phrase <TAB> what it really means   "a million times"   very many times
  oxymoron       phrase                              "deafening silence"
  opposed        word <TAB> word                     loud, silent: words whose meanings pull apart (for new oxymorons)
  paradox        phrase <TAB> what it means          "less is more"
  metaphor       phrase <TAB> what it means          "time is money"     (the conventional ones; new ones are reasoned)
  role           who <TAB> what they are there to prevent   firefighter    fire burn burned burnt blaze
  sarcasm        phrase                              "oh great", "thanks a lot", "yeah right"
  human          verb                                whisper: what only people do (personification when a thing does it)
  homophone      word <TAB> word                     their, there
  pun            phrase <TAB> the two meanings       "lost interest"     curiosity; money a bank pays

One line an entry: kind <TAB> phrase <TAB> meaning. Written here (CC0); none of it is copied from elsewhere.

Usage: tools/build_figures.py   (then it is coded: tools/encode_text.py <tsv> --remove)
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "language", "figures.tsv")

EUPHEMISM = {
    "passed away": "died", "passed on": "died", "pass away": "die", "no longer with us": "dead", "lost his life": "died",
    "lost her life": "died", "lost their lives": "died", "departed this life": "died", "gone to a better place": "died",
    "resting in peace": "dead", "breathed his last": "died", "breathed her last": "died", "put to sleep": "killed humanely (an animal)",
    "put down": "killed humanely (an animal)", "let go": "fired from a job", "laid off": "dismissed from a job",
    "between jobs": "unemployed", "downsizing": "cutting jobs", "rightsizing": "cutting jobs", "early retirement": "being made to leave a job",
    "correctional facility": "prison", "detention centre": "prison", "collateral damage": "civilians killed or harmed",
    "friendly fire": "being shot by one's own side", "enhanced interrogation": "torture", "neutralised": "killed",
    "neutralized": "killed", "ethnic cleansing": "mass killing or expulsion of an ethnic group", "economically disadvantaged": "poor",
    "less fortunate": "poor", "low-income": "poor", "pre-owned": "used, second-hand", "previously owned": "used, second-hand",
    "senior citizen": "old person", "golden years": "old age", "big-boned": "fat", "full-figured": "fat", "on the heavy side": "fat",
    "vertically challenged": "short", "visually impaired": "blind", "hearing impaired": "deaf", "differently abled": "disabled",
    "restroom": "toilet", "bathroom": "toilet", "powder room": "toilet", "the little boys' room": "toilet",
    "answer the call of nature": "go to the toilet", "spend a penny": "go to the toilet", "under the weather": "ill",
    "tired and emotional": "drunk", "had a few": "drunk", "expecting": "pregnant", "in the family way": "pregnant",
    "sleep together": "have sex", "make love": "have sex", "economical with the truth": "lying", "misspoke": "lied or made a mistake",
    "alternative facts": "falsehoods", "creative accounting": "dishonest accounting", "negative growth": "decline",
    "revenue enhancement": "tax increase", "wardrobe malfunction": "accidental exposure", "adult entertainment": "pornography",
    "a little thin on top": "going bald", "of a certain age": "middle-aged or old", "between the sheets": "having sex",
    "relieve oneself": "urinate or defecate", "sanitation worker": "rubbish collector", "domestic engineer": "housekeeper",
    "pass gas": "fart", "break wind": "fart", "kicked the bucket": "died (a joking, not a polite, way to say it)",
}

ONOMATOPOEIA = """bang boom buzz splash sizzle hiss crash pop meow miaow woof bark tick tock tick-tock thud whoosh zoom clang drip
gurgle murmur roar chirp tweet quack oink moo baa neigh ding dong ding-dong knock beep honk click clatter crackle rustle slurp
snap splat squeak thump vroom achoo ahem boing bonk bump burp chomp clap clink clunk cock-a-doodle-doo cuckoo fizz flutter growl
grunt gulp hiccup hoot howl hum jingle kaboom kerplunk mumble munch oomph ping plop poof pow purr rattle ribbit ring rumble screech
shush sniff snore splish splosh squelch squish swish swoosh tap thwack tinkle toot twang wham whack whiz whizz whirr whir yap zap
zing zip clop clip-clop pitter-patter bow-wow ha-ha hee-hee ho-ho boo cough""".split()

HYPERBOLE = {
    "a million times": "very many times", "a thousand times": "very many times", "a hundred times": "many times",
    "a billion times": "very many times", "told you a million times": "said it many times", "for ages": "for a long time",
    "for ever": "for a very long time", "forever": "for a very long time", "took forever": "took a long time",
    "since the beginning of time": "for a very long time", "a ton": "a lot", "tons of": "a lot of", "weighs a ton": "is very heavy",
    "loads of": "a lot of", "a mile long": "very long", "miles away": "far away", "i'm starving": "I'm very hungry",
    "i am starving": "I'm very hungry", "starving to death": "very hungry", "dying of thirst": "very thirsty", "i'm dying": "I'm suffering (from laughter, heat, boredom…)",
    "could eat a horse": "am very hungry", "dying to": "eager to", "scared to death": "very frightened", "bored to death": "very bored",
    "bored to tears": "very bored", "the best in the world": "very good", "the worst in the world": "very bad",
    "the best thing ever": "very good", "the worst day ever": "a very bad day", "worst day of my life": "a very bad day",
    "never in a million years": "never", "older than dirt": "very old", "as old as the hills": "very old", "a zillion": "very many",
    "zillions": "very many", "gazillion": "very many", "everyone knows": "it's widely known", "nobody ever": "people rarely",
    "my feet are killing me": "my feet hurt", "cried a river": "cried a lot", "an ocean of tears": "a lot of crying",
    "the size of a house": "very big", "faster than lightning": "very fast", "in a split second": "very quickly",
    "a mountain of": "a great deal of", "a sea of": "a great many", "the whole world": "a lot of people", "to the moon and back": "very much",
    "my bag weighs a ton": "my bag is very heavy", "i've told you a million times": "I've said it many times",
}

OXYMORON = """deafening silence jumbo shrimp bittersweet living dead open secret act naturally pretty ugly awfully good seriously funny
original copy only choice clearly confused found missing small crowd old news same difference terribly good virtual reality
working holiday paid volunteer civil war cruel kindness definite maybe passive aggressive alone together liquid gas organized chaos
controlled chaos freezing fire burning cold sweet sorrow wise fool loud whisper silent scream dark light bright darkness
minor crisis friendly fire seriously joking random order exact estimate genuine imitation icy hot little giant""".split("\n")

OXYMORON_PHRASES = [
    "deafening silence", "jumbo shrimp", "bittersweet", "living dead", "open secret", "act naturally", "pretty ugly", "awfully good",
    "seriously funny", "original copy", "only choice", "clearly confused", "found missing", "small crowd", "old news",
    "same difference", "terribly good", "working holiday", "paid volunteer", "cruel kindness", "definite maybe", "alone together",
    "liquid gas", "organized chaos", "organised chaos", "controlled chaos", "freezing fire", "burning cold", "sweet sorrow",
    "wise fool", "loud whisper", "silent scream", "bright darkness", "dark light", "minor crisis", "seriously joking",
    "random order", "exact estimate", "genuine imitation", "icy hot", "little giant", "cold fire", "hot ice", "living death",
    "honest thief", "awful good", "virtual reality", "almost exactly", "unbiased opinion", "constant change", "foolish wisdom",
]

OPPOSED = [
    ("loud", "silent"), ("loud", "silence"), ("deafening", "silence"), ("deafening", "silent"), ("loud", "quiet"), ("loud", "whisper"),
    ("silent", "scream"), ("hot", "cold"), ("hot", "ice"), ("hot", "icy"), ("burning", "cold"), ("freezing", "fire"), ("cold", "fire"),
    ("sweet", "sorrow"), ("bitter", "sweet"), ("wise", "fool"), ("wise", "foolish"), ("foolish", "wisdom"), ("dark", "light"),
    ("bright", "darkness"), ("living", "dead"), ("living", "death"), ("cruel", "kindness"), ("cruel", "kind"), ("open", "secret"),
    ("small", "crowd"), ("small", "huge"), ("tiny", "giant"), ("little", "giant"), ("jumbo", "shrimp"), ("old", "news"),
    ("original", "copy"), ("genuine", "imitation"), ("exact", "estimate"), ("definite", "maybe"), ("alone", "together"),
    ("found", "missing"), ("organized", "chaos"), ("organised", "chaos"), ("controlled", "chaos"), ("random", "order"),
    ("pretty", "ugly"), ("awfully", "good"), ("terribly", "good"), ("seriously", "funny"), ("seriously", "joking"),
    ("honest", "thief"), ("minor", "crisis"), ("same", "difference"), ("constant", "change"), ("clearly", "confused"),
    ("happy", "sad"), ("joyful", "sorrow"), ("peaceful", "war"), ("calm", "storm"), ("slow", "rush"), ("silent", "roar"),
    ("gentle", "violence"), ("beautiful", "ugliness"), ("heavy", "lightness"), ("light", "heavy"), ("love", "hate"),
    ("friendly", "enemy"), ("young", "old"), ("rich", "poverty"), ("poor", "wealth"), ("true", "lie"), ("truthful", "lies"),
]

PARADOX = {
    "less is more": "doing or having less can be better", "the more you learn, the more you realise you don't know": "knowledge shows how much is unknown",
    "the more i learn, the less i know": "knowledge shows how much is unknown", "i know that i know nothing": "wisdom is knowing the limits of what you know",
    "this statement is false": "a sentence that is false if true and true if false (the liar paradox)",
    "you have to spend money to make money": "investment comes before profit", "the only constant is change": "change never stops",
    "the only constant in life is change": "change never stops", "be cruel to be kind": "a hard act can be kind in the end",
    "slow and steady wins the race": "steady effort beats haste", "the beginning of the end": "the start of a decline",
    "war is peace": "a slogan that inverts meaning (Orwell)", "freedom is slavery": "a slogan that inverts meaning (Orwell)",
    "ignorance is strength": "a slogan that inverts meaning (Orwell)", "nobody goes there anymore, it's too crowded": "a self-contradicting remark",
    "do the opposite of what i say": "an instruction that undermines itself", "save money by spending it": "spending now can save later",
    "the child is father of the man": "a person's childhood shapes who they become", "less haste, more speed": "hurrying makes you slower",
    "if you want peace, prepare for war": "strength deters attack", "the more things change, the more they stay the same": "change on the surface hides sameness",
    "what doesn't kill you makes you stronger": "hardship can build strength", "i must be cruel only to be kind": "a hard act can be kind in the end",
    "youth is wasted on the young": "the young don't value what they have", "the sound of silence": "silence felt as something heard",
}

METAPHOR = {
    "time is money": "time has great value", "life is a journey": "life is lived as a path with stages", "life is a rollercoaster": "life has ups and downs",
    "the world is a stage": "people play roles in life", "all the world's a stage": "people play roles in life", "love is a battlefield": "love involves conflict",
    "knowledge is power": "knowing gives you power", "knowledge is light": "knowing makes things clear", "ideas are seeds": "ideas grow when tended",
    "my heart is broken": "I am deeply sad", "a heart of gold": "a very kind nature", "a heart of stone": "an unfeeling nature",
    "the apple of my eye": "the one I love most", "a sea of troubles": "a great many troubles", "drowning in work": "having far too much work",
    "a rollercoaster of emotions": "feelings that rise and fall fast", "the light at the end of the tunnel": "hope after hardship",
    "food for thought": "something worth thinking about", "a melting pot": "a place where cultures mix", "the backbone of": "the main support of",
    "the heart of the city": "the centre of the city", "a storm of protest": "a great deal of protest", "climbing the career ladder": "rising at work",
    "argument is war": "arguing is spoken of as fighting", "time flies": "time passes quickly", "he is a lion": "he is brave",
    "she is a star": "she is outstanding", "a couch potato": "a person who sits watching TV a lot", "a night owl": "a person who stays up late",
    "an early bird": "a person who gets up early", "a dark horse": "someone with unexpected talent", "a rat race": "a tiring, competitive way of life",
    "a glass ceiling": "an unseen barrier to promotion", "a wall of silence": "a refusal to speak", "the tip of the iceberg": "a small sign of a much bigger problem",
}

ROLE = [
    ("fire station", "fire burn burns burned burnt burning blaze"), ("firefighter", "fire burn burns burned burnt burning blaze"),
    ("fire brigade", "fire burn burned burnt blaze"), ("fireman", "fire burn burned burnt"),
    ("police station", "robbed rob robbery stolen theft burgled burglary"), ("police officer", "robbed arrested stolen"),
    ("policeman", "robbed arrested stolen"), ("dentist", "teeth tooth cavity cavities toothache"),
    ("doctor", "sick ill illness disease"), ("nurse", "sick ill"), ("lifeguard", "drown drowns drowned drowning"),
    ("swimming instructor", "drown drowned drowning"), ("marriage counsellor", "divorce divorced divorces"),
    ("marriage counselor", "divorce divorced divorces"), ("pilot", "afraid of flying fear of flying airsick"),
    ("locksmith", "locked out lock out"), ("traffic cop", "speeding ticket crash"), ("driving instructor", "crash crashed accident"),
    ("cardiologist", "heart attack"), ("nutritionist", "junk food obese"), ("fitness trainer", "unfit out of shape"),
    ("security company", "robbed burgled hacked"), ("cyber security firm", "hacked hack breach"), ("antivirus company", "virus hacked"),
    ("weather forecaster", "caught in the rain soaked"), ("chef", "burnt the food food poisoning"), ("plumber", "leak leaking flooded"),
    ("electrician", "power cut no electricity electrocuted"), ("bank", "bankrupt broke robbed"), ("banker", "bankrupt broke"),
    ("mechanic", "broke down breaks down broken down"), ("teacher", "failed the test cannot read"), ("librarian", "never reads"),
    ("health inspector", "food poisoning"), ("spelling bee champion", "misspelled misspells"), ("english teacher", "grammar mistake"),
    ("lawyer", "arrested sued convicted"), ("judge", "arrested convicted"), ("psychic", "surprised didn't see it coming"),
    ("alarm clock company", "overslept late"), ("gym", "unfit"), ("pharmacist", "sick ill"),
]

SARCASM = """oh great
oh, great
just great
just perfect
oh perfect
oh, perfect
oh wonderful
oh, wonderful
wonderful job
great job
nice going
nice one
well done genius
thanks a lot
thanks a bunch
thanks for nothing
yeah right
yeah, right
sure you did
sure, because
as if
like that's going to happen
like that will happen
what a surprise
big surprise
shocker
who would have thought
who knew
oh joy
oh goody
how lovely
how wonderful
lucky me
my favourite
my favorite
exactly what i needed
just what i needed
that's just great
that's just perfect
that's just wonderful
i love it when
i just love it when
because that worked so well last time
good luck with that
tell me something i don't know
no kidding
you don't say
brilliant idea
genius idea
clever
oh brilliant""".split("\n")

HUMAN = """whisper whispered whispers whispering smile smiled smiles smiling laugh laughed laughs laughing weep wept weeps weeping
cry cried cries crying sing sang sings singing dance danced dances dancing speak spoke speaks speaking talk talked talks talking
think thought thinks thinking kiss kissed kisses kissing embrace embraced embraces embracing beckon beckoned beckons beckoning
shout shouted shouts shouting scream screamed screams screaming sigh sighed sighs sighing wink winked winks winking frown frowned
frowns frowning moan moaned moans complain complained complains mourn mourned mourns grieve grieved grieves dream dreamed dreamt
dreams yawn yawned yawns hug hugged hugs smirk smirked giggle giggled giggles chuckle chuckled chuckles nod nodded nods
wave waved greet greeted greets invite invited invites call called calls beg begged begs plead pleaded pleads promise promised
promises lie lied lies betray betrayed betrays forgive forgave forgives remember remembered remembers forget forgot forgets
hope hoped hopes worry worried worries mock mocked mocks tease teased teases scold scolded scolds tell told tells say said says
march marched marches stare stared stares wait waited waits watch watched watches listen listened listens refuse refused refuses
welcome welcomed welcomes""".split()

HOMOPHONE = [
    ("their", "there"), ("there", "they're"), ("their", "they're"), ("to", "too"), ("to", "two"), ("two", "too"), ("your", "you're"),
    ("its", "it's"), ("hear", "here"), ("write", "right"), ("right", "rite"), ("knight", "night"), ("knew", "new"), ("know", "no"),
    ("son", "sun"), ("whole", "hole"), ("wait", "weight"), ("be", "bee"), ("dear", "deer"), ("tale", "tail"), ("flour", "flower"),
    ("sole", "soul"), ("pane", "pain"), ("knead", "need"), ("bored", "board"), ("hour", "our"), ("meat", "meet"), ("peace", "piece"),
    ("plain", "plane"), ("sea", "see"), ("weak", "week"), ("wear", "where"), ("which", "witch"), ("whether", "weather"),
    ("brake", "break"), ("buy", "by"), ("bye", "by"), ("cell", "sell"), ("cent", "scent"), ("cent", "sent"), ("allowed", "aloud"),
    ("ate", "eight"), ("bare", "bear"), ("blew", "blue"), ("cereal", "serial"), ("die", "dye"), ("fair", "fare"), ("flew", "flu"),
    ("for", "four"), ("hair", "hare"), ("heal", "heel"), ("higher", "hire"), ("hymn", "him"), ("mail", "male"), ("made", "maid"),
    ("mind", "mined"), ("one", "won"), ("pair", "pear"), ("pause", "paws"), ("principal", "principle"), ("rain", "reign"),
    ("read", "red"), ("road", "rode"), ("role", "roll"), ("sail", "sale"), ("scene", "seen"), ("stair", "stare"), ("steal", "steel"),
    ("tide", "tied"), ("toe", "tow"), ("vain", "vein"), ("waist", "waste"), ("way", "weigh"), ("wood", "would"), ("tired", "tyred"),
    ("dough", "doe"), ("bread", "bred"), ("lessen", "lesson"), ("pole", "poll"), ("missed", "mist"), ("nose", "knows"),
    ("prey", "pray"), ("profit", "prophet"), ("so", "sew"), ("so", "sow"), ("some", "sum"), ("tea", "tee"), ("threw", "through"),
    ("berry", "bury"), ("idle", "idol"), ("muscle", "mussel"), ("naval", "navel"), ("serf", "surf"), ("cheap", "cheep"),
]

PUN = {
    "two-tired": "too tired; two tyres (a bicycle's)", "two tired": "too tired; two tyres (a bicycle's)", "lost interest": "stopped caring; lost the money a bank pays",
    "can't put it down": "can't stop reading; can't set it down (anti-gravity)", "kneaded the dough": "needed the money; worked bread dough",
    "need the dough": "need money; need bread dough", "a bit dull": "boring; not sharp (a pencil, a knife)", "pointless": "without purpose; without a point (a pencil)",
    "outstanding in his field": "excellent at his work; standing in a field", "outstanding in her field": "excellent at her work; standing in a field",
    "time flies like an arrow; fruit flies like a banana": "flies the verb and flies the insects; like the preposition and like the verb",
    "dying to get in": "eager to get in; dying (a cemetery)", "a bad case of the hiccups": "a bad bout of hiccups",
    "make a hole lot": "make a whole lot; dig a hole", "egg-cellent": "excellent; egg", "un-brie-lievable": "unbelievable; brie",
    "for heaven's steak": "for heaven's sake; steak", "lettuce": "let us; lettuce", "a-salt": "assault; salt", "sole mate": "soulmate; a sole (fish or shoe)",
    "wheely good": "really good; wheels", "shoe-in": "shoo-in; shoe", "cereal killer": "serial killer; cereal", "dam good": "damn good; a dam",
    "bear with me": "be patient; a bear", "pun-ishment": "punishment; pun", "hole-y": "holy; full of holes", "berry nice": "very nice; berry",
}

def main():
    rows = []
    for p, m in EUPHEMISM.items():
        rows.append(("euphemism", p, m))
    for w in ONOMATOPOEIA:
        rows.append(("onomatopoeia", w, ""))
    for p, m in HYPERBOLE.items():
        rows.append(("hyperbole", p, m))
    for p in OXYMORON_PHRASES:
        rows.append(("oxymoron", p, ""))
    for a, b in OPPOSED:
        rows.append(("opposed", a, b))
    for p, m in PARADOX.items():
        rows.append(("paradox", p, m))
    for p, m in METAPHOR.items():
        rows.append(("metaphor", p, m))
    for who, what in ROLE:
        rows.append(("role", who, what))
    for p in SARCASM:
        if p.strip():
            rows.append(("sarcasm", p.strip(), ""))
    for v in HUMAN:
        rows.append(("human", v, ""))
    for a, b in HOMOPHONE:
        rows.append(("homophone", a, b))
    for p, m in PUN.items():
        rows.append(("pun", p, m))
    seen = set()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# kind\tphrase\tmeaning (tools/build_figures.py)\n")
        for r in rows:
            if r not in seen:
                seen.add(r)
                f.write("\t".join(r) + "\n")
    print(f"{len(seen)} entries → {OUT}")
    if "--keep" not in sys.argv:
        subprocess.check_call([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"])


if __name__ == "__main__":
    main()
