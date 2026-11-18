# Ibiko Intelligence Upgrade — Phase 3: Clause Frames

## Scope

This phase adds a conservative, deterministic candidate subject–verb–object (SVO) frame extractor to the existing local understanding preprocessor. Frames are appended to the existing `[LOCAL UNDERSTANDING]` evidence brief consumed by the comprehension prompt builder.

## Runtime behaviour

- Uses the existing tokenizer and heuristic part-of-speech labels.
- Prefers lexical verbs over auxiliaries, so patterns such as `does not build` use `build` as the candidate predicate.
- Extracts the nearest nominal candidate before the predicate as subject and the nearest nominal candidate after it as object.
- Preserves a local negation signal around the predicate.
- Splits selected contrast/sequencing conjunctions (`but`, `however`, `then`) into candidate clauses.
- Caps output at four frames to bound prompt growth.
- Labels each frame `source=inferred`; it is evidence for the LLM to verify, not a certified parse.

## Safety and limitations

This is not a trained dependency parser and does not claim linguistic certainty. It can mislabel ambiguous words, passive voice, complex coordination, ellipsis, long-distance dependencies, and non-English constructions. It does not execute tools or capabilities. Dataset ingestion remains separate from inference, and retrieved content must continue to be treated as evidence rather than instructions.

## Validation

`ClauseStructureTest` covers a basic SVO sentence, auxiliary-plus-negation handling, and empty input. The standalone Java classes can be compiled independently. The full Android Gradle test task still requires the configured Gradle 8.9 distribution and Android build dependencies to be available.
