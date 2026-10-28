# Logging to Splunk

Both services can send their logs to Splunk's **HTTP Event Collector (HEC)** as JSON, through a
small Logback appender (`claim-events/src/main/java/com/claimflow/logging/SplunkHecAppender.java`).
Every log line written while handling a claim carries its `claimId` (via the logging MDC), so one
search shows a claim's whole journey across both services.

## Run it

```bash
# 1. start the stack with Splunk, and turn on the "splunk" Spring profile
SPRING_PROFILES_ACTIVE=splunk docker compose --profile splunk up --build
# 2. wait ~2 minutes for Splunk to start, then open http://localhost:8000
#    user: admin   password: ChangeMe-Demo-123  (SPLUNK_PASSWORD in docker-compose.yml)
# 3. submit a few claims in the console (http://localhost:8081)
```

## Searches to try (Search & Reporting app)

```
source="claims-service" OR source="adjudication-service"
```
All events from both services.

```
source="adjudication-service" | spath "event.message" | search "event.message"="*DENIED*"
```
Every denied claim, with the reasons.

```
event.claimId="CLM-XXXXXXXX"
```
One claim's full journey: submitted in claims-service, decided in adjudication-service, applied back.

```
source="adjudication-service" "planPays" | rex "claim \S+ (?<status>\w+)" | stats count by status
```
Decisions by status: a mini dashboard.

## Design notes

- **Non-blocking:** events go into a bounded in-memory queue and a background thread posts them in
  batches. If Splunk is down, events are dropped and counted. The application never waits on logging.
- **Off by default:** without the `splunk` profile, logs only go to the console.
- **Tested** against a fake HEC endpoint (`SplunkHecAppenderTest`): JSON shape, token header,
  `claimId` field, and error counting when Splunk answers 503.
