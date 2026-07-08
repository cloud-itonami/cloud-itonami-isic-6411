# Business Model: Central banking

## Classification

- Repository: `cloud-itonami-isic-6411`
- ISIC Rev.5: `6411`
- Activity: central banking -- currency issuance, reserve-account administration, and interbank settlement operations for member/commercial banks
- Social impact: financial inclusion, data sovereignty, transparent audit

## Customer

- national/regional monetary authorities running a modernization program
- currency boards
- community/cooperative reserve-clearing pools

## Offer

- reserve-account intake and administration
- interbank settlement batch proposal
- currency-issuance record-keeping
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per reserve account
- support: monthly retainer with SLA
- migration: import from an incumbent reserve/settlement system
- settlement-batch processing fee

## Trust Controls

- no reserve account is opened and no settlement batch is released without human sign-off
- fabricated reserve-balance evidence forces a hold, not an override
- every settlement path is auditable
- emergency manual override paths remain outside LLM control
- an unresolved correspondent-banking due-diligence concern, an
  insufficient reserve ratio, or an over-balance settlement batch,
  forces a hold, not an override
- reserve-account opening and settlement-batch release are each
  logged and escalated, and cannot be actioned twice for the same
  member: a double-actuation attempt is held off this actor's own
  member facts alone, with no upstream comparison needed

## Central Bank Reserve Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:central-
bank-reserve-governor` -- this is not a generic "review step," it is
the gate the TWO real-world acts this business performs (opening a
real reserve account, releasing a real interbank settlement batch)
must pass. The governor sits between the ReserveOps-LLM and
execution, per the README's Core Contract:

```text
ReserveOps-LLM -> Central Bank Reserve Governor -> hold, proceed, or human approval
```

**Approves**: routine central-banking actions proposed against a
member bank that already has a consented reserve-account evidence
checklist on file, satisfied required evidence, a resolved
correspondent-banking due-diligence status, a sufficient reserve
ratio, and (for settlement) a proposed amount within its own recorded
available reserve balance. These proceed straight to the member
ledger.

**Rejects or escalates**: the governor refuses to let the advisor
open a reserve account or release a settlement batch on its own
authority when any of the following hold -- a fabricated jurisdiction
spec-basis; incomplete evidence; an unresolved correspondent-banking
due-diligence concern; an insufficient reserve ratio; an over-balance
settlement batch; a double-actuation attempt. A clean proposal still
always routes to a human -- neither `:actuation/open-reserve-account`
nor `:actuation/release-settlement-batch` is ever auto-committed, at
any rollout phase.
