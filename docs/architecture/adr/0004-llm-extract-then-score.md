# ADR-0004: Rules → LLM extraction → deterministic eligibility → LLM scoring

Status: Accepted (trigger amended by ADR-0010) · Date: 2026-10-16

## Context
Asking an LLM "is this job good for me, 0–100?" in one step mixes facts (is it US-only?)
with judgment (how good is the skill match?), is hard to debug, and repeats work for
every profile. Cost must stay small.

## Decision
1. Rule prefilter (free) removes obviously irrelevant jobs.
2. LLM **extraction** turns the description into structured attributes, once per job and
   prompt version, independent of any profile.
3. **Eligibility** is plain Java comparing attributes with the profile; fully unit-tested.
4. LLM **scoring** runs only for ELIGIBLE/UNCERTAIN jobs and returns a structured breakdown.
5. Steps 2 and 4 run only when the owner asks for that job; the pipeline makes no LLM calls (ADR-0010).

LLM access goes through `LlmClient`; which provider and model serve each step is decided
by ADR-0008 (registry, free tiers, runtime switching).

## Alternatives considered
- Single LLM call per job — fewer calls, but not cacheable across profiles and hard-coded
  eligibility logic inside a prompt.
- Embeddings + similarity — good for ranking, bad at hard constraints (region, hours).
  Possible later as an extra ranking signal.

## Consequences
- Up to two calls per job the owner chooses to analyze; the owner controls the spend (ADR-0010).
- Eligibility bugs are fixed in code with tests, not by prompt tweaking.
- The extraction schema becomes a contract (scoring-spec §3); changing it means a new
  prompt version and re-extraction.
