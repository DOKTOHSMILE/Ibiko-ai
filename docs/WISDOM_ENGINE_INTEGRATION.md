# Ibiko 1 Wisdom integration

## What is connected

The existing `ai.ibiko.ibiko1.formulas.Wisdom` and `Understanding` formulas are now wired into the shared `Cortex` and the main `LocalAsk` model-request path.

- `Brains.cortex()` supplies `Ibiko1.knownLines(question)` as the evidence source. These are the existing Ibiko 1 memory sentences, not generated instructions.
- `Cortex.wisdomContext(question)` builds the W1-W3 brief from the relevant remembered sentences.
- `LocalAsk` appends that brief **after** quick/device context compression, so the reasoning evidence is not accidentally removed by the prompt-size limiter. This is shared by local and remote model replies.
- `ThinkTurns` includes the same brief in the multi-agent council context.
- `AgentSession` includes the brief in coding/planning work prompts, including the initial project task.
- The no-model decision path in `Cortex.answerAlone()` also consults the same evidence and adds the implication only when relevant remembered evidence exists.
- Wisdom is not a selectable Think level.

## Evidence boundaries

The brief distinguishes:

1. Known sentences selected from Ibiko 1 memory.
2. What those sentences imply.
3. Assumptions expressed by the sentences.
4. What the sentences do not establish.
5. What could change or undo the implication.

Only up to three matching memory sentences are used by the existing Wisdom formula. If none match, the formula explicitly says no relevant stored detail was found and limits its inference to the question itself.

Wisdom output is model context/evidence only. It does not execute app functions, grant tool permissions, or bypass the app's explicit command gate. Existing action-code validation remains authoritative.

## Tests

- `WisdomTest` verifies limits, possibility language, evidence count, and the no-memory path.
- `SuitTest` verifies the shared Cortex exposes relevant remembered evidence and carries caveats while filtering unrelated facts.
- The existing no-model response tests remain unchanged for arithmetic, direct learned answers, and local decision support.

Run the normal project checks in the Android/Gradle environment:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The full Android build/device test is still required before release.
