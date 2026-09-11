(ns reserve.kernels.gate
  "Safety kernel for the Central Bank Reserve Governor + phase gate —
  the decision CORE of `reserve.governor/check` and
  `reserve.phase/gate`, extracted into the safe-kotoba subset
  (cloud-itonami kernels discipline, ADR-0016 / superproject
  ADR-2607101200).

  Everything here is integer-coded and stays inside the emit-ready
  vocabulary: `defn`, `def` constants, nested `if`, `=`, `<`, integer
  arithmetic, recursion-free composition through named combinators. No
  keywords, strings, maps, atoms, host interop or I/O — the façades
  (`reserve.governor`, `reserve.phase`) reduce their inputs to
  flags/codes at the boundary and map the result codes back to
  keywords. `.kotoba`/wasm emission is deliberately NOT wired yet
  (owner decision 2026-07-12: ClojureScript + kotoba-datomic first);
  staying inside the subset is what keeps that door open without a
  rewrite.

  Wire codes:
    flag        0 = no, anything else = yes (norm-flag, fail-closed)
    confidence  int x100 (0..100); out-of-range counts as LOW (fail-closed)
    ratio       reserve-ratio / minimum-reserve-ratio-required in the
                SAME integer units the member record itself uses (this
                domain records integer percentage points); insufficient
                iff ratio < minimum — the EXACT comparison
                `reserve.registry/reserve-ratio-insufficient?` makes,
                so kernel and façade can never disagree. `applicable`
                is 0 when the op carries no ratio check OR either
                field is missing/non-numeric (matching the registry's
                `number?` guard — the original treats a missing field
                as no-violation, and the façade computes that guard).
    batch       proposed-settlement-amount / available-reserve-balance
                in the SAME integer currency units the member record
                itself uses; exceeds iff balance < amount (the EXACT
                strict-> comparison `reserve.registry/settlement-batch-
                exceeds-available-reserve-balance?` makes). Same
                `applicable` convention as ratio.
    op          0 reserved read — this domain's `read-ops` set is EMPTY
                  (reserve.phase), so no façade op ever maps to 0; the
                  kernel gives code 0 no write rights and it holds
                  exactly like an unknown op (fail-closed). If
                  `read-ops` ever gains a member, the kernel needs a
                  read pass-through branch FIRST.
                1 :member/intake
                2 :account/verify
                3 :duediligence/screen
                4 :actuation/open-reserve-account
                5 :actuation/release-settlement-batch
                6+ unknown write (never enabled)
    phase       0..3 (anything else: no writes at all — the façade
                normalizes unknown phases to its own default BEFORE the
                kernel, so an out-of-range phase reaching the kernel is
                a bug and fails closed)
    verdict     0 ok/commit-eligible  1 escalate  2 hard-hold
    disposition 0 commit  1 escalate  2 hold
    reason      0 none  1 phase-disabled  2 phase-approval

  Fail-closed direction: every invalid/unknown input degrades toward
  LESS autonomy (hold/escalate), never more. Ops 4 and 5 (the two real
  central-banking actuations: opening a reserve account / releasing an
  interbank settlement batch) are auto-enabled at NO phase — the same
  structural invariant the phase table and the governor's actuation
  gate state independently."
  )

;; --------------------------- combinators ---------------------------

(defn not-flag [a] (if (= a 0) 1 0))
(defn norm-flag
  "Fail-closed flag normalization: only exact 0 counts as 'no'."
  [a]
  (if (= a 0) 0 1))
(defn and2 [a b] (if (= a 1) (if (= b 1) 1 0) 0))
(defn or2 [a b] (if (= a 1) 1 (if (= b 1) 1 0)))
(defn or3 [a b c] (or2 a (or2 b c)))

;; --------------------------- governor core -------------------------

(def confidence-floor-x100 60)

(defn confidence-low
  "1 when the advisor confidence requires a human look. Out-of-range
  values (negative, or above 100) are treated as LOW — an advisor
  reporting impossible confidence is a reason for MORE scrutiny, not
  auto-commit."
  [x100]
  (if (< x100 0)
    1
    (if (< 100 x100)
      1
      (if (< x100 confidence-floor-x100) 1 0))))

(defn reserve-ratio-insufficient
  "1 when the member's own recorded reserve ratio falls STRICTLY short
  of its own recorded minimum requirement (integer units, see ns
  docstring). `applicable` is 0 when the op carries no ratio check or
  either field is missing (norm-flag, fail-closed: any non-0 applies
  the check)."
  [applicable ratio min-required]
  (if (= (norm-flag applicable) 0)
    0
    (if (< ratio min-required) 1 0)))

(defn settlement-batch-exceeds-balance
  "1 when the member's own proposed settlement amount STRICTLY exceeds
  its own recorded available reserve balance (integer units, see ns
  docstring): exceeds iff balance < amount. Same `applicable`
  convention as `reserve-ratio-insufficient`."
  [applicable amount balance]
  (if (= (norm-flag applicable) 0)
    0
    (if (< balance amount) 1 0)))

(defn hard-violation
  "1 when any HARD (human-un-overridable) violation is present:
  spec-basis missing / evidence incomplete / correspondent-banking
  due diligence unresolved / reserve ratio insufficient / settlement
  batch exceeds available reserve balance / reserve account already
  opened / settlement batch already released."
  [spec-missing evidence-incomplete dd-unresolved
   ratio-applicable ratio min-required
   batch-applicable amount balance
   already-opened already-released]
  (or3 (or3 (norm-flag spec-missing)
            (norm-flag evidence-incomplete)
            (norm-flag dd-unresolved))
       (or2 (reserve-ratio-insufficient ratio-applicable ratio min-required)
            (settlement-batch-exceeds-balance batch-applicable amount balance))
       (or2 (norm-flag already-opened)
            (norm-flag already-released))))

(defn verdict-code
  "Governor verdict: 2 hard-hold wins over 1 escalate wins over 0 ok."
  [spec-missing evidence-incomplete dd-unresolved
   ratio-applicable ratio min-required
   batch-applicable amount balance
   already-opened already-released confidence-x100 actuation]
  (if (= 1 (hard-violation spec-missing evidence-incomplete dd-unresolved
                           ratio-applicable ratio min-required
                           batch-applicable amount balance
                           already-opened already-released))
    2
    (if (= 1 (or2 (confidence-low confidence-x100) (norm-flag actuation)))
      1
      0)))

;; ---------------------------- phase core ---------------------------

(defn op-write-enabled
  "1 when `op` may WRITE at `phase` (phase table row, :writes column).
  Code 0 (reserved read — unused, `read-ops` is empty in this domain)
  is enabled nowhere, so it holds fail-closed like an unknown op."
  [phase op]
  (if (= phase 1)
    (if (= op 1) 1 0)
    (if (= phase 2)
      (if (= op 1) 1 (if (= op 2) 1 (if (= op 3) 1 0)))
      (if (= phase 3)
        (if (= op 1)
          1
          (if (= op 2)
            1
            (if (= op 3) 1 (if (= op 4) 1 (if (= op 5) 1 0)))))
        0))))

(defn op-auto-enabled
  "1 when `op` may AUTO-COMMIT at `phase` (phase table row, :auto
  column). Exactly one cell is ever 1: phase 3 x :member/intake.
  Ops 4 and 5 (:actuation/open-reserve-account /
  :actuation/release-settlement-batch) are 0 at every phase —
  permanent structural fact, not a rollout milestone. Op 3
  (:duediligence/screen) is likewise 0 at every phase."
  [phase op]
  (if (= phase 3) (if (= op 1) 1 0) 0))

(defn phase-disposition
  "Resolve the final disposition code from phase, op code and the
  governor's disposition code. Mirrors `reserve.phase/gate`: governor
  hold always wins; a write not enabled at this phase holds (this
  domain has NO read ops, so there is no read pass-through branch —
  code 0 falls into the not-enabled hold like any unknown op); a
  governor-clean write without auto rights escalates; otherwise the
  governor's disposition stands."
  [phase op governor-disposition]
  (if (= governor-disposition 2)
    2
    (if (= 0 (op-write-enabled phase op))
      2
      (if (= governor-disposition 0)
        (if (= 1 (op-auto-enabled phase op)) 0 1)
        governor-disposition))))

(defn phase-reason
  "Reason code companion of `phase-disposition` (same branch order)."
  [phase op governor-disposition]
  (if (= governor-disposition 2)
    0
    (if (= 0 (op-write-enabled phase op))
      1
      (if (= governor-disposition 0)
        (if (= 1 (op-auto-enabled phase op)) 0 2)
        0))))

;; ----------------------------- battery -----------------------------
;; Executable spec, kernels-style: each check returns 1 on pass, the
;; battery sums them, and the test suite locks the sum against
;; `battery-case-count` so a silently-skipped case can't pass review.

(defn check-verdict [spec evid dd rapp ratio rmin bapp amt bal opnd rlsd
                     conf act expected]
  (if (= (verdict-code spec evid dd rapp ratio rmin bapp amt bal opnd rlsd
                       conf act)
         expected)
    1 0))

(defn check-ratio [applicable ratio min-required expected]
  (if (= (reserve-ratio-insufficient applicable ratio min-required) expected) 1 0))

(defn check-batch [applicable amount balance expected]
  (if (= (settlement-batch-exceeds-balance applicable amount balance) expected) 1 0))

(defn check-phase [phase op gov expected-disposition expected-reason]
  (and2 (if (= (phase-disposition phase op gov) expected-disposition) 1 0)
        (if (= (phase-reason phase op gov) expected-reason) 1 0)))

(def battery-case-count 54)

(defn battery-pass-count []
  (+
   ;; -- verdict: each hard check dominates alone (conf 100, act 0)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 100 0 0)
   (check-verdict 1 0 0 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 1 0 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 1 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 0 1 1 2 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 0 0 0 0 1 1000 500 0 0 100 0 2)
   (check-verdict 0 0 0 0 0 0 0 0 0 1 0 100 0 2)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 1 100 0 2)
   ;; -- verdict: hard combos still hard-hold
   (check-verdict 1 0 1 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 0 1 1 2 1 1000 500 0 0 100 0 2)
   (check-verdict 1 1 1 1 1 2 1 1000 500 1 1 100 0 2)
   ;; -- verdict: confidence floor boundary + fail-closed range
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 59 0 1)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 60 0 0)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 0 0 1)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 100 0 0)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 -5 0 1)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 150 0 1)
   ;; -- verdict: actuation always escalates; hard still wins over it
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 100 1 1)
   (check-verdict 1 0 0 0 0 0 0 0 0 0 0 100 1 2)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 40 1 1)
   ;; -- verdict: non-0/1 flags normalize to violation (fail-closed)
   (check-verdict 7 0 0 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 5 0 0 0 0 0 0 0 0 100 0 2)
   (check-verdict 0 0 0 0 0 0 0 0 0 0 0 100 9 1)
   ;; -- ratio: strict-< boundary (insufficient iff ratio < minimum)
   (check-ratio 1 2 2 0)
   (check-ratio 1 1 2 1)
   (check-ratio 1 3 2 0)
   ;; -- ratio: not applicable / fail-closed applicable flag
   (check-ratio 0 1 2 0)
   (check-ratio 7 1 2 1)
   ;; -- batch: strict-> boundary (exceeds iff balance < amount)
   (check-batch 1 500 500 0)
   (check-batch 1 501 500 1)
   (check-batch 1 499 500 0)
   ;; -- batch: not applicable / fail-closed applicable flag
   (check-batch 0 501 500 0)
   (check-batch 9 501 500 1)
   ;; -- phase: governor hold always wins
   (check-phase 3 1 2 2 0)
   ;; -- phase: code 0 (reserved read, read-ops EMPTY here) never writes
   (check-phase 3 0 0 2 1)
   (check-phase 0 0 1 2 1)
   ;; -- phase: write disabled at this phase -> hold, phase-disabled
   (check-phase 0 1 0 2 1)
   (check-phase 1 2 0 2 1)
   (check-phase 1 3 0 2 1)
   (check-phase 2 4 0 2 1)
   (check-phase 2 5 0 2 1)
   (check-phase 3 6 0 2 1)
   ;; -- phase: enabled but not auto -> escalate, phase-approval
   (check-phase 1 1 0 1 2)
   (check-phase 2 2 0 1 2)
   (check-phase 2 3 0 1 2)
   (check-phase 3 2 0 1 2)
   (check-phase 3 3 0 1 2)
   (check-phase 3 4 0 1 2)
   (check-phase 3 5 0 1 2)
   ;; -- phase: the single auto cell
   (check-phase 3 1 0 0 0)
   ;; -- phase: governor escalate passes through an enabled write
   (check-phase 3 1 1 1 0)
   (check-phase 2 1 1 1 0)
   ;; -- phase: out-of-range phases have no writes (fail-closed)
   (check-phase -1 1 0 2 1)
   (check-phase 4 1 0 2 1)))
