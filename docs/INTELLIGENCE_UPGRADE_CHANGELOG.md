# Ibiko intelligence upgrade changelog

## Phase 2 — governed dataset ingestion and retrieval

### Added
- `ai.ibiko.intelligence.importer.DatasetImportPolicy`: explicit retrieval/training/evaluation use decisions; fail-closed for unknown or unreviewed terms.
- `ai.ibiko.intelligence.importer.DelimitedDatasetReader`: streaming CSV/TSV reader.
- `ai.ibiko.intelligence.importer.KnowledgeDatasetImporter`: transactional import for reviewed lexical tables.
- `ai.ibiko.intelligence.KnowledgeContextBuilder`: bounded SQLite evidence context with source/licence/confidence metadata.
- Optional `Cortex.context(message, database, language, maxKnowledgeChars)` integration.

### Safety and design
- Importer does not download data, accept terms, train models, or execute app capabilities.
- Evaluation-only material cannot enter retrieval or training through this importer.
- Non-commercial-only material is blocked when the caller marks the product as commercial.
- Retrieved text is explicitly labelled as evidence, not instructions.
- Existing `Cortex.context(String)` behavior remains unchanged.

### Known limitations
- Importer currently accepts CSV/TSV lexical data only; Parquet, JSONL, graph, speech and image datasets need separate adapters.
- Human review remains mandatory; licence labels are not legally interpreted by code.
- Full Android build/test verification remains outstanding due unavailable Gradle distribution download.

## Phase 4 — semantic evidence and reviewed graph persistence

### Added
- `SemanticIntelligence`: bounded built-in lexical candidates for synonyms, antonyms, simple taxonomy, component/part-of and function/affordance relationships.
- Conservative premise/hypothesis comparison with `ENTAILMENT_CANDIDATE`, `CONTRADICTION_CANDIDATE`, or `UNKNOWN` outcomes.
- Semantic evidence is included in `UnderstandingEngine.Result.toPromptContext()` and therefore reaches the existing comprehension brief.
- `StructuredKnowledgeDatabase.putReviewedSemanticEdge(...)` requires an explicit human-reviewed flag before persisting a graph edge.
- Bounded exact-predicate graph lookup via `findRelationsByPredicate(...)`.
- Unit tests for graph candidates, polarity conflict, unknown inference and prompt integration.

### Safety and limitations
- All generated edges and inference labels are explicitly candidates, not verified facts. The built-in lexicon is intentionally small and English-centric.
- Word-sense disambiguation is not claimed: polysemous words require context-rich lexical data and a trained model.
- Antonym matches alone do not prove contradiction; the implementation only emits a contradiction candidate for explicit polarity or antonym conflict patterns.
- No dataset is downloaded or automatically imported. Candidate edges are not automatically persisted; persistence requires human review and provenance.
- The inference helper is a lightweight lexical heuristic, not a replacement for an NLI model or full semantic parser.

## Phase 5 — contextual intelligence and per-chat memory

### Added
- `context.IntentClassifier`: explainable intent categories with confidence and a separate `actionRequested` hint. It does not dispatch capabilities.
- `LanguageIdentifier`: conservative lexical/orthographic language hints for English, Hausa, Yoruba, Igbo and Nigerian Pidgin; returns undetermined when evidence is insufficient.
- `context.CoreferenceResolver`: recency/salience-based pronoun resolution with grammatical compatibility and explicit abstention on ambiguity; no gender inference.
- `context.WordSenseDisambiguator`: small contextual cue rules for bank, bat, Java, light, charge and mouse; returns ambiguous when cues are insufficient.
- `context.TemporalSpatialAnalyzer`: extracts temporal and spatial expressions; relative dates/durations are normalised against a supplied reference clock using the device timezone.
- `context.ConversationMemory`: bounded recent entity/turn memory, salience decay, relevance ranking and candidate proposition history.
- Cross-turn opposite-polarity detection. Results are explicitly contradiction candidates, not verified logical conclusions.
- Versioned context snapshot export/restore. The snapshot stores a bounded recent context summary and candidate entities/propositions.
- `Cortex` now isolates contextual state by conversation key and keeps a bounded eight-conversation in-memory cache.
- `LocalAsk` restores/saves the context snapshot under the current chat's private `memory/.context-v1` file. The chat transcript remains the durable source of truth; damaged snapshots are discarded and do not block an answer.
- `KnowledgeDatasetImporter.importRelations(...)` imports reviewed CSV/TSV graph triples only after licence/provenance approval and explicit human-review confirmation.

### Safety and limitations
- Intent labels and `actionRequested` are never execution permissions. The app's existing explicit capability/action protocol remains authoritative.
- Coreference abstains on ambiguity. The baseline has no trained resolver and does not infer gender.
- Language ID and word-sense rules are small heuristic baselines, not trained language identification or WSD models. Word-sense rules cover only a few English polysemes.
- Temporal expressions are approximate; timezone comes from the device and natural-language ambiguity is not fully resolved.
- Spatial cues identify relation words but do not yet provide a full geometric/world model.
- Cross-turn contradiction detection only matches the same heuristic subject/predicate/object frame with opposite explicit polarity. It does not establish truth or detect every paraphrase.
- Context memory is bounded and per chat; its snapshot is stored in the app-private chat folder. The transcript remains authoritative. No network calls or external dataset downloads are performed.
- The relation importer requires human review and a reviewed licence/provenance policy. It does not import training/evaluation data into production retrieval.

### Verification
- New context classes compile with Java 17.
- Standalone smoke checks cover recent-entity resolution, word-sense cues, temporal normalisation, snapshot round-trip and cross-turn polarity candidates.
- Full Android Gradle unit tests and APK build remain unverified because the Gradle 8.9 distribution could not be fetched in this environment.

## Phase 7 — adaptive learning and evidence calibration

### Added
- `brain.AdaptiveLearningEngine` ranks user-taught facts and explicit corrections using lexical relevance, source type and a bounded recency adjustment.
- The newest explicit correction for an exact normalized subject supersedes older taught/correction entries for retrieval; the journal itself remains intact for audit and user-controlled forgetting.
- Corrections are recorded through the adaptive engine, preserving the existing `FactCorrection` explicit-user-input gate and `LearningJournal` sensitive-data filter.
- Answer feedback can be recorded as `FEEDBACK`, but feedback entries are never returned as factual knowledge and do not silently train model weights.
- `OfflineBrain.taughtFor(...)` now uses ranked adaptive evidence; the local-model prompt receives provenance labels and an explicit warning that derived confidence is a retrieval heuristic, not truth probability.
- Added tests for correction supersession, irrelevant-memory exclusion, feedback/fact separation and confidence caveats.

### Safety and limits
- No model weights are updated. This is retrieval-time adaptation over user-approved journal entries, not online neural training.
- Confidence is a transparent heuristic derived from entry type and age; it is not statistically calibrated and must not be presented as a probability that a fact is true.
- Only exact normalized subject keys are superseded. Paraphrased subjects and semantic contradictions still require review or a stronger entity/relation resolver.
- Feedback is an evaluation signal only. It is not allowed to rewrite facts or increase their truth confidence.
- Existing journal consent, sensitive-data filtering, local persistence and explicit user correction flow remain in force.

### Verification
- New adaptive engine and its journal dependencies compile with Java 17 using `javac`.
- Full Gradle unit tests and Android APK build could not be run because the Gradle 8.9 distribution is not present locally and the environment cannot resolve `services.gradle.org`.

## 0.99.164 — Phase 9: shared context route parity

- Centralised the existing per-chat UCL/Cortex snapshot lifecycle in `suit/ConversationContext`.
- Reused it from ordinary model replies, extended reasoning and coding work.
- Ensured extended reasoning receives the existing Ibiko 1 premeditation evidence in addition to Wisdom.
- Added snapshot restore and cross-chat isolation tests; no new model, persona, parser, database or dependency.


## 0.99.165 — Phase 10: relevance-ranked Wisdom evidence

### Changed
- `formulas/Wisdom` now ranks matching stored knowledge before applying implications, rather than taking the first three matches in storage order.
- Ranking uses a symmetric Dice overlap score over the existing `Knowledge.meaning(...)` representation, so both question coverage and evidence precision matter.
- Exact duplicate evidence sentences are de-duplicated before selection.
- Equal scores preserve original order for deterministic results.
- The existing Wisdom output contract, three-evidence bound, Understanding formulas, Cortex call sites, and selected-model routing remain unchanged.

### Safety and limits
- The score is a retrieval relevance heuristic, not a truth probability or semantic proof.
- This does not alter truth verdicts, user-confirmed knowledge, the Truth Ledger, action permissions, model weights, or command execution.
- It does not add a persona, agent, model, dependency, database, or new memory store.

### Verification
- Added tests for evidence ranking, duplicate suppression, and irrelevant-evidence exclusion.
- Full Gradle/Android build still needs to run in the repository's normal build environment.


## 0.99.166 — Phase 11: integrated intelligence quality pass

- Tightened Truth agreement from substring overlap to normalized exact claim wording; normalized subject keys and added null safety.
- Ranked TruthLedger recall by relevance while preserving false-before-true ordering.
- Upgraded Wisdom retrieval to IDF-weighted Dice using the existing knowledge tokenization.
- Hardened the existing model interpretation bridge: ambiguity/low-confidence hard stops and preservation of user constraints.
- Extended the existing Interpreter with conservative Hausa and Igbo detection and model translation prompts.
- Added regression tests across Truth, TruthLedger, Wisdom, Interpreter and action interpretation.
- No new persona, model, database, external dependency, dataset ingestion or command execution path.


## 0.99.167 — deterministic intelligence evaluation baseline

- Added a small labelled evaluation fixture for intent, POS, coreference and temporal/spatial cue extraction.
- Probe exposed missing deletion intent, unsafe gendered-pronoun resolution without gender evidence, and missing bare-duration extraction.
- Fixed those gaps conservatively; hypothetical “what if” deletion questions remain non-action questions.
- Focused Java probe: 32/32 assertions pass. This is a smoke-test result, not a general accuracy claim. Full Gradle/Android build and phone/model benchmarking remain pending.
