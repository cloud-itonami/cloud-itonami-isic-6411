(ns reserve.governor
  "Central Bank Reserve Governor -- the independent compliance layer
  that earns the ReserveOps-LLM the right to commit. The LLM has no
  notion of jurisdictional reserve-requirement/correspondent-banking
  law, whether a member bank's own recorded reserve ratio actually
  satisfies its own recorded minimum requirement, whether a member's
  own proposed settlement batch actually stays within its own
  recorded available reserve balance, whether a correspondent-banking
  due-diligence concern has actually stayed unresolved, or when an
  act stops being a draft and becomes a real-world reserve-account
  opening or interbank settlement-batch release, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD
  -- the central-banking analog of `cloud-itonami-isic-8620`'s
  ClinicGovernor.

  Five checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence, an
  unresolved correspondent-banking due-diligence concern, an
  insufficient reserve ratio, an over-balance settlement batch, or a
  double opening/release). The confidence/actuation gate is SOFT: it
  asks a human to look (low confidence / actuation), and the human
  may approve -- but see `reserve.phase`: for `:stake :actuation/
  open-reserve-account`/`:actuation/release-settlement-batch` (a real
  reserve-account opening or a real interbank settlement-batch
  release) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the account proposal cite an
                                       OFFICIAL source (`reserve.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for either actuation, has the
                                       member actually been assessed
                                       with a full member-identity-
                                       verification-record/reserve-
                                       account-application-record/
                                       correspondent-due-diligence-
                                       record/settlement-authorization-
                                       record evidence checklist on
                                       file?
    3. Correspondent-banking due
       diligence unresolved        -- reported by THIS proposal itself
                                       (a `:duediligence/screen` that
                                       just found an unresolved
                                       concern), or already on file
                                       for the member (`:duediligence/
                                       screen`/either actuation).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op) so the
                                       screening op itself can HARD-
                                       hold on its own finding. A
                                       GENUINELY NEW concept in this
                                       fleet (grep-verified absent --
                                       no dedicated correspondent-
                                       banking-due-diligence CHECK
                                       FUNCTION exists anywhere else in
                                       this fleet), the 54th distinct
                                       application of the
                                       unconditional-evaluation
                                       discipline overall (`casualty.
                                       governor/sanctions-violations`'s
                                       original fix; most recently
                                       `cultural.governor/child-
                                       performer-work-permit-
                                       unresolved-violations` at 53rd).
                                       Grounded in real correspondent-
                                       banking due-diligence law (FATF
                                       correspondent-banking guidance,
                                       Basel Committee correspondent-
                                       banking due-diligence standards,
                                       US Bank Secrecy Act 31 CFR
                                       section 1010.610).
    4. Reserve ratio insufficient  -- for `:actuation/open-reserve-
                                       account`, INDEPENDENTLY
                                       recompute whether the member's
                                       own recorded reserve ratio
                                       falls short of its own recorded
                                       minimum requirement (`reserve.
                                       registry/reserve-ratio-
                                       insufficient?`) -- needs no
                                       proposal inspection at all. An
                                       HONEST reuse of this fleet's
                                       MINIMUM-threshold sufficiency
                                       check family (the TENTH
                                       instance), not claimed as new.
    5. Settlement batch exceeds
       available reserve balance   -- for `:actuation/release-
                                       settlement-batch`,
                                       INDEPENDENTLY recompute whether
                                       the member's own proposed
                                       settlement amount exceeds its
                                       own recorded available reserve
                                       balance (`reserve.registry/
                                       settlement-batch-exceeds-
                                       available-reserve-balance?`) --
                                       needs no proposal inspection at
                                       all. An HONEST reuse of this
                                       fleet's MAXIMUM-ceiling check
                                       family (the ELEVENTH instance),
                                       not claimed as new.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/open-
                                       reserve-account`/`:actuation/
                                       release-settlement-batch` (REAL
                                       central-banking acts) ->
                                       escalate.

  Two more guards, double-actuation prevention, are enforced but NOT
  listed as numbered HARD checks above because they need no upstream
  comparison at all -- `already-account-opened-violations`/`already-
  settlement-released-violations` refuse to open a reserve account/
  release a settlement batch for the SAME member twice, off dedicated
  `:reserve-account-opened?`/`:settlement-batch-released?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320)."
  (:require [reserve.facts :as facts]
            [reserve.kernels.gate :as gate]
            [reserve.registry :as registry]
            [reserve.store :as store]))

(def confidence-floor
  "Documented threshold. The DECIDING copy is
  `reserve.kernels.gate/confidence-floor-x100` (integer x100 in the
  safety kernel); this def is kept for callers/docs and pinned equal
  by `reserve.kernels.gate-test`."
  0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Opening a real reserve account and releasing a real interbank
  settlement batch are the two real-world actuation events this
  actor performs -- a two-member set, matching every prior dual-
  actuation sibling's shape (`nursing`/8710, `laundry`/9601,
  `holdco`/6420, `residential`/8790). Both are POSITIVE actuations
  (opening/releasing a real record), matching this fleet's majority
  actuation shape (`3600`/`6190` remain the only negative-actuation
  exceptions)."
  #{:actuation/open-reserve-account :actuation/release-settlement-batch})

(defn- confidence->x100
  "Host bridge (façade-side, not kernel vocabulary): scale a 0.0..1.0
  advisor confidence to the kernel's integer x100 wire code."
  [c]
  (Math/round (* 100.0 (double c))))

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:account/verify` (or either actuation) proposal with no spec-
  basis citation is a HARD violation -- never invent a jurisdiction's
  reserve-requirement/correspondent-banking requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:account/verify :actuation/open-reserve-account :actuation/release-settlement-batch} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は準備預金基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For either actuation, the jurisdiction's required member-identity-
  verification-record/reserve-account-application-record/
  correspondent-due-diligence-record/settlement-authorization-record
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/open-reserve-account :actuation/release-settlement-batch} op)
    (let [m (store/member st subject)
          account (store/account-of st subject)]
      (when-not (and account
                     (facts/required-evidence-satisfied?
                      (:jurisdiction m) (:checklist account)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(会員金融機関身元確認記録/準備預金口座開設申請記録/コルレス銀行デューデリジェンス記録/決済承認記録等)が充足していない状態での提案"}]))))

(defn- correspondent-banking-due-diligence-unresolved-violations
  "An unresolved correspondent-banking due-diligence concern --
  reported by THIS proposal (e.g. a `:duediligence/screen` that
  itself just found an unresolved concern), or already on file in the
  store for the member (`:duediligence/screen`/either actuation) --
  is a HARD, un-overridable hold. Evaluated UNCONDITIONALLY (not
  scoped to a specific op) so the screening op itself can HARD-hold
  on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (true? (get-in proposal [:value :correspondent-due-diligence-unresolved?]))
        member-id (when (contains? #{:duediligence/screen :actuation/open-reserve-account :actuation/release-settlement-batch} op) subject)
        hit-on-file? (and member-id (true? (:correspondent-due-diligence-unresolved? (store/member st member-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :correspondent-due-diligence-unresolved
        :detail "コルレス銀行デューデリジェンスが未解決の状態での提案は進められない"}])))

(defn- reserve-ratio-insufficient-violations
  "For `:actuation/open-reserve-account`, INDEPENDENTLY recompute
  whether the member's own recorded reserve ratio falls short of its
  own recorded minimum requirement via `reserve.registry/reserve-
  ratio-insufficient?` -- needs no proposal inspection at all."
  [{:keys [op subject]} st]
  (when (= op :actuation/open-reserve-account)
    (let [m (store/member st subject)]
      (when (registry/reserve-ratio-insufficient? m)
        [{:rule :reserve-ratio-insufficient
          :detail (str subject " の準備率(" (:reserve-ratio m)
                      ")が最低所要準備率(" (:minimum-reserve-ratio-required m) ")に満たない")}]))))

(defn- settlement-batch-exceeds-available-reserve-balance-violations
  "For `:actuation/release-settlement-batch`, INDEPENDENTLY recompute
  whether the member's own proposed settlement amount exceeds its own
  recorded available reserve balance via `reserve.registry/
  settlement-batch-exceeds-available-reserve-balance?` -- needs no
  proposal inspection at all."
  [{:keys [op subject]} st]
  (when (= op :actuation/release-settlement-batch)
    (let [m (store/member st subject)]
      (when (registry/settlement-batch-exceeds-available-reserve-balance? m)
        [{:rule :settlement-batch-exceeds-available-reserve-balance
          :detail (str subject " の提案決済額(" (:proposed-settlement-amount m)
                      ")が利用可能準備残高(" (:available-reserve-balance m) ")を超過")}]))))

(defn- already-account-opened-violations
  "For `:actuation/open-reserve-account`, refuses to open a reserve
  account for the SAME member twice, off a dedicated `:reserve-
  account-opened?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/open-reserve-account)
    (when (store/member-already-account-opened? st subject)
      [{:rule :already-account-opened
        :detail (str subject " は既に準備預金口座開設済み")}])))

(defn- already-settlement-released-violations
  "For `:actuation/release-settlement-batch`, refuses to release a
  settlement batch for the SAME member twice, off a dedicated
  `:settlement-batch-released?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/release-settlement-batch)
    (when (store/member-already-settlement-released? st subject)
      [{:rule :already-settlement-released
        :detail (str subject " は既に決済バッチ実行済み")}])))

(defn check
  "Censors a ReserveOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [spec-v  (spec-basis-violations request proposal)
        evid-v  (evidence-incomplete-violations request st)
        dd-v    (correspondent-banking-due-diligence-unresolved-violations request proposal st)
        ratio-v (reserve-ratio-insufficient-violations request st)
        batch-v (settlement-batch-exceeds-available-reserve-balance-violations request st)
        opnd-v  (already-account-opened-violations request st)
        rlsd-v  (already-settlement-released-violations request st)
        hard (into [] (concat spec-v evid-v dd-v ratio-v batch-v opnd-v rlsd-v))
        conf (:confidence proposal 0.0)
        stakes? (boolean (high-stakes (:stake proposal)))
        ;; Numeric bridges: the kernel re-decides both ground-truth
        ;; comparisons from the member's raw integer fields (ratio <
        ;; minimum; balance < amount — the EXACT strict comparisons
        ;; `reserve.registry`'s predicates make, on the same values,
        ;; so kernel and violation list can never disagree). The
        ;; `applicable` flags restate the registry's op scoping AND
        ;; its `number?` guard (a missing field is no-violation,
        ;; exactly as before).
        ratio-op? (= (:op request) :actuation/open-reserve-account)
        batch-op? (= (:op request) :actuation/release-settlement-batch)
        m (when (or ratio-op? batch-op?) (store/member st (:subject request)))
        ratio? (and ratio-op?
                    (number? (:reserve-ratio m))
                    (number? (:minimum-reserve-ratio-required m)))
        batch? (and batch-op?
                    (number? (:proposed-settlement-amount m))
                    (number? (:available-reserve-balance m)))
        ;; The decision itself is delegated to the safety kernel
        ;; (reserve.kernels.gate, integer-coded fail-closed core); this
        ;; façade only gathers evidence (violation lists with
        ;; human-readable details) and maps codes back to keywords.
        ;; Kernel is stricter than the old inline logic on ONE case by
        ;; design: an out-of-range confidence (< 0 or > 1.0) now
        ;; escalates instead of counting as high confidence.
        code (gate/verdict-code (if (seq spec-v) 1 0)
                                (if (seq evid-v) 1 0)
                                (if (seq dd-v) 1 0)
                                (if ratio? 1 0)
                                (if ratio? (:reserve-ratio m) 0)
                                (if ratio? (:minimum-reserve-ratio-required m) 0)
                                (if batch? 1 0)
                                (if batch? (:proposed-settlement-amount m) 0)
                                (if batch? (:available-reserve-balance m) 0)
                                (if (seq opnd-v) 1 0)
                                (if (seq rlsd-v) 1 0)
                                (confidence->x100 conf)
                                (if stakes? 1 0))]
    {:ok?          (= 0 code)
     :violations   hard
     :confidence   conf
     :hard?        (= 2 code)
     :escalate?    (= 1 code)
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
