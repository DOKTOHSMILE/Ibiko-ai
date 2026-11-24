# Ibiko Cognitive Learning File

**File:** `IBIKO_COGNITIVE_LEARNING.md`\
**Purpose:** A build specification and learning reference for improving
Ibiko's reasoning depth, evidence handling, self-review, and continual
learning.

> Integration rule: extend the existing codebase and its current
> architecture. Do not introduce a new persona system or replace
> existing components. Connect these capabilities to the current Ibiko1,
> Cortex, Wisdom, Understanding, Knowledge, LocalAsk, BrainGate, UCL,
> Truth Ledger/Foundation, Foresight, and ConversationContext components
> wherever those components already exist.

------------------------------------------------------------------------

## 1. Primary objective

Ibiko should not merely produce a plausible next sentence. It should: 1.
Understand what the user is asking and what outcome is wanted. 2.
Separate known facts from assumptions, hypotheses, and unknowns. 3.
Retrieve relevant knowledge and inspect its provenance. 4. reason
through the problem using evidence and explicit intermediate
conclusions. 5. Check for contradictions, missing steps, and unsupported
claims. 6. Decide whether more reasoning is useful or whether the answer
is ready. 7. Respond clearly, with calibrated confidence. 8. Learn from
valid corrections and outcomes without blindly accepting every
statement.

Reasoning must be bounded and efficient. More internal passes do not
automatically mean better thinking.

## 2. Highest-priority engines --- build in this order

### Priority 1 --- Evidence & Truth Evaluation Engine

**Purpose:** Prevent confident but unsupported answers.

Required functions: - Classify each claim as `FACT`, `INFERENCE`,
`ASSUMPTION`, `HYPOTHESIS`, `PREFERENCE`, or `UNKNOWN`. - Attach
evidence IDs, source, timestamp, and confidence to factual claims. -
Detect when two pieces of evidence disagree. - Score source reliability,
directness, recency, and relevance separately. - Detect stale
information and mark time-sensitive claims for verification. - Never
turn an assumption into a fact merely because it appears repeatedly. -
If evidence is insufficient, say what is unknown instead of inventing a
conclusion.

Suggested output structure:

``` json
{
  "claim": "The statement being evaluated",
  "type": "FACT",
  "evidence_ids": ["ev_001", "ev_004"],
  "confidence": 0.86,
  "conflicts": [],
  "freshness": "CURRENT",
  "status": "SUPPORTED"
}
```

### Priority 2 --- Contradiction & Consistency Engine

**Purpose:** Find conflicts before they reach the user.

Check: - New claim versus retrieved knowledge. - New claim versus
earlier claims in the same answer. - Current user statement versus older
memory. - Different sources that disagree. - Units, dates, names,
quantities, and logical negations. - Whether the conflict is real or
only apparent because the claims refer to different times or contexts.

Do not silently discard a conflict. Resolve it using source quality,
timestamp, context, and direct evidence. If unresolved, disclose the
disagreement.

### Priority 3 --- Multi-Step Reasoning & Causal Engine

**Purpose:** Solve problems that require connected steps rather than
keyword matching.

Required capabilities: - Decompose the request into sub-problems. -
Identify dependencies between sub-problems. - Build a chain of premises
→ intermediate results → conclusion. - Distinguish correlation from
causation. - Test alternative explanations. - Check whether the
conclusion follows from the premises. - Track assumptions and identify
which assumption would change the result.

Represent a reasoning task as a directed graph: - Nodes: question,
evidence, assumptions, intermediate conclusions, final conclusion. -
Edges: supports, contradicts, depends_on, causes, or
merely_correlates_with.

A conclusion must retain links to the evidence and assumptions that
produced it.

### Priority 4 --- Metacognitive Planning & Review Engine

**Purpose:** Let Ibiko inspect the quality of its own answer before
responding.

The engine should create a compact plan: 1. What is the actual question?
2. What would count as a correct and useful answer? 3. What information
is available? 4. What information is missing? 5. Which reasoning method
fits this task? 6. What could make the answer wrong? 7. What
verification is proportionate to the risk?

The reviewer should inspect the draft for: - Relevance to the actual
request. - Logical gaps. - Unsupported factual claims. -
Contradictions. - Missing constraints. - Ambiguous wording. -
Overconfidence. - Unnecessary length.

The reviewer must not rewrite an answer simply to make it sound more
elaborate. It should make a change only when it finds a concrete defect
or improvement.

### Priority 5 --- Confidence Calibration Engine

**Purpose:** Make confidence reflect actual support.

Maintain separate scores for: - Evidence strength. - Reasoning
validity. - Source reliability. - Information freshness. - Overall
answer confidence.

Do not use one high score to conceal a weak dimension. Calibrate scores
against past outcomes and verified corrections. Confidence is not a
substitute for evidence.

Suggested labels: - `HIGH`: strong evidence and a valid reasoning
path. - `MODERATE`: reasonable support, with meaningful uncertainty. -
`LOW`: incomplete evidence or multiple plausible explanations. -
`UNVERIFIED`: requires an external check or missing information.

### Priority 6 --- Active Learning & Knowledge Consolidation Engine

**Purpose:** Learn from interactions while avoiding memory pollution.

Learning sources: - Explicit user corrections. - User-confirmed
preferences or instructions. - Verified task outcomes. - Reliable
imported knowledge. - Repeated patterns, only after sufficient evidence.

Rules: - Preserve the source and timestamp of every learned item. -
Separate user-specific preferences from general world knowledge. - Do
not learn a factual rule from a single unverified assertion. - When a
correction conflicts with an older item, supersede the old item while
preserving its history. - Store uncertainty rather than forcing a single
answer. - Support review, expiry, and deletion policies. - Do not store
secrets or sensitive information unless the user explicitly requests it
and the system is permitted to do so.

### Priority 7 --- Foresight & Consequence Evaluation Engine

**Purpose:** Consider likely outcomes before recommending an action.

For consequential recommendations: - Identify the proposed action. -
Estimate likely benefits, costs, risks, and dependencies. - Consider at
least one plausible alternative. - Identify irreversible or high-impact
consequences. - State which assumptions drive the forecast. - Avoid
presenting a forecast as a guaranteed outcome.

### Priority 8 --- Knowledge Retrieval & Concept Linking Engine

**Purpose:** Retrieve the right information, not merely similar wording.

Improve retrieval with: - Semantic similarity plus keyword and entity
matching. - Context and recency weighting. - Duplicate and
near-duplicate suppression. - Concept relationships and definitions. -
Query decomposition for multi-part questions. - Evidence diversity so
several copies of one source do not masquerade as independent
confirmation.

Reuse and strengthen the existing Wisdom, Knowledge, Understanding, UCL,
and ConversationContext paths rather than creating competing retrieval
stores.

------------------------------------------------------------------------

## 3. How many times should Ibiko think before answering?

Use **three default review passes**, not an unlimited loop. These are
quality-control stages, not a requirement to generate three complete
answers.

### Pass 1 --- Understand and plan

-   Parse intent and constraints.
-   Identify task type and risk.
-   Retrieve relevant context and evidence.
-   Create a short plan.

### Pass 2 --- Solve and verify

-   Work through the reasoning.
-   Check calculations, dependencies, evidence, and alternatives.
-   Mark unresolved uncertainty.

### Pass 3 --- Critique and finalize

-   Try to find a concrete flaw in the draft.
-   Check for unsupported claims and contradictions.
-   Correct only identified defects.
-   Produce the clearest useful answer.

**Optional fourth pass:** use only when the task is high-stakes,
technically complex, internally contradictory, or has a failed
verification check. The fourth pass should target the detected failure,
not repeat the whole process.

### Stop rule

Stop when all are true: - The request has been answered. - Required
evidence checks have passed, or uncertainty is disclosed. - No material
contradiction remains unresolved without disclosure. - The draft has no
known critical reasoning defect. - Another pass is unlikely to change
the answer materially.

Suggested limits: - Simple factual or conversational request: 1--2
passes. - Normal explanation or planning: 3 passes. - Complex technical,
mathematical, or multi-source task: 3 passes plus targeted
verification. - High-impact task: 4 or more targeted checks if
justified, with appropriate human/external verification.

Do not use a fixed token count or a fixed number of loops as a proxy for
intelligence. Use a time/compute budget and stop on diminishing returns.

------------------------------------------------------------------------

## 4. Cognitive task pipeline

Recommended logical flow:

``` text
User Input
   |
   v
Intent & Constraint Parser
   |
   v
Task / Risk Classifier
   |
   v
Context + Knowledge Retrieval
   |
   v
Evidence & Truth Evaluation
   |
   v
Reasoning Planner
   |
   v
Multi-Step / Causal Reasoner
   |
   v
Contradiction & Consistency Check
   |
   v
Confidence Calibration
   |
   v
Metacognitive Draft Review
   |
   +---- defect found and budget remains ---> targeted repair
   |
   v
Response Composer
   |
   v
User
   |
   v
Feedback / Outcome Evaluator
   |
   v
Learning Candidate Queue
   |
   v
Validated Knowledge Consolidation
```

This is a logical pipeline. Map each stage onto the existing classes and
interfaces. Do not duplicate an existing stage under a new name.

## 5. Learning record format

Store learning candidates as structured records. JSON Lines (`.jsonl`)
is suitable for machine ingestion; keep this Markdown file as the
human-readable specification.

Example:

``` json
{
  "id": "learn_000001",
  "category": "correction",
  "scope": "general",
  "content": "A concise statement of what was learned",
  "source_type": "explicit_user_correction",
  "source_ref": "conversation_or_document_reference",
  "observed_at": "2026-11-24T00:00:00Z",
  "confidence": 0.95,
  "verification_status": "user_confirmed",
  "supersedes": null,
  "expires_at": null,
  "tags": ["reasoning", "correction"]
}
```

Allowed `category` values: - `definition` - `fact` - `procedure` -
`reasoning_rule` - `correction` - `preference` - `task_outcome` -
`hypothesis`

Allowed `verification_status` values: - `unverified` - `corroborated` -
`verified` - `user_confirmed` - `disputed` - `superseded`

## 6. Learning and review policy

1.  Capture a candidate; do not immediately promote every statement into
    permanent knowledge.
2.  Classify its scope: general knowledge, project knowledge, or user
    preference.
3.  Record provenance and confidence.
4.  Search for duplicates and conflicts.
5.  Verify or corroborate factual claims when possible.
6.  Promote only when the evidence threshold for that category is met.
7.  If it conflicts with an older record, create a supersession link.
8.  Re-evaluate time-sensitive knowledge when it is retrieved.
9.  Keep a compact audit trail so the system can explain why it believes
    something.
10. Provide a controlled way to correct or remove learned records.

A user's explicit preference can be stored as a preference without
treating it as a universal fact. A factual claim from a user may be
recorded as user-provided, but should not automatically become verified
world knowledge.

## 7. Minimum evaluation tests

Build automated tests before declaring the engines complete.

  -----------------------------------------------------------------------
  Test                                Expected behaviour
  ----------------------------------- -----------------------------------
  Two sources directly conflict       Detect conflict and avoid false
                                      certainty

  Old fact versus newer reliable fact Prefer current evidence and retain
                                      timestamps

  User states an assumption           Keep it labelled as an assumption

  Three copies of one article         Do not count as three independent
                                      sources

  Multi-step calculation              Verify intermediate steps and final
                                      result

  Correlation presented as cause      Flag causal overreach

  Missing key information             Ask a focused question or disclose
                                      limitation

  User corrects a previous answer     Record correction and supersede the
                                      incorrect item appropriately

  Repeated review finds no defect     Stop; do not loop indefinitely

  Reviewer proposes stylistic change  Avoid needless regeneration
  only                                

  Low-confidence answer               Communicate uncertainty clearly

  High-impact recommendation          Trigger targeted risk and
                                      alternative checks
  -----------------------------------------------------------------------

## 8. Implementation order

Build and integrate in this order: 1. Evidence & Truth Evaluation. 2.
Contradiction & Consistency. 3. Multi-Step Reasoning & Causal Analysis.
4. Metacognitive Planning & Review with the three-pass default. 5.
Confidence Calibration. 6. Active Learning & Knowledge Consolidation. 7.
Foresight & Consequence Evaluation. 8. Retrieval and concept-linking
refinements.

For each engine: - Inspect the current source implementation first. -
Identify existing interfaces and data models. - Extend them instead of
creating parallel systems. - Add unit tests for normal, edge, and
failure cases. - Integrate into the actual response path. - Confirm both
local-model and API routes use the same cognitive pipeline where
intended. - Log stage outcomes and timings without exposing private
internal reasoning to the user.

## 9. Important design boundary

Ibiko should provide concise explanations, evidence summaries,
assumptions, and conclusions---not dump private internal scratchwork.
The internal process should be structured and auditable, while the
user-facing answer should contain the useful result and an appropriate
explanation.

**Definition of success:** Ibiko gives answers that are more accurate,
consistent, evidence-aware, appropriately cautious, and useful---not
merely longer or slower.

------------------------------------------------------------------------

## 10. Implementation map (where each engine lives)

All engines are pure Java in `app/src/main/java/ai/ibiko/ibiko1/`, extending what was already there.

| Engine | Code | Builds on |
|---|---|---|
| 1 Evidence & truth | `cognition/Evidence.java`: claim types, evidence records with ids, independent origins, freshness, `Evaluation.json()` | `formulas/Truth.java` (source ranks = reliability), `formulas/TruthLedger.java` |
| 2 Contradiction & consistency | `cognition/Consistency.java`: statements, units on one scale, negation, dates, real vs different-time conflicts, resolution by rank then recency | `Truth` G3 |
| 3 Multi-step & causal | `cognition/Reasoning.java`: reasoning graph (grounds, pivotal assumptions, follows, causal overreach), every worked step recomputed and repaired, causal overreach in answers | `formulas/Critical.java` K5 (causes in requests) |
| 4 Metacognition | `cognition/Metacognition.java`: plan (wants, method, missing, risks, constraints), complexity and risk, pass budget, review, repair of material defects only, bounded passes with the stop rule | `Critical` (kind, findings), `words/InnerVoice.java` (grammar passes) |
| 5 Calibration | `cognition/Calibration.java`: four dimensions, weakest-held overall, labels, outcomes from ratings, demotion of labels that over-promise (`brain/calibration.tsv`) | `Critical` K9 |
| 6 Active learning | `cognition/LearningRecords.java`: JSONL records in this file's format (`brain/learning.jsonl`), duplicates, corroboration, supersession with history, scopes, secrets refused, expiry, forget | `Knowledge`, `TeacherCheck` |
| 7 Foresight | `formulas/Foresight.java` `weigh()`: benefits, costs, risks, irreversibility, alternatives, assumptions | `Foresight` P5 (look before acting) |
| 8 Retrieval | `Evidence.origins()` / `nearSame()`; `Ibiko1.foundation()` counts copies once | `Hops`, `Wisdom`, `Understanding` |

**The pipeline, shared by every brain** (`cognition/Cognition.java`):

- *Before* any brain answers: `Ibiko1.premeditate()` → `Cognition.plan()` (pass 1, plus consequences for a weighty action). The offline model, API models and agents all receive it.
- *After* a model or API reply: `ThinkTurns.factCheck()` → `Ibiko1.reviewReply()` → the truth check, working, consistency, causes and calibrated confidence (passes 2–3). Anything found is said in a note under the reply.
- *Ibiko v1's own replies*: `Ibiko1.says()` → `Cognition.review(..., repair = true)`. A concrete defect is put right before the reply is shown, and the step says what was put right.
- *Learning*:
  - ratings (👍/👎) → `Calibration.outcome()` and the learning records
  - "no, it's …" corrections → `explicit_user_correction` records that supersede
  - Learning-chat answers → records by `TeacherCheck` verdict
  - stated preferences → `preference` records (scope `user`)
- *Audit*: `Cognition.log()` keeps each stage's outcome and time, never the scratchwork.

**Tests**: `app/src/test/java/ai/ibiko/ibiko1/cognition/CognitionTest.java` — the twelve rows of §7 and each engine's edges.
