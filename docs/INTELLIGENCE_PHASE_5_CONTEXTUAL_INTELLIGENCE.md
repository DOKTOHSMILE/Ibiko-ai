# Ibiko Intelligence Upgrade — Phase 5

## Implemented

- Stateful, bounded conversation context (up to 12 recent turns and 24 candidate entities).
- Salience/recency-based pronoun resolution with explicit abstention when candidates are ambiguous.
- Small explainable word-sense disambiguation rules for common polysemous words (bank, bat, Java, light, charge, mouse).
- Intent labels for questions, explanation, creation, search, edit, summary, translation, comparison, planning, continuation, greeting, thanks and feedback.
- Conservative language hints for English, Hausa, Yoruba, Igbo and Nigerian Pidgin; weak evidence remains `undetermined`.
- Temporal cue extraction and relative-date normalisation against an explicitly supplied reference clock.
- Spatial preposition and relation cue extraction.
- Versioned opaque conversation-context snapshot export/restore APIs.
- Cortex integration: contextual evidence is appended to the model brief.
- Reviewed semantic graph triple ingestion from CSV/TSV, with the existing licence/provenance gate.

## Safety and correctness boundaries

All output is labelled heuristic evidence. `actionRequested` is an intent classification only; it is not an execution permission, tool call, or capability dispatch. Existing app-layer explicit-command safeguards remain authoritative. Retrieved and imported text must remain data, never instructions.

Coreference deliberately abstains when it cannot identify a unique compatible antecedent. It does not infer a person's gender. Temporal normalisation is only as accurate as the caller-supplied reference timestamp and does not infer timezone-specific deadlines. The language identifier and built-in word-sense rules are small baselines, not general trained models. Word-sense coverage is limited to a few English polysemes. Grammar/POS labels from the earlier phases remain heuristic.

## Persistence

`Cortex.exportConversationContext()` returns a versioned Base64 snapshot. The app may store it in its existing private persistence layer per conversation and restore it using `restoreConversationContext()`. Call `resetConversationContext()` when starting a new conversation. No conversation snapshot is written automatically, and no cloud/network operation is performed.

## Dataset ingestion

`KnowledgeDatasetImporter.importRelations(...)` accepts reviewed CSV/TSV triples (`subject`, `predicate`/`relation`, `object`, optional `language`). It requires an explicit human-reviewed flag and the same licence/provenance policy as lexical imports. It does not import raw training/evaluation corpora into the retrieval store.

## Verification

New JVM unit tests cover entity reference, intent labelling, word-sense cues, relative dates, snapshot round-trip and invalid snapshots. Run the Android project's normal unit-test and assemble tasks in the project's supported Gradle environment before release. These tests do not establish production-grade NLP accuracy.
