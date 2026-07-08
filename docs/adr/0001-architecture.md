# ADR-0001: ReserveOps-LLM ⊣ Central Bank Reserve Governor architecture

## Status

Accepted. `cloud-itonami-isic-6411` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-6411` publishes an OSS business blueprint for
central banking: currency issuance, reserve-account administration,
and interbank settlement operations for member/commercial banks.
Like every prior actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph
StateGraph + independent Governor + Phase 0→3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across sixty-eight prior siblings, most recently `cloud-itonami-isic-
8542` (cultural education).

## Decision

### Decision 1: entity and op shape

The primary entity is a `member` (a member/commercial bank holding a
reserve account with the central bank), matching the business-
model.md's own Offer language ("reserve-account intake and
administration"). Five ops: `:member/intake` (directory upsert, no
capital risk), `:account/verify` (per-jurisdiction reserve-
requirement/correspondent-banking evidence checklist, never auto),
`:duediligence/screen` (correspondent-banking due-diligence
screening, unconditional-evaluation discipline, never auto),
`:actuation/open-reserve-account` (POSITIVE, high-stakes -- opening a
real reserve account), and `:actuation/release-settlement-batch`
(POSITIVE, high-stakes -- releasing a real interbank settlement
batch).

### Decision 2: dual-actuation shape on one entity

This blueprint's own operator-guide names TWO real-world acts
together ("opening a reserve account or releasing an interbank
settlement batch always requires a human sign-off"), both acting on
the SAME entity (the member bank) -- an onboarding act (opening an
account) and a recurring transactional act (releasing a settlement
batch), conceptually distinct enough to warrant two actuations rather
than folding them into one. Matching `nursing`/8710's, `laundry`/
9601's, `holdco`/6420's and `residential`/8790's dual-actuation-on-
one-entity shape, `high-stakes` is the two-member set `#{:actuation/
open-reserve-account :actuation/release-settlement-batch}`, each with
its own history collection, sequence counter, and dedicated double-
actuation-guard boolean (`:reserve-account-opened?`/`:settlement-
batch-released?`, never a single `:status` value).

### Decision 3: `correspondent-banking-due-diligence-unresolved-violations` -- the 54th unconditional-evaluation screening grounding, a genuinely new concept

Before writing this check, every prior sibling's governor/registry
namespaces were grepped for "correspondent-bank" and "correspondent-
due-diligence" -- zero hits, confirming this is a genuinely new
unconditional-evaluation concept, avoiding the false-precedent-claim
risk `leasing`'s ADR-0001 documents.
`correspondent-banking-due-diligence-unresolved-violations` reuses
the unconditional-evaluation DISCIPLINE (`casualty.governor/
sanctions-violations`'s original fix) for the 54th distinct
application overall, continuing the count established across this
fleet's builds (most recently `cultural.governor/child-performer-
work-permit-unresolved-violations` at 53rd). Grounded in real
correspondent-banking due-diligence law: FATF correspondent-banking
guidance, Basel Committee correspondent-banking due-diligence
standards, and US Bank Secrecy Act 31 CFR §1010.610. Gates
`:duediligence/screen` and both actuations.

### Decision 4: `reserve-ratio-insufficient?` -- an honest tenth MINIMUM-threshold instance, gating account opening only

`veterinary`/`funeral`/`hospital` established the first three
(temporal) instances of this fleet's MINIMUM-threshold sufficiency
check family; `association`/`secondary`/`polling`/`research`
generalized it to non-temporal ground truths as the fourth through
seventh; `personalservice`'s cooling-off-period-not-elapsed? returned
to temporal as the eighth; `cultural`'s practice-hours-insufficient?
the ninth. `reserve.registry/reserve-ratio-insufficient?` is the
TENTH instance, comparing a member's own recorded reserve ratio
against its own recorded minimum requirement -- grounded in US
Regulation D reserve requirements and ECB minimum reserves under
Regulation (EC) No 1745/2003 -- not claimed as new. Gates only
`:actuation/open-reserve-account` (the account-opening-specific
ground truth).

### Decision 5: `settlement-batch-exceeds-available-reserve-balance?` -- an honest eleventh MAXIMUM-ceiling instance, gating settlement release only

`facility`/`school`/`card`/`recovery`/`care`/`navigator`/
`advertising`/`nursing`/`holdco`/`headoffice` established the first
ten instances of this fleet's MAXIMUM-ceiling check family.
`reserve.registry/settlement-batch-exceeds-available-reserve-
balance?` is the ELEVENTH, comparing a member's own proposed
settlement amount against its own recorded available reserve balance
-- a direct, natural mapping onto real-time gross settlement (RTGS)
liquidity-sufficiency practice, not claimed as new. Gates only
`:actuation/release-settlement-batch` (the settlement-specific ground
truth) -- deliberately DIFFERENT from Decision 4's check, since each
actuation has its own distinct real-world ground truth to verify.

### Decision 6: TWO dedicated double-actuation-guard booleans

`:reserve-account-opened?` and `:settlement-batch-released?` are each
a dedicated boolean on the `member` record, never a single `:status`
value -- the same discipline every prior sibling governor's guards
establish, informed by `cloud-itonami-isic-6492`'s real status-
lifecycle bug (ADR-2607071320). Each actuation gets its OWN guard,
history collection and sequence counter, following `nursing`/8710's
precedent exactly.

### Decision 7: Store protocol, MemStore + DatomicStore parity

`reserve.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/reserve/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
The protocol's per-entity accessor is named `member` directly -- not
a Clojure special form, so no `-of` suffix workaround was needed.

### Decision 8: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:member/intake` (no
capital risk). `:account/verify` and `:duediligence/screen` are never
auto-eligible at any phase (matching every sibling's screening/
verification-op posture), and BOTH actuations are permanently
excluded from every phase's `:auto` set -- a structural fact, not a
rollout milestone, enforced by BOTH `reserve.phase` and `reserve.
governor`'s `high-stakes` set independently.

### Decision 9: no bespoke domain capability lib as a code dependency (despite blueprint.edn requiring `:banking`)

This blueprint's own `:itonami.blueprint/required-technologies`
uniquely names `:banking` (pointing at `kotoba-lang/banking`'s
accounts/IBAN/double-entry-ledger/clearing contracts) beyond the
generic stack. This R0 implementation does NOT add `:banking` as a
real `deps.edn` dependency, following the same posture `banking`/
6419 (`:banking`/`:swift`), `research`/`aerospace`/`fab` (`:cae`/
`:eda`) and `holdco`/6420 (`:securities`) already established:
implement the specific ground-truth check a governor needs directly
(here, plain numeric reserve-ratio/settlement-balance comparisons and
a boolean correspondent-due-diligence flag in `reserve.registry`/
`reserve.governor`) rather than pull in an external capability
library for a governed-actor scaffold this narrow in scope.

### Decision 10: mock + LLM advisor pair

`reserve.reserveadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-opening
a reserve account or auto-releasing a settlement batch).

## Alternatives considered

- **A single-actuation shape** (treating account-opening and
  settlement-release as one conceptual act). Rejected: unlike
  `sports`/8541's genuinely ambiguous either/or phrasing, opening an
  account (an onboarding act) and releasing a settlement batch (a
  recurring transactional act) are conceptually distinct real-world
  acts that recur at different points in a member's lifecycle --
  matching `nursing`/8710's own resolution of a similar pattern.
- **A single shared ground-truth check gating both actuations.**
  Rejected: reserve-ratio sufficiency (an eligibility concern at
  account-opening time) and settlement-balance sufficiency (a per-
  transaction liquidity concern) are genuinely different real-world
  facts about a member bank -- collapsing them into one check would
  misrepresent which concern each actuation actually needs verified.
- **Adding a real `kotoba-lang/banking` dependency** since the
  blueprint explicitly names it. Rejected: following `banking`/6419's,
  `research`/`aerospace`/`fab`'s and `holdco`/6420's own precedent,
  the specific ground-truth checks a governor needs can be
  implemented directly without pulling in an external capability
  library for a scaffold this narrow in scope.

## Consequences

- Sixty-ninth actor in this fleet (68 implemented before this
  build).
- Establishes a genuinely NEW unconditional-evaluation-screening
  concept (correspondent-banking-due-diligence-unresolved), grep-
  verified absent from every prior sibling before the claim was
  finalized.
- Documents an honest TENTH instance of the MINIMUM-threshold
  sufficiency check family and an honest ELEVENTH instance of the
  MAXIMUM-ceiling check family, each gating a DIFFERENT one of this
  actor's two actuations, not claimed as new.
- Confirms the dual-actuation-on-one-entity shape generalizes to a
  5th instance in this fleet (nursing, laundry, holdco, residential,
  reserve).
- `MemStore` ‖ `DatomicStore` parity is proven by `test/reserve/
  store_contract_test.clj`.
- `blueprint.edn` required no field-sync fixes -- the `isic-`
  prefixed `:id` and `:required-technologies`/`:optional-
  technologies` already matched the `kotoba-lang/industry` registry's
  own entry for `"6411"` exactly, only the `:maturity` flip itself
  needed adding.

## References

- `orgs/cloud-itonami/cloud-itonami-isic-6411/README.md`
- `orgs/cloud-itonami/cloud-itonami-isic-6411/docs/business-model.md`
- `orgs/kotoba-lang/industry/resources/kotoba/industry/registry.edn` (entry `"6411"`)
