# Ibiko dataset ingestion and governance

## Scope

This phase adds local, streaming CSV/TSV lexical and reviewed graph-triple importers plus a fail-closed use-policy gate. It does not download datasets, accept online terms, train the Qwen model, or silently add benchmark data to user memory.

## Data flow

1. A maintainer obtains a dataset from its primary source outside the app.
2. A human reviews the dataset-specific licence, upstream source licences, attribution, provenance, intended purpose, and commercial-use status.
3. The importer receives a `Metadata` object with the reviewed decision and source URL.
4. `KnowledgeDatasetImporter.importLexicon(...)` streams rows in a single SQLite transaction.
5. Lexical rows are stored with language, lemma, POS and definition. Reviewed graph rows are stored as subject/predicate/object triples. Both retain dataset source, licence and confidence. The dataset registry records the source, version, licence, decision and checksum.

## Accepted lexical headers

- Lemma: `lemma`, `word`, or `term`
- Language: `language`, `lang`, or `language_code`
- Definition: `definition`, `meaning`, or `gloss`
- Part of speech: `pos`, `part_of_speech`, or `category`

CSV and TSV are supported. Quoted separators, escaped quotes, CRLF and embedded newlines inside quoted fields are supported. The importer is streaming and does not load the full corpus into memory.

## Licence gate

`DatasetImportPolicy` is fail-closed. Unknown or unreviewed licences/provenance are blocked. `NON_COMMERCIAL_ONLY` is blocked for a commercial product. `EVALUATION_ONLY` cannot be imported into retrieval or training. This is an engineering guard, not legal advice; a reviewer must confirm the actual dataset and all upstream component terms.

## Training vs retrieval vs evaluation

- Retrieval: curated lexical entries and factual triples with provenance; SQLite/indexed lookup.
- Training: supervised examples prepared in a separate offline training pipeline; never inferred from a retrieval licence.
- Evaluation: benchmark examples and split identifiers kept separate from training and production knowledge to prevent leakage.

`importLexicon(...)` handles retrieval lexicons. `importRelations(...)` handles CSV/TSV graph triples only when the caller explicitly confirms human review. Neither method ingests question-answer benchmarks or training corpora.

## Important limitations

- No network downloader is included.
- No automatic licence interpretation is attempted.
- The importer currently supports lexical tables and simple graph triples, not arbitrary JSON/Parquet, audio, images, or native graph dumps.
- Dataset-specific field mappings may need a preprocessing adapter.
- `confidence=0.65` is a provisional import confidence, not a measure of linguistic correctness. Production pipelines should attach quality scores from validation or human review.
- Large or malformed records should be rejected by an upstream validation pass and import limits should be added before accepting untrusted files.
