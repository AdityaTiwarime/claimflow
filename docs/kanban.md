# Kanban board

Work on ClaimFlow is planned on a GitHub Projects board (Board layout) with WIP-limited columns:

| Backlog | Ready | In progress (WIP ≤ 2) | Review | Done |
| --- | --- | --- | --- | --- |

## Set it up (5 minutes)

1. On the repo page: **Projects → Link a project → New project → Board**. Name it "ClaimFlow".
2. Rename the columns to **Backlog, Ready, In progress, Review, Done**.
3. Open the column menu on **In progress** and set a **column limit of 2**. That's the Kanban WIP limit.
4. Add the cards below as items, then move each one across as you work on it.

## Cards (the work this repo contains)

| Card | Acceptance check |
| --- | --- |
| Event contracts (ClaimSubmitted, ClaimAdjudicated, topics) | Both services compile against claim-events |
| Adjudication rules engine | `AdjudicationEngineTest`: deductible, coinsurance, coverage, not covered, review threshold, annual max |
| Kafka listener + retries + dead-letter topic | `ClaimListenerKafkaTest` passes on embedded Kafka |
| MySQL schema + seed data (Flyway) | Service starts on an empty database |
| Claims REST API with validation (problem+json) | `ClaimsApiTest`: 202, 400, 404 cases |
| Publish after commit, idempotent decisions | A duplicate decision does not double-count member totals |
| Claim timeline in MongoDB | History panel shows SUBMITTED → SENT → decision |
| Claims console UI | Submit and watch a claim get decided live |
| Selenium UI tests | `mvn verify -Pe2e` green |
| Splunk HEC logging | Events searchable in Splunk by `claimId` |
| Docker Compose stack | `docker compose up` → console on :8081 |
| GitHub Actions CI | build, selenium and docker jobs green |
