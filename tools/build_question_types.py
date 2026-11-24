#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/language/question_types.tsv.ibk: the kinds of question, by how they start.

"Why", "what is", "what was", "what happened", "what is happening", "what am I", "can I", "can you"… each opens a
different kind of question: a reason, what a thing is, something past, an event, what's going on now, something about
the person, leave, a request. Ibiko v1 reads a question's kind from its opening words (so "why?" is curiosity about a
reason, not a word to look up), says it in its thinking, and when only the opening is asked ("What was?", "Why?") it
answers from the kind and the conversation's subject.

One line a kind of opening, the most particular first (they are tried in order):
  id, opening (a regular expression, matched at the start, any case), type, what the person wants (in plain words),
  when it is about (past, present, future, any), what to say to the opening alone ({topic} is the subject we're on).

Coded like the rest of the knowledge (tools/encode_text.py), read back by ai.ibiko.ibiko1.TextCode. Add a line to
teach Ibiko a new kind of question; nothing else changes.

Usage: tools/build_question_types.py
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "language", "question_types.tsv")

TYPES = {"REASON", "CAUSE", "CONSEQUENCE", "HYPOTHETICAL", "MEANING", "DEFINITION", "FACT", "PAST", "PAST_EVENT", "CURRENT_EVENT",
         "FUTURE_EVENT", "FUTURE", "SELF", "IBIKO", "PERMISSION", "REQUEST", "ADVICE", "OPINION", "IDENTITY", "OWNER", "CHOICE",
         "PLACE", "TIME", "COUNT", "AMOUNT", "DURATION", "AGE", "MEASURE", "PROCESS", "MECHANISM", "MANNER", "WELLBEING",
         "COMPARE", "YES_NO", "TELL"}

# id, opening, type, wants, when, alone
KINDS = [
    # why: curiosity about a reason
    ("why_did", r"why did|why didn't|why has|why had|why was|why were", "REASON", "the reason something happened", "past",
     "Why did what happen? Tell me what you're curious about{about} and I'll look for the reason."),
    ("why_does", r"why does|why do|why is|why are|why's|why isn't|why aren't|why doesn't|why don't", "REASON",
     "the reason something is so", "present", "Why is what so? Tell me what you're curious about{about} and I'll look for the reason."),
    ("why_will", r"why will|why would|why should", "REASON", "the reason something will or should be so", "future",
     "Why would what be so? Say the rest and I'll reason it out."),
    ("how_come", r"how come", "REASON", "the reason something is so", "any", "How come what? Tell me what surprised you{about}."),
    ("why", r"why", "REASON", "the reason something is so", "any",
     "Why what? You're curious about a reason{about}: tell me what you'd like explained."),
    # what happened, is happening, will happen
    ("what_happened", r"what happened|what has happened|what had happened|what went on|what took place", "PAST_EVENT",
     "an event: what took place", "past", "What happened where, or to whom?{topic_q} Tell me and I'll tell you what I know."),
    ("what_is_happening", r"what is happening|what's happening|what is going on|what's going on|what is up with|what's up with",
     "CURRENT_EVENT", "what's going on now", "present", "What's happening where?{topic_q} I can tell you what I've read; for today's news I need to be online."),
    ("what_will_happen", r"what will happen|what's going to happen|what is going to happen|what would happen", "FUTURE_EVENT",
     "what is going to happen", "future", "What will happen when, or to what? I can reason about what usually follows something, but nobody can know the future for sure."),
    ("what_happens_if", r"what happens if|what happens when|what if", "CONSEQUENCE", "what follows from something",
     "any", "What if what? Tell me the situation and I'll say what usually follows."),
    ("what_causes", r"what causes|what caused|what leads to|what led to|what makes", "CAUSE", "the cause of something", "any",
     "What causes what? Tell me the thing and I'll look for its cause."),
    # the person asking, and Ibiko
    ("what_am_i", r"what am i|who am i|where am i|what do i|what did i|what was i|what have i|how am i", "SELF",
     "something about you, the person asking", "any",
     "About you? Ask me what you'd like to know: what you told me, what you asked, or something about yourself."),
    ("about_ibiko", r"what are you|who are you|what can you|what do you|where are you|how old are you|are you", "IBIKO",
     "something about me, Ibiko", "any", "About me? I'm Ibiko. Ask me what I can do, how I work, or anything else."),
    # leave, requests, advice, opinions
    ("can_i", r"can i|may i|could i|am i allowed to", "PERMISSION", "leave to do something, or whether it's possible", "any",
     "Can you what? Go ahead: say the rest."),
    ("can_you", r"can you|could you|would you|will you|please", "REQUEST", "something done for you", "any",
     "Can I what? Say what you'd like me to do."),
    ("should", r"should i|what should i|should we|do you think i should", "ADVICE", "advice: what would be best", "any",
     "Should you what? Tell me the choice and I'll help you think it through."),
    ("opinion", r"what do you think|how do you feel|what's your opinion|what is your opinion|do you like", "OPINION",
     "my view on something", "any", "What do I think about what? Name it and I'll tell you what I know and think."),
    # meanings and things
    ("meaning", r"what does .{1,40} mean|what is the meaning of|what's the meaning of|meaning of", "MEANING", "what a word means",
     "any", "What does what mean? Tell me the word."),
    ("difference", r"what is the difference between|what's the difference between|difference between", "COMPARE",
     "how two things differ", "any", "The difference between what and what? Name the two."),
    ("what_was", r"what was|what were|what used to be", "PAST", "something in the past", "past",
     "‘What was…’ asks about something in the past: what was what?{topic_q}"),
    ("what_is_a", r"what is an?|what's an?|what are", "DEFINITION", "what a thing is", "present",
     "What is what? Name the thing and I'll tell you what it is."),
    ("what_is_the", r"what is the|what's the", "FACT", "a fact about something", "present",
     "What is the what? Say the rest{about}."),
    ("what_is", r"what is|what's|whats|what", "DEFINITION", "what something is", "present",
     "What would you like to know?{topic_q}"),
    # people, places, times, choices
    ("who_was", r"who was|who were|who did|who used to", "IDENTITY", "a person in the past", "past", "Who was what? Say the rest."),
    ("who", r"who is|who's|who are|who", "IDENTITY", "a person", "any", "Who? Tell me who or what you're asking about."),
    ("whose", r"whose", "OWNER", "whose it is", "any", "Whose what? Say the rest."),
    ("which", r"which", "CHOICE", "one of some options", "any", "Which what? Tell me the options."),
    ("where_was", r"where was|where were|where did", "PLACE", "where something was", "past", "Where was what? Say the rest."),
    ("where", r"where", "PLACE", "a place", "any", "Where is what? Tell me the place or thing you're looking for."),
    ("when_was", r"when was|when were|when did", "TIME", "when something happened", "past",
     "When did what happen? Tell me the event{about}."),
    ("when_will", r"when will|when is|when's|when does", "TIME", "when something is or will be", "future",
     "When is what? Tell me the event."),
    ("when", r"when", "TIME", "a date or time", "any", "When what? Say the rest."),
    # how
    ("how_many", r"how many", "COUNT", "a number of things", "any", "How many what? Say the rest."),
    ("how_much", r"how much", "AMOUNT", "an amount or a price", "any", "How much what? Say the rest."),
    ("how_long", r"how long", "DURATION", "a length of time or a length", "any", "How long is what? Say the rest."),
    ("how_old", r"how old", "AGE", "an age", "any", "How old is who? Say the rest."),
    ("how_big", r"how (?:big|far|tall|high|deep|wide|heavy|fast|large|small|hot|cold)", "MEASURE", "a measure", "any",
     "How big (or far, or tall) is what? Say the rest."),
    ("how_are_you", r"how are you|how's it going|how is it going|how do you do", "WELLBEING", "how I am", "present",
     "I'm well, thanks for asking. How are you?"),
    ("how_to", r"how do i|how can i|how to|how should i|how would i", "PROCESS", "the steps to do something", "any",
     "How do you what? Tell me what you want to do and I'll give you the steps."),
    ("how_does", r"how does|how do|how is|how are|how was|how were", "MECHANISM", "how something works or came about", "any",
     "How does what work? Name the thing."),
    ("how", r"how", "MANNER", "the way something is done", "any", "How what? Say the rest."),
    # yes or no
    ("will", r"will|is it going to|are we going to", "FUTURE", "whether something will happen", "future",
     "Will what happen? Say the rest. (If it's about the future, I'll tell you what's likely, not what's sure.)"),
    ("yes_no", r"is|are|was|were|do|does|did|has|have|had|can|could|should|would|isn't|aren't|wasn't|doesn't|don't|didn't",
     "YES_NO", "a yes or a no", "any", "Is what so? Say the rest and I'll check."),
    ("tell", r"tell me|explain|describe|define|teach me|show me", "TELL", "an explanation", "any",
     "Tell you about what? Name it."),
]


def main():
    lines = ["# Kinds of question by how they start (tools/build_question_types.py): id, opening, type, wants, when, alone. "
             "Tried in order: the most particular first."]
    seen = set()
    for k in KINDS:
        assert len(k) == 6, k[0]
        assert k[0] not in seen, k[0]
        seen.add(k[0])
        assert k[2] in TYPES, (k[0], k[2])
        assert k[4] in ("past", "present", "future", "any"), k[0]
        re.compile("(?i)^(?:" + k[1] + ")\\b")
        assert all("\t" not in c for c in k)
        lines.append("\t".join(k))
    # the order (the most particular first) is checked on real questions by QuestionTypesTest
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d kinds of question" % len(KINDS))


if __name__ == "__main__":
    main()
