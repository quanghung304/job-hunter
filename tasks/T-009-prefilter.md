# T-009: Prefilter rules

Phase: 1 · Depends on: T-002, T-005 · Size: S

## Goal
`Prefilter.evaluate(JobView job, Profile profile)` returns PASS/REJECT with reason codes,
and `AssessmentService` stores the result in `job_assessment`.

## Read first
- `docs/ai/scoring-spec.md` §1, §2 · config-spec §1 (`prefilter.*`, `excludeCompanies`)
- data-model `job_assessment`

## In scope
- Pure `Prefilter` class implementing every rule in §2, collecting all reasons.
- Skill-hit matcher: word-boundary, case-insensitive; aliases for multi-word skills
  (`Spring Boot` ~ `SpringBoot` ~ `spring-boot`; `REST API` ~ `RESTful`); alias table in code.
- `AssessmentService.recordPrefilter(jobId, profileId, profileHash, result)`
  (creates/updates `job_assessment` row for the current score prompt version).
- Re-evaluation when `profile_hash` differs (method `reassessPrefilter(profile)`), used by T-013.

## Acceptance criteria
- [ ] Example profile: "Senior Java Developer" PASS; "iOS Engineer" REJECT `TITLE_EXCLUDED`;
      "Software Engineer" with description mentioning Java and Spring Boot PASS;
      "Data Analyst" with no core skills REJECT `NOT_RELEVANT`;
      on-site "Backend Developer, Munich" REJECT `ONSITE_SOURCE_HINT`;
      on-site "Backend Developer, Turin" PASS.

## Tests
`PrefilterTest` parameterized (≥ 20 rows), `SkillMatcherTest`, `AssessmentServiceIT`.
