(ns reserve.phase
  "Phase 0->3 staged rollout -- the central-banking analog of
  `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- member intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds reserve-account verification +
                                 correspondent-due-diligence screening
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:member/intake` (no capital risk
                                 yet) may auto-commit. `:actuation/
                                 open-reserve-account`/`:actuation/
                                 release-settlement-batch` NEVER
                                 auto-commit, at any phase.

  `:actuation/open-reserve-account`/`:actuation/release-settlement-
  batch` are deliberately ABSENT from every phase's `:auto` set,
  including phase 3 -- a permanent structural fact, not a rollout
  milestone still to come. Opening a real reserve account and
  releasing a real interbank settlement batch are the two real-world
  central-banking acts this actor performs; both are always a human
  central-bank-staff call. `reserve.governor`'s high-stakes gate
  enforces the same invariant independently -- two layers, not one,
  agree on this. `:duediligence/screen` is likewise never auto-
  eligible, at any phase -- the same posture every sibling's
  screening op has. Phase 3's `:auto` set here has only ONE member
  (`:member/intake`) -- this domain has no separate no-capital-risk
  'file' lifecycle distinct from the member itself.

  The decision core is delegated to the safety kernel
  `reserve.kernels.gate` (integer-coded, fail-closed, safe-kotoba
  subset); this namespace keeps the human-readable phase table (the
  documentation and structural-invariant tests read it) and does the
  keyword<->wire-code mapping at the boundary. The kernel's own
  battery and the parity matrix in `reserve.kernels.gate-test` pin the
  two representations together."
  (:require [reserve.kernels.gate :as kernel]))

(def read-ops  #{})
(def write-ops #{:member/intake :account/verify :duediligence/screen
                 :actuation/open-reserve-account :actuation/release-settlement-batch})

;; NOTE the invariant: `:actuation/open-reserve-account`/`:actuation/
;; release-settlement-batch` are members of `write-ops` (governor-
;; gated like any write) but are NEVER members of any phase's `:auto`
;; set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                       :auto #{}}
   1 {:label "assisted-intake"  :writes #{:member/intake}                                          :auto #{}}
   2 {:label "assisted-verify"  :writes #{:member/intake :account/verify :duediligence/screen}      :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:member/intake}}})

(def default-phase 3)

;; ---- kernel wire-code bridges (façade-side, not kernel vocabulary) ----

(defn- op->code
  "Kernel op wire code. `read-ops` is EMPTY in this domain, so nothing
  maps to the reserved read code 0 (if `read-ops` ever gains a member,
  the kernel needs a read pass-through branch too — today code 0 has
  no rights in-kernel, fail-closed). Unknown ops map to 6 (unknown
  write) — the kernel never write-enables it, so an unrecognized op
  fails closed to HOLD exactly as the old set-membership logic did."
  [op]
  (cond
    (= op :member/intake)                       1
    (= op :account/verify)                      2
    (= op :duediligence/screen)                 3
    (= op :actuation/open-reserve-account)      4
    (= op :actuation/release-settlement-batch)  5
    :else                                       6))

(defn- disposition->code [d]
  (cond (= d :commit) 0 (= d :escalate) 1 (= d :hold) 2 :else 2))

(defn- code->disposition [c]
  (if (= c 0) :commit (if (= c 1) :escalate :hold)))

(defn- code->reason [c]
  (if (= c 1) :phase-disabled (if (= c 2) :phase-approval nil)))

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/open-reserve-account`/`:actuation/release-settlement-
    batch` are never auto-eligible at any phase, so they always
    escalate once the governor clears them (or hold if the governor
    doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [p (if (contains? phases phase) phase default-phase)
        op-code (op->code op)
        gov-code (disposition->code governor-disposition)
        d (kernel/phase-disposition p op-code gov-code)
        r (kernel/phase-reason p op-code gov-code)]
    {:disposition (code->disposition d)
     :reason (code->reason r)}))

(defn verdict->disposition
  "Map a Central Bank Reserve Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
