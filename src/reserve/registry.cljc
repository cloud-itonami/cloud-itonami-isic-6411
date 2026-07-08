(ns reserve.registry
  "Pure-function reserve-account-opening + settlement-batch-release
  record construction -- an append-only central-banking book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a reserve-account or
  settlement-batch reference number -- every central bank/jurisdiction
  assigns its own reference format. This namespace does NOT invent
  one; it builds a jurisdiction-scoped sequence number and validates
  the record's required fields, the same honest, non-fabricating
  discipline `reserve.facts` uses.

  Two distinctive ground-truth checks, each an HONEST reuse of an
  existing check-family shape, gating a DIFFERENT one of this
  actor's two actuations:

  `reserve-ratio-insufficient?` is the TENTH instance of this fleet's
  MINIMUM-threshold sufficiency check family (`veterinary.registry/
  withdrawal-period-insufficient?`/`funeral.registry/waiting-period-
  elapsed?`/`hospital.registry/observation-period-elapsed?`
  established the first three, temporal; `association.registry/
  continuing-education-hours-insufficient?`/`secondary.registry/
  attendance-hours-insufficient?`/`polling.registry/sample-size-
  insufficient?`/`research.registry/replication-count-insufficient?`
  generalized it to non-temporal ground truths as the fourth through
  seventh; `personalservice.registry/cooling-off-period-not-elapsed?`
  returned to temporal as the eighth; `cultural.registry/practice-
  hours-insufficient?` the ninth), applying the SAME lo-bound
  comparison to a member bank's own recorded reserve ratio against
  its own recorded minimum-required reserve ratio -- a direct,
  natural mapping onto real central-banking practice (US Regulation D
  reserve requirements, ECB minimum reserves under Regulation (EC)
  No 1745/2003, Japan's Reserve Deposit Requirement System Act), not
  claimed as new. Gates only `:actuation/open-reserve-account`.

  `settlement-batch-exceeds-available-reserve-balance?` is the
  ELEVENTH instance of this fleet's MAXIMUM-ceiling check family
  (`facility.registry/occupancy-exceeds-capacity?`/`school.registry/
  class-size-exceeds-maximum?`/`card.registry/settlement-amount-
  exceeds-authorized?`/`recovery.registry/contamination-percentage-
  exceeds-maximum?`/`care.registry/caregiver-workload-exceeds-
  maximum?`/`navigator.registry/eligibility-window-elapsed-exceeds-
  validity?`/`advertising.registry/media-spend-exceeds-authorized-
  budget?`/`nursing`'s/`holdco`'s/`headoffice`'s instances established
  the first ten), applying the SAME ceiling-only comparison to a
  member bank's own proposed settlement-batch amount against its own
  recorded available reserve balance -- a direct, natural mapping
  onto real-time gross settlement (RTGS) liquidity-sufficiency
  practice, not claimed as new. Gates only `:actuation/release-
  settlement-batch`.

  The `correspondent-banking-due-diligence-unresolved?` concept (a
  GENUINELY NEW unconditional-evaluation check, grep-verified absent
  -- no 'correspondent-bank'/'correspondent-due-diligence' concept
  exists anywhere else in this fleet) is a BOOLEAN flag read directly
  off the member's own record by `reserve.governor` -- the same shape
  `photo.governor`'s/`residential.governor`'s unconditional checks
  use, neither of which needed a dedicated registry-level predicate
  either -- so it is NOT defined here.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real reserve/settlement system. It builds the RECORD a
  central bank would keep, not the act of opening the reserve account
  or releasing the settlement batch itself (that is `reserve.
  operation`'s `:actuation/open-reserve-account`/`:actuation/release-
  settlement-batch`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  central bank's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn reserve-ratio-insufficient?
  "Does `member`'s own recorded `:reserve-ratio` fall short of its own
  recorded `:minimum-reserve-ratio-required`? A pure ground-truth
  check against the member's own permanent fields -- no upstream
  comparison needed. The TENTH instance of this fleet's MINIMUM-
  threshold sufficiency family (see ns docstring), not claimed as
  new."
  [{:keys [reserve-ratio minimum-reserve-ratio-required]}]
  (and (number? reserve-ratio) (number? minimum-reserve-ratio-required)
       (< reserve-ratio minimum-reserve-ratio-required)))

(defn settlement-batch-exceeds-available-reserve-balance?
  "Does `member`'s own `:proposed-settlement-amount` exceed its own
  recorded `:available-reserve-balance`? A pure ground-truth check
  against the member's own permanent fields -- no upstream comparison
  needed. The ELEVENTH instance of this fleet's MAXIMUM-ceiling check
  family (see ns docstring), not claimed as new."
  [{:keys [proposed-settlement-amount available-reserve-balance]}]
  (and (number? proposed-settlement-amount) (number? available-reserve-balance)
       (> proposed-settlement-amount available-reserve-balance)))

(defn register-reserve-account-opening
  "Validate + construct the RESERVE-ACCOUNT-OPENING registration
  DRAFT -- the central bank's own act of opening a real reserve
  account for a member bank. Pure function -- does not touch any
  real reserve/settlement system; it builds the RECORD a central bank
  would keep. `reserve.governor` independently re-verifies the
  member's own reserve-ratio ground truth and blocks a double-opening
  for the same member, before this is ever allowed to commit."
  [member-id jurisdiction sequence]
  (when-not (and member-id (not= member-id ""))
    (throw (ex-info "reserve-account-opening: member_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "reserve-account-opening: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "reserve-account-opening: sequence must be >= 0" {})))
  (let [account-number (str (str/upper-case jurisdiction) "-RSV-" (zero-pad sequence 6))
        record {"record_id" account-number
                "kind" "reserve-account-opening-draft"
                "member_id" member-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "account_number" account-number
     "certificate" (unsigned-certificate "ReserveAccountOpening" account-number account-number)}))

(defn register-settlement-batch-release
  "Validate + construct the SETTLEMENT-BATCH-RELEASE registration
  DRAFT -- the central bank's own act of releasing a real interbank
  settlement batch for a member bank. Pure function -- does not touch
  any real reserve/settlement system; it builds the RECORD a central
  bank would keep. `reserve.governor` independently re-verifies the
  member's own available-reserve-balance ground truth and blocks a
  double-release for the same member, before this is ever allowed to
  commit."
  [member-id jurisdiction sequence]
  (when-not (and member-id (not= member-id ""))
    (throw (ex-info "settlement-batch-release: member_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "settlement-batch-release: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "settlement-batch-release: sequence must be >= 0" {})))
  (let [batch-number (str (str/upper-case jurisdiction) "-STL-" (zero-pad sequence 6))
        record {"record_id" batch-number
                "kind" "settlement-batch-release-draft"
                "member_id" member-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "batch_number" batch-number
     "certificate" (unsigned-certificate "SettlementBatchRelease" batch-number batch-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
