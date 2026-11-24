#!/usr/bin/env python3
"""Builds app/src/main/assets/voice/recording_script.tsv.ibk: the sentences the pilot reads to train a voice of their own.

A voice is trained from recordings of one person reading sentences aloud (Settings › Voice › Record my voice). The
sentences must cover the sounds of English, in many combinations, with the tunes of statements, questions and
exclamations. They come from two places:

  * Ibiko's own encyclopedia (Simple English Wikipedia, CC BY-SA 4.0): plain sentences of 6 to 18 words, no numbers,
    abbreviations, brackets or foreign letters (hard to read aloud the same way twice), chosen greedily so that each one
    adds the most letter patterns (letter triples and word starts and ends) not yet covered;
  * lines written here for an assistant's own talk: greetings, questions, answers, exclamations, Nigerian names and
    places, so the voice sounds like Ibiko in conversation, not only like an encyclopedia.

One line a sentence:  id <TAB> sentence   (ids 0001…). Read in the app by ai.ibiko.voice.MyVoice.

Usage: tools/build_voice_script.py [count]   (default 750, about 45 minutes of speech)
"""
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from encode_encyclopedia import decode  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ENC = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "encyclopedia")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "voice", "recording_script.tsv")

# an assistant's own talk: written here
OWN = """Hello! How can I help you today?
Good morning. Did you sleep well?
Good evening. What would you like to talk about?
I'm not sure about that, but I can look it up for you.
That's a great question. Let me think about it for a moment.
Here's what I found, and I'll tell you how sure I am.
Would you like me to read it out loud?
Sorry, I didn't catch that. Could you say it again?
Thank you! I'm glad that helped.
You're welcome. Is there anything else?
Of course. I'll remind you tomorrow morning.
Are you sure you want to delete all of them?
Wow, that's wonderful news!
Oh no, I'm sorry to hear that.
Please wait a moment while I check.
Let's take it one step at a time.
First, open the settings. Then tap on voice.
Is it going to rain in Lagos this afternoon?
How far? I hope your day is going well.
No wahala, we'll sort it out together.
Chinedu runs a busy workshop in Onitsha.
Ngozi and Emeka travelled from Enugu to Abuja.
Amina sells tomatoes, yams and garri at the market in Jos.
Tunde said the generator would come on at seven.
Kemi baked fresh bread for the whole street.
Ifeoma asked whether the bus to Ibadan had left.
Musa is learning to repair phones and laptops.
Zainab paid for the four new screens herself.
Bisi, Obi and Femi waited by the river in Kano.
Can you believe how quickly the children grew?
Why does the moon change its shape every night?
Which road is faster at this time of day?
Who wrote the story you were reading yesterday?
When will the shop open again after the holiday?
Where did you leave the keys to the car?
What a beautiful sunrise over the hills!
Really? I never knew that before.
Hmm, let me check that once more.
Yes, that's right. You remembered it well.
No, that isn't quite right. Here's why.
I learned something new today, and I'd like to tell you about it.
Your reminder is set for half past nine.
The battery is getting low, so please charge your phone.
I've saved the file in your downloads folder.
Shall I send the message now, or later?
Speak slowly and clearly, and I'll follow every word.
The quick brown fox jumps over the lazy dog.
She sells sea shells on the sea shore, surely.
Thirty thrifty thinkers thought through three themes.
Judge the vague voice of the cheerful choir.
"""

BAD = re.compile(r"[0-9()\[\]{}/\\%&@#$*_=+<>|~^\"“”;]|[^\x00-\x7f]|\b(?:[A-Z]{2,}|[A-Z]\.|e\.g|i\.e|etc|vs|St|Dr|Mr|Mrs|No)\b|\.\.")


COMMON = {}


def entries():
    """Every article body in the encyclopedia, decoded; COMMON gets each word's rank (the vocabulary is most-used first)."""
    with open(os.path.join(ENC, "vocab.ibk"), "rb") as f:
        b = f.read()
    assert b[:5] == b"IBK1V"
    p = 5

    def varint():
        nonlocal p
        n = shift = 0
        while True:
            x = b[p]
            p += 1
            n |= (x & 0x7F) << shift
            shift += 7
            if not x & 0x80:
                return n

    vocab = []
    for _ in range(varint()):
        ln = varint()
        vocab.append(b[p:p + ln].decode("utf-8"))
        p += ln
    for i, w in enumerate(vocab):
        COMMON.setdefault(w.lower(), i)
    for name in sorted(os.listdir(ENC)):
        if not re.match(r"\d{3}\.ibk$", name):
            continue
        with open(os.path.join(ENC, name), "rb") as f:
            b = f.read()
        if b[:4] != b"IBK1":
            continue
        p = 4
        for _ in range(varint()):
            ln = varint()
            p += ln  # the key
            flags = b[p]
            p += 1
            rule, redirect = flags >> 1, flags & 1
            if rule == 0:
                ln = varint()
                p += ln  # its own title
            ln = varint()
            payload = b[p:p + ln]
            p += ln
            if not redirect:
                yield decode(payload, vocab)


def candidates():
    seen = set()
    for body in entries():
        for s in re.split(r"(?<=[.!?])\s+", body):
            s = s.strip()
            words = s.split()
            if not (8 <= len(words) <= 18) or not s.endswith(".") or BAD.search(s) or s.lower() in seen:
                continue
            if sum(1 for w in words[1:] if w[0].isupper()) > 1:
                continue  # names: hard to say the same way twice, and they teach the voice little
            plain = [re.sub(r"[^a-z']", "", w.lower()) for w in words]
            if any(not w or COMMON.get(w, 10 ** 9) > 12000 for w in plain):
                continue  # only common words: every one read the way it is usually said
            seen.add(s.lower())
            yield s


def units(s):
    t = " " + re.sub(r"[^a-z ]", "", s.lower()) + " "
    u = {t[i:i + 3] for i in range(len(t) - 2)}
    for w in t.split():
        u.add("^" + w[:2])
        u.add(w[-2:] + "$")
    return u


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 750
    own = [l.strip() for l in OWN.strip().splitlines() if l.strip()]
    covered = set()
    for s in own:
        covered |= units(s)
    pool = list(candidates())
    pool.sort(key=len)
    chosen = []
    # greedy: the sentence that adds the most not yet covered, per word (short and rich first)
    scored = [(s, units(s)) for s in pool[:60000]]
    while len(chosen) + len(own) < count and scored:
        best_i, best = -1, 0.0
        for i, (s, u) in enumerate(scored):
            gain = len(u - covered) / (len(s.split()) ** 0.25)
            if gain > best:
                best_i, best = i, gain
        if best_i < 0:
            break
        s, u = scored.pop(best_i)
        chosen.append(s)
        covered |= u
    lines = own + chosen
    # the conversational lines spread through the script, not all at the start
    mixed, step = [], max(1, len(chosen) // max(1, len(own)))
    oi = 0
    for i, s in enumerate(chosen):
        if i % step == 0 and oi < len(own):
            mixed.append(own[oi])
            oi += 1
        mixed.append(s)
    mixed += own[oi:]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# The sentences to read for a voice of your own (tools/build_voice_script.py): id, sentence\n")
        for i, s in enumerate(mixed[:count]):
            f.write("%04d\t%s\n" % (i + 1, s))
    words = sum(len(s.split()) for s in mixed[:count])
    print("%d sentences, %d words (about %d minutes read aloud), %d letter patterns covered"
          % (min(count, len(mixed)), words, round(words / 150), len(covered)))
    subprocess.check_call([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"])


if __name__ == "__main__":
    main()
