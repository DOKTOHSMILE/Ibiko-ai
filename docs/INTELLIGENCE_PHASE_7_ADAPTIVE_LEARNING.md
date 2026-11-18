# Phase 7 — Adaptive learning

Ibiko adapts its retrieval from explicit user-taught facts and corrections without changing Qwen or API model weights.

## Retrieval flow

1. Read the existing local `LearningJournal`.
2. Ignore feedback and unanswered-question records as factual evidence.
3. Token-match the current question against each taught fact/correction.
4. Prefer the newest explicit correction for the exact same normalized subject key.
5. Rank remaining evidence by lexical relevance, source type and a small recency adjustment.
6. Attach source kind and derived confidence to the model context.
7. Preserve the user's ability to forget entries using the existing memory controls.

## Confidence interpretation

The displayed score is a retrieval heuristic only. It combines the entry kind (explicit correction vs. taught fact) and age. It is not a calibrated probability, source verification, or guarantee of truth. The model prompt explicitly says user-provided memories are not independently verified.

## Feedback boundary

`recordAnswerFeedback(...)` records an evaluation signal as `FEEDBACK`. Such entries are never returned by `retrieve(...)` and cannot become facts. No automatic self-training, model-weight updates, network upload or capability execution is triggered by the adaptive engine.

## Files

- `app/src/main/java/ai/ibiko/brain/AdaptiveLearningEngine.java`
- `app/src/main/java/ai/ibiko/brain/OfflineBrain.java`
- `app/src/main/java/ai/ibiko/LocalAsk.java`
- `app/src/test/java/ai/ibiko/brain/AdaptiveLearningEngineTest.java`
