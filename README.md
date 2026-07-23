# cloud-itonami-isic-6411

Open Business Blueprint for **ISIC Rev.5 6411**: Central banking.

This repository publishes a central-banking actor -- member-bank
intake, per-jurisdiction reserve-requirement/correspondent-banking
regulatory assessment, correspondent-banking due-diligence screening,
reserve-account opening and interbank settlement-batch release -- as
an OSS business that any qualified, licensed operator can fork,
deploy, run, improve and sell, so a community or independent
professional never surrenders member-bank data and ledgers to a
closed SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419),
[`7310`](https://github.com/cloud-itonami/cloud-itonami-isic-7310),
[`7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320),
[`7210`](https://github.com/cloud-itonami/cloud-itonami-isic-7210),
[`7410`](https://github.com/cloud-itonami/cloud-itonami-isic-7410),
[`8710`](https://github.com/cloud-itonami/cloud-itonami-isic-8710),
[`8541`](https://github.com/cloud-itonami/cloud-itonami-isic-8541),
[`8690`](https://github.com/cloud-itonami/cloud-itonami-isic-8690),
[`9601`](https://github.com/cloud-itonami/cloud-itonami-isic-9601),
[`6420`](https://github.com/cloud-itonami/cloud-itonami-isic-6420),
[`7420`](https://github.com/cloud-itonami/cloud-itonami-isic-7420),
[`9609`](https://github.com/cloud-itonami/cloud-itonami-isic-9609),
[`8550`](https://github.com/cloud-itonami/cloud-itonami-isic-8550),
[`7010`](https://github.com/cloud-itonami/cloud-itonami-isic-7010),
[`8790`](https://github.com/cloud-itonami/cloud-itonami-isic-8790),
[`8542`](https://github.com/cloud-itonami/cloud-itonami-isic-8542)) --
here it is **ReserveOps-LLM ⊣ Central Bank Reserve Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> member-bank-intake summary, normalizing records, and checking
> whether a member's own recorded reserve ratio actually satisfies
> its own recorded minimum requirement -- but it has **no notion of
> which jurisdiction's reserve-requirement/correspondent-banking law
> is official, no license to open a real reserve account or release a
> real interbank settlement batch, and no way to know on its own
> whether a correspondent-banking due-diligence concern has actually
> stayed resolved**. Letting it open an account or release a
> settlement batch directly invites fabricated regulatory citations,
> a reserve account opening on top of an insufficient reserve ratio,
> a settlement batch releasing beyond a member's own available
> balance, and an unresolved due-diligence concern being quietly
> overlooked -- and liability, and systemic-settlement risk, for
> whoever runs it. This project seals the ReserveOps-LLM into a
> single node and wraps it with an independent **Central Bank
> Reserve Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers member-bank intake through reserve-requirement/
correspondent-banking regulatory assessment, correspondent-banking
due-diligence screening, reserve-account opening and interbank
settlement-batch release. It does **not**, by itself, hold any
license required to operate as a central bank in a given
jurisdiction, and it does not claim to. It also does not implement
real-time gross settlement (RTGS) queue management, monetary-policy
rate-setting, or currency issuance itself -- `reserve.registry`'s
checks are pure ground-truth recomputes against the member's own
recorded fields, not a monetary-policy determination. Whoever deploys
and operates a live instance (a licensed central bank or currency
board) supplies any jurisdiction-specific license, the real
settlement-system/RTGS integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch.

### Actuation

**Opening a real reserve account or releasing a real interbank
settlement batch is never autonomous, at any phase, by construction.**
Two independent layers enforce this (`reserve.governor`'s
`:actuation/open-reserve-account`/`:actuation/release-settlement-
batch` high-stakes gate and `reserve.phase`'s phase table, which
never puts either op in any phase's `:auto` set) -- see `reserve.
phase`'s docstring and `test/reserve/phase_test.clj`'s `open-reserve-
account-never-auto-at-any-phase`/`release-settlement-batch-never-
auto-at-any-phase`. The actor may draft, check and recommend; a human
central-bank staff member is always the one who actually opens an
account or releases a batch. Following `nursing`/8710's, `laundry`/
9601's, `holdco`/6420's and `residential`/8790's dual-actuation-on-
one-entity precedent -- the blueprint's own operator-guide names BOTH
acts together ("opening a reserve account or releasing an interbank
settlement batch"), two conceptually distinct real-world acts (an
onboarding act vs. a recurring transactional act) acting on the SAME
member-bank entity -- this build uses the two-member high-stakes set
`#{:actuation/open-reserve-account :actuation/release-settlement-
batch}`, each with its own history collection, sequence counter and
dedicated double-actuation-guard boolean. Both are POSITIVE
actuations (opening/releasing a real record), matching this fleet's
majority actuation shape (`3600`/`6190` are the fleet's two NEGATIVE-
actuation exceptions).

## The core contract

```
member intake + jurisdiction facts (reserve.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ ReserveOps-LLM        │ ─────────────▶ │ Central Bank Reserve          │  (independent system)
   │ (sealed)              │  + citations    │ Governor:                    │
   └───────────────────────┘                 │ spec-basis · evidence-       │
          │                 commit ◀┼ incomplete · correspondent-       │
          │                         │ due-diligence-unresolved            │
    record + ledger        escalate ┼ (unconditional, NEW) · reserve-       │
          │              (ALWAYS for│ ratio-insufficient (MINIMUM-           │
          │               either     │ threshold, reuse) · settlement-         │
          │               actuation) │ exceeds-balance (MAXIMUM-ceiling,         │
          │                          │ reuse) · already-opened ·                  │
          ▼                          │ already-released                             │
      human approval                 └───────────────────────┘
```

**The ReserveOps-LLM never opens a reserve account or releases a
settlement batch the Central Bank Reserve Governor would reject, and
never does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; an unresolved
correspondent-banking due-diligence concern; an insufficient reserve
ratio; an over-balance settlement batch; a double actuation) force
**hold** and *cannot* be approved past; a clean proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a secure vault-servicing
and currency-handling robot manages physical reserve-currency
custody, under the actor, gated by the independent **Central Bank
Reserve Governor**. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Central Bank Reserve Governor, reserve-account/settlement-batch draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `6411`), which names [`kotoba-lang/banking`](https://github.com/kotoba-lang/banking)
(accounts, IBAN, double-entry ledger, clearing) as a required
capability. This R0 implementation does NOT add a real `deps.edn`
dependency on it -- matching `banking`/6419's, `research`/7210's/
`aerospace`/`fab`'s and `holdco`/6420's own precedent, `reserve.*`
implements the specific ground-truth checks a governor needs (plain
numeric reserve-ratio/settlement-balance comparisons and a boolean
correspondent-due-diligence flag) directly, rather than pulling in an
external capability library for a governed-actor scaffold this narrow
in scope.

## Layout

| File | Role |
|---|---|
| `src/reserve/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + SEPARATE reserve-account-opening and settlement-batch-release histories/sequence counters. No dynamically-filed sub-record -- each actuation op acts directly on a pre-seeded member, and the double-actuation guards check dedicated `:reserve-account-opened?`/`:settlement-batch-released?` booleans rather than a `:status` value |
| `src/reserve/registry.cljc` | Reserve-account-opening + settlement-batch-release draft records, plus `reserve-ratio-insufficient?` (honest TENTH MINIMUM-threshold instance) and `settlement-batch-exceeds-available-reserve-balance?` (honest ELEVENTH MAXIMUM-ceiling instance), each gating a DIFFERENT one of this actor's two actuations, not claimed as new |
| `src/reserve/facts.cljc` | Per-jurisdiction reserve-requirement/correspondent-banking catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/reserve/reserveadvisor.cljc` | **ReserveOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/account-verification/due-diligence-screening/reserve-account-opening/settlement-batch-release proposals |
| `src/reserve/corporate_intel.cljc` | Optional cross-reference into [`cloud-itonami-isic-8291`](https://github.com/cloud-itonami/cloud-itonami-isic-8291)'s `:disclosure/query` op -- resolves a correspondent bank's own company profile by name and feeds its sourced `:flags` into `screen-duediligence` via `reserveadvisor/mock-advisor`'s `:corporate-intel-screen` opt (default: no-op, so every existing caller keeps its exact prior behavior). This repo's binary `:correspondent-due-diligence-unresolved?` vocabulary has no middle "incomplete" state, so a real sanctions hit, 8291's own pending-human-review escalation, and a held/misconfigured 8291 query all collapse onto the SAME `true` verdict -- an immediate, un-overridable HARD hold, more conservative than the 3-way repos, matching `cloud-itonami-isic-6419`'s/`6420`'s own binary-collapse precedent |
| `src/reserve/governor.cljc` | **Central Bank Reserve Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · correspondent-due-diligence-unresolved, unconditional evaluation, GENUINELY NEW, the 54th grounding of this discipline · reserve-ratio-insufficient, honest MINIMUM-threshold reuse · settlement-batch-exceeds-available-reserve-balance, honest MAXIMUM-ceiling reuse) + already-account-opened guard + already-settlement-released guard + 1 soft (confidence/actuation gate) |
| `src/reserve/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both actuations always human; member intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/reserve/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/reserve/sim.cljc` | demo driver |
| `test/reserve/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage · corporate-intelligence integration |
| `wasm/reserve_ratio.kotoba` | `.kotoba`-subset port of `reserve.registry/reserve-ratio-insufficient?`, compiled to real WASM via `kotoba wasm emit` and hosted under `kototama.tender` (`test/wasm/reserve_ratio_test.clj`) -- see `wasm/README.md` |

## Business-process coverage (honest)

This actor covers member-bank intake through reserve-requirement/
correspondent-banking regulatory assessment, correspondent-banking
due-diligence screening, reserve-account opening and interbank
settlement-batch release -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Member intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:member/intake`/`:account/verify`) | Real RTGS/settlement-system integration, real monetary-policy rate-setting and currency issuance itself (see `reserve.facts`'s docstring) |
| Correspondent-banking due-diligence screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:duediligence/screen`) | Any monetary-policy judgment itself -- deliberately outside this actor's competence |
| Reserve-account opening, HARD-gated on full evidence, a sufficient reserve ratio and a resolved due-diligence status, plus a double-opening guard (`:actuation/open-reserve-account`) | |
| Settlement-batch release, HARD-gated identically plus a proposed amount within available balance, plus its OWN dedicated double-release guard (`:actuation/release-settlement-batch`) | |
| Immutable audit ledger for every intake/verification/screening/actuation decision | |

Extending coverage is additive: add the next gate (e.g. a real-time
liquidity-gridlock check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world
act" pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`reserve.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `reserve.facts/catalog` --
currently 6 seeded (JPN, USA, GBR, DEU, SAU, IND) out of ~194
jurisdictions worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `reserve.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `ReserveOps-LLM` + `Central Bank Reserve Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, modeled closely on
the sixty-eight prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
