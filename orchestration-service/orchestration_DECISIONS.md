# DECISIONS.md — Orchestration Service

## 1. State machine as an enum with explicit allowed-transition rules

Each `TransferState` declares exactly which states it may legally move to
next (`allowedNextStates()`). Illegal jumps (e.g. `INITIATED -> SETTLED`
directly) are rejected with `IllegalStateTransitionException` before any DB
write. Terminal states (`SETTLED`, `FAILED`, `REVERSED`) have no outgoing
transitions.

## 2. Saga progress is persisted, never held in memory

`TransferOrchestrationService` is stateless — every call reads
`current_state` fresh from Postgres and writes the next state back before
returning. There is no in-memory saga object anywhere. This means process
restarts require zero special recovery logic: "resuming" a transfer after a
crash is just calling `transitionTo()` again, because normal processing
already only reads persisted state. Proven by
`TransferResumptionTest` — see also #5 below on what "crash" actually means
in that test.

## 3. Outbox pattern — write-path proven, publish-path stubbed (NOT live Kafka)

**Accurate status, stated precisely because this is exactly the kind of gap
an interviewer will probe in 30 seconds:**

- **Write-path (done, proven):** every state transition writes to
  `transfers`, `transfer_state_transitions`, and `outbox_events` inside one
  `@Transactional` method. This guarantees the event row can never exist
  without the state change having actually happened, and vice versa —
  the atomicity is real and tested.
- **Publish-path (stubbed, NOT done):** `OutboxPoller.publish()` currently
  only logs the event (`log.info(...)`) and marks the row `PUBLISHED`. There
  is no Kafka broker running, no producer, no topic. Nothing is actually
  being published anywhere outside this service.

Do not describe this as "events are published to Kafka" until a real broker
and producer exist. Correct description until then: "outbox pattern
implemented and proven at the write/atomicity level; publish-path to a real
broker is pending."

## 4. Ledger integration — pending, this is the actual core of Week 2

As of the initial Week 2 build, `PAY_IN`/`PAY_OUT` were state labels only —
no call was made to Ledger's `POST /api/v1/transactions/post`. This has now
been closed: `LedgerClient` (using `WebClient`) calls Ledger when
transitioning into `PAY_IN`.

**Idempotency key propagation:** the key sent to Ledger is derived
deterministically as `transferId + "-" + targetState.name()` (e.g.
`"92d64f7d...-PAY_IN"`), NOT a random UUID generated per call. This matters
specifically because of #2 above — if a crash happens mid-call and the
orchestration service resumes by re-attempting the `PAY_IN` transition, a
random key would cause a duplicate post to the ledger; a deterministic key
means Ledger's own idempotency check (Week 1) catches the retry and returns
the original result instead of double-posting. This is the actual
connection point between Week 1 and Week 2 — Ledger's idempotency guarantee
is only useful to Orchestration if Orchestration calls it with a
predictable key.

## 5. What "crash" means in TransferResumptionTest — scope honesty

The test does not kill a real JVM process. It simulates the *effect* of a
crash by discarding any reference to the service call chain and re-fetching
the transfer through a separate, fresh repository call — proving there is
no in-memory state that a real crash could lose. This is a reasonable proxy
because `TransferOrchestrationService` has no fields holding saga state, but
it is not literally a process-kill test. Worth stating precisely if asked.

## 6. Deferred / not yet implemented

- Real Kafka broker + producer (publish-path is stubbed, see #3)
- Ledger call for `PAY_OUT` (only `PAY_IN` wired so far)
- Compensating transactions / reversal flow (`REVERSED` state exists in the
  enum but nothing yet drives a transfer into it or reverses posted ledger
  entries)
- Screening/AML service integration (`SCREENING`/`PENDING_REVIEW` states
  exist but are driven manually via the API, not by a real screening call)

## 7. Week 3 — Risk/AML screening integration

**Pattern established:** interface (`RiskScreeningClient`) → resilience-wrapped
implementation (`RiskScreeningClientImpl`, using Resilience4j `@Retry` +
`@CircuitBreaker`) → stub vendor (WireMock, in tests) → fallback that fails
SAFE (vendor down = route to manual review, never auto-approve). This same
shape is intended to be reused for every future vendor integration
(pay-in/pay-out, settlement, and any additional AML checks).

**Proven, not assumed:**
- `RiskScreeningClientImplTest` — real HTTP call + JSON parsing against
  WireMock, both approve and manual-review response shapes.
- `RiskScreeningRetryTest` — confirms exactly 3 HTTP attempts occur for one
  failing call (matching `max-attempts: 3`), verified by counting real
  requests WireMock received, not inferred.
- `RiskScreeningCircuitBreakerTest` — confirms the circuit breaker actually
  transitions to `OPEN` after repeated failures, read directly from
  Resilience4j's own `CircuitBreakerRegistry`, not inferred indirectly.
- `TransferScreeningIntegrationTest` — full `runScreening()` flow against
  real Postgres: low-risk auto-advances to `PAY_IN`; high-risk parks in
  `PENDING_REVIEW` with a real row in `review_queue_entries`.
- `ReviewControllerIntegrationTest` — approve/decline endpoints proven over
  real HTTP, correctly driving the transfer to `PAY_IN` or `FAILED`.

**Deliberate design choice — fail-safe fallback:** when the risk vendor is
unreachable (circuit open, or retries exhausted), the fallback returns
`MANUAL_REVIEW`, never an automatic approval. A screening outage must never
silently become "let everything through" — that would be a security hole,
not a resilience feature.

**Explored but NOT integrated:** a second client, `SanctionsScreeningClient`,
calling the real (free, public) U.S. Consolidated Screening List API at
api.trade.gov — a genuine sanctions/denied-party list check, distinct from
the amount/pattern-based risk scoring `RiskScreeningClient` represents. Code
was written to understand the pattern against a live external API, but it
is NOT wired into `runScreening()`, has no tests, and is not part of the
working saga flow. Left as a documented next step, not a completed feature —
do not describe this as "integrated sanctions screening" until it actually
is.

## 8. Deferred / not yet implemented (updated)

- Real Kafka consumer (publish-path proven, nobody subscribes yet)
- `PAY_OUT` Ledger call (only `PAY_IN` wired)
- Compensating transactions / reversal flow
- `SanctionsScreeningClient` integration (see #7 — explored, not wired)
- `runScreening()`'s open-transaction-across-network-call design smell
  (flagged, not yet fixed — the Ledger/DB transaction boundary around the
  vendor HTTP call could be tightened to avoid holding a DB connection open
  during a slow/retrying vendor call)
