# Phase 9 — Shared comprehension context across reasoning routes (0.99.164)

## Source-led scope

This phase continues the repository's existing single-assistant architecture. It does not add a model, persona, agent identity, parser, database, dataset or external dependency.

## What the source already had

- `Ibiko1.understood(...)` reads the original message through the existing Universal Comprehension Layer (WordNet/ConceptNet-backed `Comprehension`).
- `Cortex` holds bounded per-conversation contextual understanding and can export/restore its snapshot.
- `LocalAsk` already restored and saved `files/ibiko/chats/<chat>/memory/.context-v1` around ordinary model replies.
- `Ibiko1.premeditate(...)` already combines the existing Truth foundation, TruthLedger warnings, Foresight, Reflection, Critical reading and applicable data/probability notes.
- `Cortex.wisdomContext(...)` already applies the existing `formulas/Wisdom` logic to Ibiko 1's remembered lines.

## Gap addressed

The snapshot lifecycle was embedded inside `LocalAsk`, so the extended-reasoning route and coding-work route did not reuse the same durable per-chat UCL/Cortex context. The extended-reasoning route also supplied Wisdom without the complete `premeditate` evidence packet.

## Changes

1. Added `ai.ibiko.suit.ConversationContext`, a small shared adapter that restores the bounded snapshot, reads the original user wording through `Ibiko1.understood`, asks the existing Cortex for the advisory context, and saves the updated snapshot atomically.
2. Refactored `LocalAsk` to use that adapter without changing its original-vs-translated wording behavior.
3. Updated `ThinkTurns` to supply the same per-chat comprehension context plus the existing `Ibiko1.premeditate` and Wisdom briefs.
4. Added conversation-aware overloads of `Ibiko1.premeditate` and `Ibiko1.foundation`; the selected chat key now reaches the existing UCL read instead of silently using the default session.
5. Updated `AgentSession` to include the same chat-scoped comprehension context alongside its existing premeditation and Wisdom evidence.
6. Added tests for snapshot restoration after Cortex recreation and isolation between conversation keys.

## Safety and boundaries

- Context blocks are advisory evidence only; they are not parsed as tool calls and do not change `BrainGate`, `FunctionCode`, approval or capability execution.
- The chat transcript remains authoritative. A damaged, oversized or missing snapshot is disposable.
- New snapshots carry a conversation-key envelope; a snapshot copied into another chat key is rejected. Legacy phase-8 raw snapshots remain readable because they were stored inside each chat's private folder.
- The snapshot is capped at 128 KiB and written via a temporary file before replacement.
- No existing knowledge, memory, chat, model or persona files are deleted or migrated.

## Verification

- Source-level integration checks: pending in this build environment.
- Unit tests added: `ConversationContextTest`.
- Full Android Gradle/device build: not run in this environment; run the repository's normal Gradle test/build on the user's build host before merging.
