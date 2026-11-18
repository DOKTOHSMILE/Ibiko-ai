# Ibiko Intelligence Evaluation — Baseline 1

## Scope

This is a deterministic, local evaluation of the existing rule-based intelligence components. It tests representative intent labels, parts-of-speech tags, pronoun resolution, and temporal/spatial cue extraction. It does **not** measure the downloaded LLM, factual accuracy over open-domain questions, model training, or end-to-end user satisfaction.

## Initial probe and findings

A first 32-assertion probe produced **29/32 (90.6%)**. The three failures were diagnostic rather than a single aggregate quality score:

1. `Please delete the file` was labelled as a generic statement because the intent vocabulary had no deletion class.
2. A gendered pronoun (`he`) could resolve to any remembered person because the memory schema stores person/thing but no gender.
3. A bare duration (`2 days`) was not extracted unless preceded by `in` or followed by `ago`.

## Changes made from the probe

- Added an advisory `DELETION` intent label for delete/remove/erase/wipe/clear language. Hypothetical openers such as “what if” take precedence and remain non-action questions; explicit negation such as “do not delete” also sets `actionRequested=false`. It sets `actionRequested=true` but does not execute anything; app-side capability authorization remains separate.
- Coreference now abstains on gendered pronouns because the current entity memory has no gender evidence. It also skips null/blank pronoun entries and safely rejects null entity types for `it` compatibility.
- Temporal extraction now recognises bare durations and labels their direction as unspecified instead of fabricating a calendar date.
- Added regression coverage in `IntelligenceEvaluationTest`.

## Reproducible evaluation categories

| Category | Current fixture | What it measures | Known boundary |
|---|---:|---|---|
| Intent | 17 cases plus a negated-action assertion | Single-label routing context | Heuristic English cue patterns; not execution authorization |
| Parts of speech | 10 tokens | Common closed-class, suffix, and number tags | Polyfunctional words and sentence-level syntax need larger corpus |
| Coreference | 3 supported/abstention cases | Number/type compatibility and abstention | Gender, discourse roles, and long-distance references are not represented |
| Temporal/spatial | 3 cue assertions | Relative date, duration, and spatial preposition extraction | No weekday/date parsing, spatial graph, or relation direction inference |

## How to interpret scores

Report per-category accuracy and abstention separately. Do not combine these small fixtures into a claim of general language understanding. The next evaluation expansion should use a held-out, labelled corpus with multiple examples per phenomenon, ambiguity cases, and adversarial negatives. The LLM bridge should be benchmarked separately on a fixed phone/model/device configuration, reporting load time, time-to-first-token, decode tokens/second, memory, and repeated-run variance.

## Verification status

The focused Java probe is executable without Android runtime dependencies. The Android Gradle/JUnit suite still requires the project's normal Android/Gradle toolchain; a successful local Java probe must not be described as a full app build.

## First corrected probe

After the fixes, the same 32-assertion probe passes **32/32 (100%)**. This is only a tiny smoke fixture and must not be interpreted as a 100% language-understanding score. The four committed JUnit evaluation groups expand the repeatable checks and are intended to grow as a labelled held-out corpus is added.
