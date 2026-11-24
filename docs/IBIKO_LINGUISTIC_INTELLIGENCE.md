# Ibiko Linguistic Intelligence

How Ibiko understands English: not only each word, but what kind of word it is in its sentence, how the sentence is
built, which expressions are figurative, how words relate, and what a message means in its conversation. "It's raining
cats and dogs" is heavy rain, not animals. "Can you open the door?" is a request, not a question about ability.
"That's just great!" is glad or sarcastic depending on what happened before.

The same engine serves every brain. Ibiko v1 uses it to answer. The on-device model and the API models get its reading
as a note with the request (`Ibiko1.premeditate`), so all of them take the meaning rather than the literal words.

All of it is pure Java in `app/src/main/java/ai/ibiko/ibiko1/language/` and `…/ibiko1/words/`, with rules anyone can read.
It needs no model.

---

## 1. Three levels of understanding

| Level | Capability | Where |
|---|---|---|
| **1. Recognition** | Each word and its grammatical category, in its sentence | `formulas/PartsOfSpeech`, `language/WordRecord`, `words/SenseTags` |
| **2. Interpretation** | The sentence's structure, function, tense, voice; its figures of speech; how words relate | `language/SentenceForm`, `language/Figures`, `language/Relations`, `formulas/Syntax` |
| **3. Comprehension** | What it does and means in context: the act, the intent, the tone, what is implied, what is still ambiguous | `language/Pragmatics`, `Context`, `comprehend/ConversationReading` |

`language/Language` reads a message at all three levels (`read(sentence, previous)`). Its output has three uses:
- `brief()`: the note every brain gets with the request.
- `steps()`: the thinking lines shown in the chat.
- `reply()`: answers to questions about language.

## 2. The architecture

```
IBIKO LANGUAGE INTELLIGENCE ENGINE                       code
│
├── 1. LANGUAGE FOUNDATION
│   ├── Phonetics / Phonology    sounds, sound-alike keys     words/Phonetic, words/Morph (allomorphs: -s /s/ /z/ /ɪz/)
│   ├── Morphology               roots, affixes, word trees   words/Morphology, words/Morph, language/WordRecord
│   └── Lexicology               the dictionary, both ways    words/Meanings, words/MeaningIndex (Open English WordNet)
│
├── 2. WORD CLASSIFICATION       parts of speech in context    formulas/PartsOfSpeech, language/WordRecord
│
├── 3. EXPRESSION INTELLIGENCE   idioms … puns, separately     language/Figures, formulas/Expressions
│
├── 4. SEMANTIC INTELLIGENCE     word relations                language/Relations (+ ConceptNet: comprehend/Concepts)
│
├── 5. GRAMMAR INTELLIGENCE      structure, function, tense    language/SentenceForm, formulas/Syntax, words/GrammarCheck
│
├── 6. CONTEXTUAL INTELLIGENCE   act, intent, tone, ambiguity  language/Pragmatics, words/SenseTags (word sense), Context
│
└── 7. LANGUAGE LEARNING         see §9                        Lexicon, PartsOfSpeech (learned tags), cognition/LearningRecords
```

The other disciplines in the brief map onto these:
- **Discourse analysis**: `Context` and `comprehend/ConversationReading` (references across turns).
- **Stylistics**: tone in `Pragmatics`, plus `formulas/Reshape`.
- **Etymology**: Wiktionary notes in the sayings table; there is no full etymology table yet (§10).
- **Semiotics**: emoji and symbols are read by `formulas/Readings`.

## 3. Word classification (parts of speech)

Ibiko classifies a word **in its sentence**; it does not memorise one class per word.

| Category | Kinds | How it is recognised |
|---|---|---|
| Noun | proper, common, concrete, abstract, collective, countable, uncountable, compound, material, possessive | after a determiner/adjective; -tion -ness -ment -ity -ship -ism; capitalised inside a sentence → proper; `'s` → possessive; WordNet's broader terms say concrete or abstract (`NounKinds`) |
| Pronoun | personal, possessive, reflexive, demonstrative, relative, interrogative, indefinite, reciprocal | closed list (`PartsOfSpeech.PRONOUNS`); a relative pronoun after a noun opens a relative clause (`SentenceForm`) |
| Verb | action, stative, auxiliary, modal, transitive, intransitive, linking, regular, irregular, phrasal, causative | auxiliaries and modals: closed list; irregular forms: `Morph` (go, went, gone); regular: -ed; after "to" or a modal; WordNet says "verb" |
| Adjective | descriptive, quantitative, demonstrative, possessive, interrogative, comparative, superlative, compound, participial | -ous -ful -ive -able -al -ic -less; -er/-est (`Morph.comparative/superlative`); an -ing/-ed word before a noun → participial |
| Adverb | manner, time, place, frequency, degree, certainty, negation, focus, interrogative | -ly; closed lists (always, very, not, only, here, yesterday…) |
| Preposition | location, time, direction, cause, means, relationship | closed list |
| Conjunction | coordinating, subordinating, correlative, compound | coordinating: and, but, or, so, yet, nor; subordinating: because, although, if, when…; correlative: either…or, neither…nor |
| Interjection | wow, ouch, alas, hey, hurray, oh, hmm | closed list |

### The word record (JSON)

`WordRecord.of(word, sentence, source).json()`:

```json
{
  "word": "running",
  "lemma": "run",
  "possible_categories": ["verb", "noun", "adjective"],
  "morphology": {"suffix": "ing", "suffix_means": "present participle or gerund", "root": "run"},
  "meanings": ["move fast on foot", "the act of running"],
  "grammar_features": {
    "verb_form": "present_participle_or_gerund",
    "aspect": "progressive_when_used_as_part_of_a_verb_phrase"
  },
  "in_sentence": {"sentence": "Running is healthy.", "role": "gerund", "acts_as": "noun"},
  "context_required": true
}
```

| Sentence | "running" is | Rule |
|---|---|---|
| I am running. | verb (progressive) | a form of *be* before it |
| Running is healthy. | gerund, acting as a noun | it is the subject, or follows a preposition |
| Running water is clean. | participle, acting as an adjective | a noun follows, and it opens the phrase or follows a determiner |

Morphology undoes spelling changes when it takes an affix off: happi-ness → happy + -ness; run-n-ing → run; us-able →
use + -able. `words/Morphology` also builds the full binary tree, and finds the two trees of an ambiguous word
("unlockable").

## 4. Expression intelligence (figures of speech)

Each figure has **its own classifier** (`language/Figures`), because they are not interchangeable. Each figure found
gives the words it is in, what is meant, why it was read so, and how sure the classifier is.

| Figure | Example | Classifier |
|---|---|---|
| Idiom | break the ice → start a conversation | the sayings table (`knowledge/language/expressions.tsv`, Wiktionary + Nigerian and Pidgin sayings), when the saying is a phrase |
| Proverb | actions speak louder than words | a saying that is a whole statement of general truth or advice (a subject and a simple-aspect verb, or "don't…/never…/a…/the early…") |
| Metaphor | my boss is a dragon; time is money | "X is a N" where X and N are different kinds (person ≠ animal; an idea ≠ a substance), or a conventional one from the table |
| Simile | brave as a lion; runs like the wind | "as ADJ as a N" / "VERB like a N" where N is an animal, a thing or nature; a person compared with a person ("as tall as his brother") is a plain comparison, and "I like a challenge" is liking |
| Personification | the wind whispered | a thing, nature or an idea doing what only people do (a list of human-only verbs) |
| Hyperbole | I've told you a million times | table phrases; "a million/thousand … times"; "the best … ever"; "bored to death" |
| Irony | a fire station burns down | a role suffers what it exists to prevent (the role table: firefighter→fire, dentist→teeth, lifeguard→drowning…) |
| Sarcasm | "Oh great" after the car broke down | a praising phrase said about something bad, in this message or the one before. With nothing bad around, "Oh great!" is just glad. A few phrases are always sarcastic ("yeah right", "thanks for nothing") |
| Oxymoron | deafening silence | table phrases; two adjacent words whose meanings pull apart (the opposed pairs, or WordNet antonyms) |
| Euphemism | passed away → died | table, with what it softens |
| Alliteration | Peter Piper picked a peck of pickled peppers | three or more content words with the same first consonant *sound* (ph = f, kn = n, soft c = s) |
| Onomatopoeia | bang, buzz, splash | word list |
| Paradox | less is more | table; "X is Y" where X and Y are opposites; "the more…, the less…" |
| Pun | it's two-tired | table (two meanings or two sounds) |

The tables are in `knowledge/language/figures.tsv.ibk`, built by `tools/build_figures.py` and stored in the .ibk text code.
They hold euphemisms, onomatopoeia, hyperbole phrases, oxymorons, opposed pairs, paradoxes, conventional metaphors, irony
roles, sarcasm markers, human-only verbs, homophones and puns, 815 entries in all.

**Sarcasm vs irony vs metaphor vs idiom:**
- Sarcasm needs a bad situation being praised.
- Irony needs a situation that turns against its own purpose.
- A metaphor needs two different kinds of thing equated.
- An idiom needs a fixed phrase.

The tests check that each is *not* found where it doesn't belong: no sarcasm in "Oh great!" after good news, no simile
in "I like a challenge", and no metaphor in "the lion is an animal".

## 5. Semantic intelligence (word relations)

`language/Relations`:

| Relation | Example | Source |
|---|---|---|
| Synonyms | happy / joyful | WordNet sense lists |
| Antonyms | hot / cold | WordNet |
| Hypernyms | dog → canine | WordNet broader term |
| Hyponyms | animal → dog, cat | ConceptNet IsA, read backwards |
| Meronyms | car → wheel, engine | ConceptNet HasPart, HasA |
| Holonyms | wheel → car | ConceptNet PartOf |
| Homonyms | bank (river slope / financial institution) | two senses of one part of speech that share no words and no broader term |
| Polysemy | head (body part / leader) | several senses that do share words |
| Homophones | their / there / they're | the table |
| Homographs | lead (/liːd/ guide, /lɛd/ metal) | list with pronunciations |
| Collocations | make a decision, heavy rain | list |
| Contronyms | sanction (approve / penalise) | list |

`Relations.between(a, b)` names how two words are related ("wheel" is a meronym of "car").

## 6. Grammar intelligence (sentence form)

`language/SentenceForm.read(sentence)`:

**Clauses.** A clause has its own verb. A sentence is split at these points:
- a semicolon
- a coordinator followed by a *new subject* ("…, but it was raining")
- a subordinator: because, although, if, when, while, since, unless…
- "that" after a saying or thinking verb
- who/which/whom/whose after a noun

A part with no verb joins the one before it ("bread and butter", "because of the rain"). "and" with no new subject joins
two predicates of one clause ("I came and saw").

| Structure | Rule |
|---|---|
| Simple | 1 independent clause, 0 dependent |
| Compound | 2 or more independent, 0 dependent |
| Complex | 1 independent, 1 or more dependent |
| Compound-complex | 2 or more independent, 1 or more dependent |

| Function | Rule |
|---|---|
| Interrogative | ends "?", or an auxiliary comes before its subject; kind: yes/no, wh-, tag ("isn't it?"), choice ("… or …?") |
| Imperative | a bare verb first (after please/kindly/just), "don't …", "let's …" |
| Exclamatory | "What a …!", "How … you are!" |
| Declarative | everything else |

**Tense and aspect: the twelve.** The verb chain of the main clause is its auxiliaries, then the main verb:
- will/shall (or be going to) → future; a first word in the past (was, had, did, -ed) → past; otherwise present
- have + participle → perfect; be + -ing → continuous; both → perfect continuous
- be + participle → passive voice; another modal (can, could, should…) → modal

| | simple | continuous | perfect | perfect continuous |
|---|---|---|---|---|
| present | she writes | she is writing | she has written | she has been writing |
| past | she wrote | she was writing | she had written | she had been writing |
| future | she will write | she will be writing | she will have written | she will have been writing |

Contractions are opened first: won't → will not, she's gone → she *has* gone, she's going → she *is* going, I'd been →
I *had* been, I'd go → I *would* go.

**Conditionals** (if/unless clause + main clause):

| Type | Shape | Example |
|---|---|---|
| zero | present + present | If you heat ice, it melts. |
| first | present + will / imperative / can, may | If it rains, we will stay home. |
| second | past (or "were") + would / could / might | If I were you, I would apologise. |
| third | past perfect + would have | If she had studied, she would have passed. |
| mixed | anything else | |

The sentence form also reports:
- **Negation**: not, n't, never, no, nobody, nothing, none, neither, nowhere.
- **Speech**: direct when there are quotation marks and a saying verb; indirect when a saying verb is followed by that / if / whether / a wh-word, or by "told X to".

Constituents, syntax trees and attachment ambiguity are in `formulas/Syntax`; agreement and grammaticality are in
`words/GrammarCheck`.

## 7. Contextual intelligence (pragmatics)

`language/Pragmatics.read(sentence, previous, figures)`:

| | |
|---|---|
| **Act** | request, question, statement, suggestion, offer, promise, advice, greeting, thanks, apology, complaint, permission, exclamation, hint |
| **Indirect requests** | "can/could/would you (please) V …" with a doing verb is a request ("please open the door"); with "swim" or "speak French" it asks about ability. Also: "would you mind V-ing"; "I was wondering if you could…"; "do you know what time it is?" asks the time; "why don't we / how about / let's" is a suggestion; "you might want to" is advice; "is anyone sitting here?" asks to sit |
| **Implied meaning** | "it's cold in here" → close a window or warm the room; "I'm hungry" → would like food; "it's late" → time to go; "NEPA took light" → the power is out |
| **Tone** | polite, urgent, angry (including words shouted in capitals), uncertain, sarcastic |
| **Feeling** | joy, sadness, anger, fear, worry, surprise, frustration, gratitude. Praise said sarcastically is not joy |
| **Ambiguity** | "I saw her duck": a word after "her" that is both noun and verb, after a seeing verb. "I saw the man with the telescope": a prepositional phrase that can attach to the verb or the noun (`formulas/Syntax`) |
| **Word sense** | `words/SenseTags` picks a sense by subject: "root" in botany, computing or maths |
| **Reference** | `Context` resolves he/she/it/there across turns |

## 8. One pipeline for every brain

```
message
   │
   ├─ Ibiko v1 answers ──► Language.asks(message)? → Language.reply(…)   ("what tense is …", "is that sarcastic?")
   │                       Expressions.explain(…)                         ("what does 'break a leg' mean?")
   │                       WordTalk / SyntaxTalk                          (meanings, synonyms, parse trees)
   │
   └─ a model answers ───► Ibiko1.premeditate(request)
                              … + Cognition.plan + Language.brief(request) …
                              "Ibiko's reading of the language (take the meaning, not the literal words):
                               - “it's raining cats and dogs” is an idiom: it means it's raining very heavily …
                               - It is a request: please open the door (asked as a question, meant as a request)."
```

The brief is empty when a message is plain and literal, so ordinary questions cost nothing extra. It is built on the
model's worker thread, never on the tap (see the 0.99.167 freeze fix).

### Questions Ibiko answers about language

| Ask | Example |
|---|---|
| figures | What figure of speech is "Time is money"? · Is "the wind whispered" personification? |
| sarcasm | Is that sarcastic? (about the message before) |
| tense, voice | What tense is "She had been writing"? · Is "The letter was written" active or passive? |
| sentence kind | What type of sentence is "…"? · Analyse "…" (all three levels) |
| a word | What part of speech is "running" in "Running is healthy"? · Classify the word "unhappiness" |
| relations | How are "wheel" and "car" related? · Homophones of "there" · Is "sanction" a contronym? |
| meaning in context | What do they mean by "Can you open the door?" · Is "I saw her duck" ambiguous? |

## 9. Language learning

| Part | Now | Where |
|---|---|---|
| Vocabulary acquisition | new words with their part of speech and meaning, from Wiktionary in learning sessions; words said in chat are never "typos" | `Lexicon`, `Spelling.fix` (`said`) |
| Grammar learning | the part of speech a word was each time Ibiko was sure, so new words are tagged better over time | `PartsOfSpeech` (rule 3, LEARNED) |
| Expression learning | sayings and their meanings ship in the table; the Learning chat can teach new ones through the API (kept as learning records, verified before use) | `formulas/Expressions`, `cognition/LearningRecords` |
| Error correction | typos (a dropped letter wins a tie: "autor" → author), agreement, a/an | `formulas/Spelling`, `words/GrammarCheck` |
| Knowledge consolidation | every learned item carries its source, status (unverified → corroborated → verified), history and expiry | `cognition/LearningRecords` (Memory › What Ibiko learned) |

## 10. Not yet built (next)

- **Learned figures**: sayings and figures taught in the Learning chat could be added to the sayings table on the phone, rather than only kept as records.
- **Etymology**: word origins (Wiktionary has them under CC BY-SA) as a coded table.
- **Sarcasm from voice**: tone of voice in talk mode would make "That's just great" easier to read.
- **Puns** beyond the table: a word whose two senses are both cued by the other words in the sentence (SenseTags already knows each sense's subject).
- **Phrasal verbs** as units ("give up", "look after") in the word record.

## 11. Tests

| Test | What it holds |
|---|---|
| `language/SentenceFormTest` | the twelve tenses, voice and modals, the four structures, the four functions, negation, conditionals, direct and indirect speech |
| `language/FiguresTest` | each figure found, and told apart: no sarcasm after good news, no simile in "I like a challenge", no metaphor in "the lion is an animal" |
| `language/PragmaticsTest` | indirect requests vs ability questions, hints, tone, feeling, sarcasm needing context, "I saw her duck" |
| `language/WordsEngineTest` | synonyms to contronyms; "running" as verb, gerund and participle; the JSON record; un-happi-ness |
| `LanguageEngineTest` | the brain answering language questions with the real dictionary, and the same reading in `premeditate` for every model |
