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
