# Ibiko Intelligence Modules — first integration

This change adds a deterministic, lightweight linguistic pre-processing layer and a provenance-aware SQLite knowledge store.

## Implemented

- `ai.ibiko.intelligence.UnderstandingEngine`: tokenisation with character offsets, conservative heuristic POS labels, request/question classification, negation detection, temporal/spatial/causal cue detection, and optional pronoun reference resolution using a caller-supplied previous subject.
- `ai.ibiko.intelligence.StructuredKnowledgeDatabase`: Android SQLite tables and indexed lookup for lexical entries, semantic relations, and a dataset provenance/licence registry.
- `ai.ibiko.suit.Comprehension`: adds the understanding evidence to the existing model-facing `brief()` context. It does not invoke tools or execute commands.
- Unit tests cover core signals and explicitly assert that unsupported language identification remains `undetermined`.
- Phase 5 adds per-chat contextual state, candidate coreference, contextual word-sense cues, intent labels, temporal/spatial cue extraction, relevance/decay scoring, and cross-turn contradiction candidates. The local chat path snapshots this bounded context in the app-private chat folder.

## Important limitations

The language engine is a deterministic heuristic baseline, not a trained parser, language identifier, coreference model, or substitute for Qwen. POS labels are approximate and flagged as inferred. The current `LanguageIdentifier` is a tiny lexical/orthographic hint for English, Hausa, Yoruba, Igbo and Nigerian Pidgin; it returns `undetermined` when cues are insufficient. It is not a validated language-identification model. Coreference now uses a bounded per-chat entity history and abstains when antecedents are ambiguous. It remains heuristic and is not a trained resolver. No dataset has been downloaded or bundled by this change.

## Dataset ingestion policy

Before importing any external dataset, verify the exact source revision, upstream provenance, language coverage, file schema, and licence for the specific files being imported. Record the commercial-use decision in `dataset_registry`. Do not treat a repository's code licence as the dataset licence. Keep non-commercial/evaluation-only data out of production training and redistribution unless permission is obtained.

Recommended future adapters should transform reviewed data into:

- `lexeme(language, lemma, pos, definition, source, licence, confidence)`
- `relation(subject, predicate, object, language, source, licence, confidence)`

Keep supervised training corpora and evaluation benchmarks outside the production retrieval database to prevent benchmark leakage.

## Next integration stages

1. Replace the small lexical language hint with a compact, tested language-ID sidecar and measure short-message accuracy before routing by language.
2. Add trained POS/dependency, intent, NLI, word-sense and coreference models as optional components; preserve deterministic fallbacks.
3. Add reviewed adapters for Parquet/JSONL and language-specific treebanks where licences permit; keep training/evaluation corpora separate from retrieval storage.
4. Expand multilingual grammar and lexical coverage with validated Hausa, Igbo, Yoruba and Nigerian Pidgin resources.
5. Benchmark startup time, per-turn latency, snapshot size, SQLite query latency and peak memory on arm64 Android hardware.

## Phase 3 addition — candidate clause frames

`ai.ibiko.intelligence.ClauseStructure` now extracts up to four conservative candidate S-V-O frames from the existing token/POS stream. It prefers lexical verbs to auxiliaries, records local negation, and labels every frame as inferred. `UnderstandingEngine.Result.toPromptContext()` includes these frames, so the existing `Comprehension.brief()` path passes them to the model. See `docs/INTELLIGENCE_PHASE_3_CLAUSE_FRAMES.md` for scope and limitations. This remains a heuristic baseline, not a full dependency parser.

## Phase 4 addition — semantic evidence and candidate knowledge-graph edges

`ai.ibiko.intelligence.SemanticIntelligence` adds a small deterministic lexical map for synonym/antonym candidates, simple hypernym candidates, component-to-whole candidates, and object-function candidates. It can compare an explicit premise/hypothesis pair and return `ENTAILMENT_CANDIDATE`, `CONTRADICTION_CANDIDATE`, or `UNKNOWN`. These labels are deliberately conservative evidence, not verified logical conclusions. The current built-in vocabulary is small and English-centric; production coverage should come from reviewed, licensed datasets and evaluated models.

The semantic evidence is attached to `UnderstandingEngine.Result` and included in the existing comprehension prompt context. `StructuredKnowledgeDatabase.putReviewedSemanticEdge` requires a caller-provided human-review confirmation and preserves source/licence/confidence metadata. The engine never automatically writes its heuristic candidates into the knowledge graph.

See `docs/INTELLIGENCE_UPGRADE_CHANGELOG.md` for detailed scope and limitations. The Android Gradle test task could not run in this environment because the Gradle 8.9 distribution host was unreachable. The changed pure-Java classes were compiled with Java 17 and exercised by a standalone smoke test.


## Phase 5 addition — contextual memory and reference tracking

`ContextualUnderstandingEngine` now adds intent labels, recent-entity coreference candidates, word-sense candidates, temporal/spatial cues, relevant remembered entities and cross-turn contradiction candidates. `Cortex` keeps independent bounded state per chat; `LocalAsk` saves/restores a compact versioned snapshot in that chat's private memory directory. The system abstains when references are ambiguous and labels every inference as heuristic evidence. See `docs/INTELLIGENCE_PHASE_5_CONTEXTUAL_INTELLIGENCE.md`.
