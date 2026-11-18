# Phase 10 — relevance-ranked Wisdom evidence (0.99.165)

## Source-led scope

This phase modifies the existing `ai.ibiko.ibiko1.formulas.Wisdom` implementation only. It follows the existing single-assistant architecture and preserves the current `Wisdom.Applied` contract consumed by Cortex and the existing model routes.

## Problem found in the current source

`Wisdom.of(...)` previously walked the stored knowledge list in insertion order and stopped after the first three sentences sharing at least one content word with the question. This made the reasoning brief depend on storage order. A weak one-word overlap could occupy an evidence slot ahead of a much more relevant sentence. Duplicate stored lines could also consume multiple slots.

## Implementation

1. Reuse `Knowledge.meaning(...)` as the canonical token/synonym/stemming representation.
2. Score each candidate using the Dice coefficient: `2 × sharedMeaningWords / (questionMeaningWords + evidenceMeaningWords)`.
3. Sort highest relevance first; preserve original order for equal scores.
4. Remove exact normalized duplicate sentences before ranking.
5. Keep the existing maximum of three evidence sentences and pass each selected sentence through the existing `Understanding.of(...)` formula.
6. Preserve the existing no-evidence behavior and all output fields.

## Boundaries

- This is retrieval ranking, not a truth score, calibrated probability, entailment proof, or new learning mechanism.
- It does not rewrite or delete stored knowledge.
- It does not change Truth/TruthLedger verdicts, model selection, prompts outside the existing Wisdom brief, capability permissions, or command execution.
- No external dataset, dependency, persona, model, or database was added.

## Tests added

- A stronger match ranks ahead of a weak match even when it appears later in storage order.
- Duplicate evidence does not consume multiple evidence slots.
- Unrelated knowledge is excluded.
- Existing Wisdom tests continue to cover limits, assumptions, unknown evidence, and output contract.

## Verification status

The repository's unit tests are added but must be run with the project's Gradle 8.9 / Android toolchain. This environment does not have the Gradle distribution cached, so no full Android build is claimed.
