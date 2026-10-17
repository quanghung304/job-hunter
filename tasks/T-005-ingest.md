# T-005: Ingest — normalize, fingerprint, dedupe, upsert

Phase: 1 · Depends on: T-004 · Size: M

## Goal
`IngestService.ingest(List<NormalizedPosting>)` stores raw postings, merges duplicates into
`job` rows per the rules, and reports counts back to the collector run.

## Read first
- `docs/architecture/data-model.md` §1 (raw_posting, job), §3, §4

## In scope
- `TextNormalizer.companyKey(String)`, `TextNormalizer.titleKey(String)`,
  `Fingerprint.of(companyKey, titleKey)` exactly as §4.
- `IngestService` (transactional per posting batch of 50):
  validation → upsert raw_posting (update `last_seen_at`, payload/hash if changed) →
  resolve job (fingerprint; re-posting rule after 60 days) → create or merge job
  (merge rules in §4) → update `job.last_seen_at`.
- When a job's `description_hash` changes, publish `JobDescriptionChanged(jobId)` (Spring
  application event) — `enrich` will listen later (T-011); for now only publish.
- Return `IngestResult(newRaw, newJobs, updatedJobs, skippedInvalid)`.
- Wire: the admin endpoint `POST /api/admin/collect/{sourceCode}` (in `api`, see api-spec) that runs one
  collector, ingests, then calls `CollectorRunner.recordIngestCounts(...)`. T-013 moves this
  sequence into the pipeline.

## Out of scope
Expiry (T-013), any enrichment.

## Acceptance criteria
- [x] Same posting ingested twice → 1 raw_posting, 1 job, second run counts 0 new.
- [x] Same job from two sources → 2 raw_postings, 1 job; merge rules applied.
- [x] "Acme, Inc." / "ACME Inc" / "Acme" produce the same `company_key`.
- [x] "Senior Backend Engineer (Remote) - m/f/d" and "Senior Backend Engineer" same `title_key`.

## Tests
- `TextNormalizerTest`, `FingerprintTest` (≥ 20 rows incl. accents: "Società Srl" → `societa`).
- `IngestServiceIT` (dedupe, merge rules, re-posting after 60 days with fixed Clock,
  description change event, invalid posting skipped).

## Docs to update
data-model §4 if rules were refined (with examples). progress.
