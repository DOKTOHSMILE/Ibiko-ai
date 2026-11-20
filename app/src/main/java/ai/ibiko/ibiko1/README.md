# Ibiko 1

Ibiko 1 is Ibiko's own language brain. It runs inside any Android app, with no network and no other model needed. It:

- answers from its built-in knowledge: world facts, the elements, local facts, an English dictionary (Open English WordNet),
  and an encyclopedia: the opening of every Simple English Wikipedia article, with other names for each (CC BY-SA 4.0,
  `assets/knowledge/encyclopedia/`, built by `tools/build_encyclopedia.py`). "What is a volcano", "who was Nelson
  Mandela" and "tell me about Lagos" are answered offline, saying which article it read;
- answers other questions from those articles too (`EncyclopediaQA`): "When did Nigeria gain independence?", "How many
  people live in Lagos?". It finds what the question is about (the longest run of its words that is an article's
  title, a name before a common word), then the sentence that answers it (the question's words, and the kind of answer
  it wants: a date, a number in the right measure, a place, a name, a reason). When no sentence answers it surely it
  says nothing rather than something wrong;
- answers exactly from its fact tables (`FactsQA`): the facts in each article's information box (born, died, where,
  capital, population, area, founded, founder, author, director, leader, currency, languages and more), kept in
  `encyclopedia/facts/` and built by `tools/build_facts.py`. "When was Nelson Mandela born?", "How old is Wizkid?",
  "Who founded Microsoft?" and "What is the population of Lagos?" get one exact sentence;
  A name that means several things ("Titanic") also tries the articles titled after it ("Titanic (1997 movie)"),
  linked by `tools/facts_variants.py`;
- reads a text you paste (`Passage`): says what it is about and its main points, then answers questions about it from
  it ("Who started FarmLink?", "When did she move?", "Summarise it", "What are the key points?"), with "he"/"she" as
  its main person; a question after the text, on its last line, is answered at once. A question about anything else
  still goes to its knowledge;
- follows the conversation (`Context`): "Who was Nelson Mandela?" then "When was he born?" (he = Nelson Mandela),
  "What is the capital of Nigeria?" then "What about Ghana?", "When was the company founded?", "and his wife?", "tell
  me more" (what wasn't said yet, then the article's facts). What it put in is said in its steps;
- reads who does what to whom (`Roles`): a question's subject, action and object, and which one it asks for ("Who
  painted the Mona Lisa?" asks the subject of "paint"; "What did Thomas Edison invent?" the object), answered from the
  sentence of an article with the same action and object, active or passive ("painted in oil by Leonardo da Vinci");
- works out units, dates and percentages exactly (`Everyday`): "convert 5 km to miles", "how many days until
  Christmas", "what day of the week was 1 October 1960", "15% of 200";
- reads a sentence as nested constituents (`formulas/Syntax`, `SyntaxTalk`): phrase structure rules (S → NP VP,
  NP → (Det) (AdjP) N (PP), VP → V (NP) (PP) (AdvP), PP → P NP, AdjP → (Deg) Adj), recursive, parsed bottom up with a
  chart (CYK) so every reading is found. "Parse: the engineer deployed the node on the server" draws its tree and
  brackets; "is 'on the server' a constituent in …" runs the substitution, movement and fragment tests; "why is 'I saw
  the user with the telescope' ambiguous?" shows both trees and where the PP attaches in each;
- knows itself (`SelfModel`): what it is, what it can do right now (from what is loaded and plugged in), how it works,
  its state (online, a model on the phone, what this chat is about, what was just asked), and an honest view of its
  feelings and opinions; any question put to it is answered as itself, never from an article on one of its words;
- knows the person (`Person`): "I'm a man", "my name is Emeka", "I live in Lagos", "I like football", "I'm tired" are
  understood, kept and said back; "who am I?" and "what do you know about me?" are answered from them. "I'm a man,
  what are you?" is both: the person's part is taken in, then the question answered;
- reads every way a thing is said as one (`Paraphrase`): slang ("wat r u"), contractions, greetings and fillers, its own
  name, polite wrappers ("can you tell me…") and synonyms ("chat about", "discuss") are brought to one plain form before
  the rules read it. `ManyWaysTest` says each message over 250,000 ways and checks every one reaches the same answer;
- answers questions put to itself as itself (`SelfTalk`): "Where are you from?", "Can you cook?", "Are you human?";
- writes short letters, notes, emails and messages offline (`Letters`), in the tone that suits who they're to, with
  blanks in [brackets] for what only the writer knows;
- remembers what it is taught, and learns from "right" and "wrong";
- researches the web only when the app gives it a way to (`Plug.web`);
- writes a function line (`§XX …`) when a message asks the app to do something;
- reasons over what it knows (`Reasoner`). A question one fact can't answer is broken into steps, which run on its facts and are then combined:
  - compare: "Which is bigger, Nigeria or Ghana?";
  - rank: "the most populous country in Africa";
  - count and list: "How many countries use the euro?";
  - chain: "the currency of the country whose capital is Accra";
  - compute: population density, "combined", a continent's total.

  Every answer says the values it rests on, a false premise is corrected rather than explained, and nothing is invented. The rules are in `docs/FORMULAS.md`.

Inside Ibiko it lives in the app's own sources (this folder) and its knowledge in `app/src/main/assets/knowledge/`, so every builder (CodeAssist on the device included) builds it with the app. `sh tools/export_ibiko1.sh [out]` puts it all together as a standalone library folder (default `build/ibiko1`):

| Path | What it holds |
|---|---|
| `src/main/java/ai/ibiko/ibiko1/` (this folder) | The model: `Ibiko1` (the brain), `Ibiko1Model` (the entry point), `formulas/` (grammar, commands, truth, foresight, …), `comprehend/` (dictionary and concepts), `learner/` |
| `src/main/assets/knowledge/` | Its built-in knowledge and their licences, all in the Ibiko v1 codes: the encyclopedia and its fact tables in IBK1, the tables and packs (`*.tsv.ibk`, `*.json.ibk`) in IBK1T (`TextCode`, made by `tools/encode_text.py`), which decode to the very same bytes |
| `src/main/AndroidManifest.xml` | Its night-learning job |
| `build.gradle` | An Android library (minSdk 34, Java 17, no dependencies) |

## Use it in an app

1. Run `sh tools/export_ibiko1.sh`, then copy `build/ibiko1` next to your app module.
2. In `settings.gradle`, add `include ":ibiko1"`.
3. In the app's `build.gradle`, add `implementation project(':ibiko1')`.

```java
Ibiko1Model brain = new Ibiko1Model(context);   // its memory goes to context.getFilesDir()/brain
Ibiko1Model.Reply r = brain.reply("What is the capital of Ghana?", 15000);
r.text;    // "The capital of Ghana is Accra."
r.steps;   // how it worked it out
r.code;    // a function line for your app, when the message asked for an action
brain.feedback("What is the capital of Ghana?", r.text, true);   // it learns
brain.close();
```

Its id is `ibiko-1-0.1` and its address is `ibiko://ibiko1/v1/chat` (`Ibiko1Model.ID` and `Ibiko1Model.ADDRESS`), the same kind of name other models have.

### Optional plugs (`Plug`)

Set these once, at app start:

- `Plug.asset`: where its files are. Set by `Ibiko1Model` from the context's assets.
- `Plug.web`: the web, for research.
- `Plug.workers`: a board of who is working on what.
- `Plug.interests`: what the user is into, for night learning.
- `Plug.icon`: its notification icon.

### On a chat screen

To show working steps and right or wrong buttons, implement `Ibiko1.Host` and create a `Ibiko1` directly. That is how the Ibiko app uses it (`main/Ibiko1Screen` in the app).

## Tests

Its tests are in `app/src/test/java/ai/ibiko/ibiko1/`, including `Ibiko1ModelTest` for the entry point.
