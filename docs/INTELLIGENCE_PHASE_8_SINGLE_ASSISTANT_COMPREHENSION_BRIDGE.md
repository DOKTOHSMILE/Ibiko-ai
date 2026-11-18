# Phase 8 — Single-assistant comprehension bridge (0.99.163)

## Existing architecture retained

This build starts from the latest supplied Phase 7 source (version 0.99.162) and preserves its files and changes. The normal message path is one Ibiko assistant. The selected brain is Ibiko 1, the on-device model, or the chosen API provider; the same app-level gates and shared prompt path remain in place. No three-persona design is introduced.

## Existing systems reused

- `ai.ibiko.ibiko1.comprehend.Comprehension` reads intent, operations, attributes, negation, references, word senses, goals, constraints, conditions and clarification.
- `Ibiko1.hear(...)` already reads user messages through the UCL.
- WordNet and ConceptNet tables are loaded once by `Ibiko1`.
- Phase 7's `LocalAsk.comprehensionContext(...)` persists the separate Cortex context per chat.
- `LocalAsk` is the shared prompt path for the selected local model and API provider; Wisdom and adaptive evidence remain in that path.

## Missing connection fixed

The richer `ibiko1/comprehend` result was maintained by Ibiko 1 but was not included in the selected model's prompt. `LocalAsk` now retrieves the existing UCL `Meaning` for the original user wording and passes it to `Cortex.context(message, conversationId, meaning)`. Cortex appends the concise `Meaning.promptBrief()` with an explicit advisory-only label. That prompt form carries intent, operations, negation, word senses and constraints, but leaves references and clarification to Cortex's durable per-chat coreference context. Using the original wording avoids treating an interpreter's translated copy as another conversation turn.

## Cross-chat isolation

The original UCL held one recent-reference queue inside the app-wide `Ibiko1` instance. That could let a pronoun in one chat resolve to an object mentioned in another. `ConversationReading` now shares the immutable WordNet/ConceptNet tables but gives each chat its own `Comprehension` reader and cached latest meaning. It keeps at most eight active chat readers; the chat transcript and Phase 7's per-chat Cortex snapshot remain the durable history. `BrainGate` and `LocalAsk` use the same captured chat key.

## Safety and scope

- The structured reading is advisory context, not an executable command.
- No function-code parser, approval, capability, or execution gate was changed.
- No new persona, model, model call, dependency, dataset or database was added.
- The previous Phase 7 files were preserved; this is an incremental build.

## Verification

Regression tests cover cross-chat pronoun isolation, bounded session count, same-message caching and advisory prompt formatting. A standalone Java 17 smoke test is run for the UCL-to-Cortex bridge. Full Android/Gradle tests could not run here because the Gradle 8.9 distribution host was unreachable. Phone verification remains unchecked in `PLAN.md`.
