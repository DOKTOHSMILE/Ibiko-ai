# Ibiko

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Ibiko's app icon">

Ibiko is an AI assistant for Android. It chats, researches, writes, draws, and writes and runs code, all from one
chat on your phone. You bring the brain: an AI provider's API, a model downloaded to the phone, or Ibiko's own
built-in brain, which needs no model at all.

Android 14 or newer, 64-bit ARM. Package `ai.ibiko`.

## What it does

- **One assistant, one chat.** Every message goes to Ibiko. The brain in use reads what you mean and Ibiko acts on it;
  your words alone never run anything.
- **Any brain.**
  - An API model: any OpenAI-compatible provider or Anthropic, with backups tried in order.
  - A model on the phone (GGUF files such as Qwen3, Llama, Gemma, Phi), running offline through llama.cpp.
  - Ibiko v1, the app's own brain: built-in facts, exact arithmetic, a dictionary and common-sense links, and what it
    has learned from you. No model needed.
- **A coding agent.** It reads, writes and searches files, runs commands in a real Linux (Alpine, inside the app; no
  Termux), and clones, commits, pushes and opens pull requests on GitHub. It plans first, checks each step and keeps a
  record, so it can carry on after an interruption.
- **Asks before it acts.** A shell command, a push, a pull request, a post or anything that changes one of your
  accounts waits for your yes. Three modes: Plan, Accept edits, and Auto (which you switch on yourself).
- **Connectors.** Web search, offline developer docs, Gmail (read and draft), your device calendar, GitHub, Notion and
  any MCP server over https.
- **Pictures.** Icons, logos, diagrams and charts are drawn on the phone from the brain's SVG. Photo-like pictures use
  your provider's image model.
- **Social accounts.** Posts and comments on X and Facebook from your signed-in account, only on your yes (or
  automatically, if you turn that on), and scheduled posts. Other apps get the text handed to them for you to send.
  X, Facebook, Instagram and Reddit can be searched for research.
- **Voice.** Speak with the mic; replies can be read aloud in a human-sounding voice that runs on the phone (Kokoro,
  through sherpa-onnx).
- **Attachments.** Photos, videos, PDFs, Office documents and other files, picked in the app or shared to it with
  Share › Ibiko.
- **Each chat its own folder.** A chat sees only its own files, and deleting the chat deletes them.
- **Private by design.**
  - Keys and tokens stay on the phone, sealed with the Android Keystore.
  - Every connection is https.
  - Nothing is included in backups or device transfers.
  - Pages a model writes can't reach the network except public script libraries.

## Build

Gradle 8.9, Android Gradle Plugin 8.7.3, Java 17, compileSdk 35, minSdk 34, NDK 27.2 (the on-device model engine is
C++, built with CMake).

```
./gradlew testDebugUnitTest assembleDebug
```

Pull requests run the unit tests, Android lint and the debug and release (R8) builds (`.github/workflows/ci.yml`).
A release build is signed only when `IBIKO_KEYSTORE`, `IBIKO_KEYSTORE_PASSWORD`, `IBIKO_KEY_ALIAS` and
`IBIKO_KEY_PASSWORD` are set. No provider key belongs in this repository: keys are entered in the app.

## Where things are

| Folder (`app/src/main/java/ai/ibiko/`) | What it holds |
|---|---|
| `agent/` | The coding agent: tools, approvals, plans, workers |
| `llm/` | API providers, the on-device model, key storage |
| `ibiko1/`, `learn/`, `suit/` | Ibiko v1: reasoning, comprehension, learning |
| `connect/`, `net/` | Connectors, GitHub, web fetching and search |
| `linux/` | The built-in Alpine Linux and terminal |
| `chat/`, `stage/` | The chat, its storage and screens |
| `social/`, `voice/`, `design/` | Social accounts, speech, the design board |

More detail: [`docs/APP_FUNCTIONS.md`](docs/APP_FUNCTIONS.md) (the functions a brain can use, generated from the
code), [`docs/SUIT_ARCHITECTURE.md`](docs/SUIT_ARCHITECTURE.md), [`docs/FORMULAS.md`](docs/FORMULAS.md) (Ibiko v1's
rules), [`docs/SOCIAL_INTELLIGENCE.md`](docs/SOCIAL_INTELLIGENCE.md), [`docs/TRAINING.md`](docs/TRAINING.md) and
[`PLAN.md`](PLAN.md).

The tools in `tools/` rebuild the shipped data (`build_world_facts.py`, `build_more_facts.py`, `build_lexicon.py`,
`build_concepts.py`) and the icons (`make_icon.py`, `make_logo.py`).

## Licences of shipped data

- Countries and population: mledoze/countries (ODbL) and datasets/population (PDDL); see
  `assets/knowledge/WORLD_FACTS_LICENSE.txt` and `MORE_FACTS_LICENSE.txt`.
- Dictionary and common-sense links: Open English WordNet (CC BY 4.0) and ConceptNet 5.7 (CC BY-SA 4.0); see
  `assets/knowledge/COMPREHENSION_LICENSE.txt`.
- Voice: Kokoro and sherpa-onnx (Apache 2.0).
- Icons: drawn from Google's Material Symbols Rounded (Apache 2.0).
