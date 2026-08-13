(ns reserve.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300):
  this repo previously had NO demo page and no generator at all. This
  namespace drives the REAL actor stack (`reserve.operation` ->
  `reserve.governor` -> `reserve.store`, through `langgraph.graph/run*`)
  and renders whatever that run actually produced. Nothing on the page is
  typed by hand: member rows come from `reserve.store/all-members`, the
  phase gate rows are produced by CALLING `reserve.phase/gate`, the HARD
  check rows are grouped out of the real `:governor-hold` facts, the
  jurisdiction rows come from `reserve.facts/coverage` over the
  jurisdictions the seeded members actually carry, and the registry
  drafts are the records `reserve.registry` actually minted.

  Scenario shape (adapted from this repo's own `reserve.sim` demo driver,
  `clojure -M:dev:run`, which was run BEFORE this file was written and
  confirmed to produce a sensible ledger against the real seeded member
  ids `member-1`..`member-6`):

    - member-1 (Sato Trust Bank, JPN) walks a full clean lifecycle --
      intake (auto-commit at phase 3, no capital risk), reserve-account
      evidence verification (escalates, approved), correspondent-banking
      due-diligence screening (escalates, approved), reserve-account
      opening and interbank settlement-batch release (BOTH always
      escalate at every phase, both approved).
    - member-6 (GBR) has its evidence verification REJECTED by the human
      approver -- the SOFT gate's other outcome, to contrast with a HARD
      hold that never reaches a human at all.
    - seven distinct HARD governor rules actually fire (see
      `min-distinct-hard-rules`); none of them reaches a human, which
      `-main` verifies structurally rather than asserting in prose.

  Build-time invariants (`-main` THROWS, it does not warn):
    1. the run must produce at least one real `:governor-hold` fact;
    2. it must exercise at least `min-distinct-hard-rules` DISTINCT HARD
       rules -- an evidence floor, so a governor change that silently
       stops firing a check fails the build instead of quietly shrinking
       the page;
    3. no run that HARD-held may also have asked a human (no
       `:approval-requested` in the same run's audit) -- the
       'HARD holds never reach a human' claim is measured, not asserted;
    4. every HARD-check row rendered must trace to a rule present in the
       ledger (the section cannot outlive the run that justified it).

  Deterministic: no timestamps, no random ids, byte-identical across
  reruns against the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [reserve.store :as store]
            [reserve.facts :as facts]
            [reserve.phase :as phase]
            [reserve.governor :as governor]
            [reserve.operation :as op]
            [reserve.reserveadvisor :as reserveadvisor]
            [reserve.corporate-intel :as corporate-intel]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :central-bank-staff :phase 3})

(def min-distinct-hard-rules
  "Evidence floor for invariant 2. The scenario below drives seven
  distinct HARD governor rules; if a governor/store/advisor change makes
  fewer of them fire, the build fails rather than rendering a quietly
  thinner page. Raise this when the scenario grows; never lower it to
  make a build pass."
  7)

;; ----------------------------- the real run -----------------------------

(defn- exec!
  "One supervised operation = one langgraph run. Returns the run result;
  `runs` accumulates them so the renderer can read each run's own audit
  channel (the approval facts never reach the store ledger -- see
  `reserve.operation`'s `:commit` node, which appends only the commit
  fact)."
  [runs actor tid request]
  (let [r (g/run* actor {:request request :context operator} {:thread-id tid})]
    (swap! runs conj (assoc (select-keys request [:op :subject]) :thread tid
                            :audit (get-in r [:state :audit])
                            :disposition (get-in r [:state :disposition])))
    r))

(defn- resume!
  "Resume a run paused at `:request-approval` with a human decision."
  [runs actor tid status by]
  (let [r (g/run* actor {:approval {:status status :by by}}
                  {:thread-id tid :resume? true})]
    (swap! runs conj {:thread tid :resume? true
                      :audit (get-in r [:state :audit])
                      :disposition (get-in r [:state :disposition])})
    r))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach. Returns `{:db .. :runs [..]}` -- every field the
  renderer reads below is real governor/store/langgraph output.

  The last screening is driven by a SECOND actor whose advisor has
  `reserve.corporate-intel/screen-company` wired in, so member-6's HARD
  hold is produced by an actual cross-reference into
  `cloud-itonami-isic-8291` (whose own DisclosureGovernor escalates the
  sanctions hit to ITS OWN reviewer -- reported here as
  `:pending-human-review?`, which this domain's two-valued vocabulary
  must treat as unresolved), not by a local flag."
  []
  (let [db (store/seed-db)
        runs (atom [])
        actor (op/build db)
        ;; second actor, same SSoT, advisor wired to the 8291 actor
        intel-actor (corporate-intel/build)
        intel-op (op/build db {:advisor (reserveadvisor/mock-advisor
                                         {:corporate-intel-screen
                                          #(corporate-intel/screen-company % {:actor intel-actor})})})]

    ;; --- member-1: full clean lifecycle -------------------------------
    (exec! runs actor "m1-intake"
           {:op :member/intake :subject "member-1"
            :patch {:id "member-1" :member-name "Sato Trust Bank"}})

    (exec! runs actor "m1-verify" {:op :account/verify :subject "member-1"})
    (resume! runs actor "m1-verify" :approved "op-1")

    (exec! runs actor "m1-dd" {:op :duediligence/screen :subject "member-1"})
    (resume! runs actor "m1-dd" :approved "op-1")

    (exec! runs actor "m1-open" {:op :actuation/open-reserve-account :subject "member-1"})
    (resume! runs actor "m1-open" :approved "op-1")

    (exec! runs actor "m1-release" {:op :actuation/release-settlement-batch :subject "member-1"})
    (resume! runs actor "m1-release" :approved "op-1")

    ;; --- HARD: fabricated jurisdiction spec-basis ---------------------
    (exec! runs actor "m2-verify" {:op :account/verify :subject "member-2" :no-spec? true})

    ;; --- HARD: actuation before the evidence checklist is on file -----
    (exec! runs actor "m2-open" {:op :actuation/open-reserve-account :subject "member-2"})

    ;; --- HARD: member's own reserve ratio below its own minimum -------
    (exec! runs actor "m3-verify" {:op :account/verify :subject "member-3"})
    (resume! runs actor "m3-verify" :approved "op-1")
    (exec! runs actor "m3-open" {:op :actuation/open-reserve-account :subject "member-3"})

    ;; --- HARD: unresolved correspondent-banking due diligence (local) -
    (exec! runs actor "m4-dd" {:op :duediligence/screen :subject "member-4"})

    ;; --- HARD: settlement batch over the member's own balance ---------
    (exec! runs actor "m5-verify" {:op :account/verify :subject "member-5"})
    (resume! runs actor "m5-verify" :approved "op-1")
    (exec! runs actor "m5-release" {:op :actuation/release-settlement-batch :subject "member-5"})

    ;; --- HARD: double opening / double release of the same member -----
    (exec! runs actor "m1-open-again" {:op :actuation/open-reserve-account :subject "member-1"})
    (exec! runs actor "m1-release-again" {:op :actuation/release-settlement-batch :subject "member-1"})

    ;; --- SOFT: clean proposal, human approver says NO -----------------
    (exec! runs actor "m6-verify" {:op :account/verify :subject "member-6"})
    (resume! runs actor "m6-verify" :rejected "op-1")

    ;; --- HARD: due diligence unresolved via the 8291 cross-reference --
    (exec! runs intel-op "m6-dd" {:op :duediligence/screen :subject "member-6"})

    {:db db :runs @runs}))

;; ----------------------------- helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kname [k] (if (keyword? k) (name k) (str k)))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- code* [v] (str "<code>" (esc v) "</code>"))

(defn- rows [xs] (str/join "\n" xs))

;; ----------------------------- derived views -----------------------------

(defn- hard-holds [ledger] (filter #(= :governor-hold (:t %)) ledger))

(defn- hard-rule-groups
  "Groups the run's REAL `:governor-hold` facts by violated rule, in
  first-seen ledger order. Nothing here is a literal: if a rule stops
  firing its row disappears (and `-main`'s evidence floor fails)."
  [ledger]
  (let [vs (for [f (hard-holds ledger)
                 v (:violations f)]
             (assoc v :subject (:subject f) :op (:op f)))]
    (->> vs
         (reduce (fn [acc {:keys [rule detail subject op]}]
                   (if (contains? acc rule)
                     (-> acc
                         (update-in [rule :count] inc)
                         (update-in [rule :subjects] conj subject)
                         (update-in [rule :ops] conj op))
                     (assoc acc rule {:rule rule :detail detail :count 1
                                      :order (count acc)
                                      :subjects [subject] :ops [op]})))
                 {})
         vals
         (sort-by :order))))

;; --- store approver attribution, DERIVED (never asserted) -------------

(defn- approver-in
  "Any value stored under an `approved-by` / `approved_by` key, whatever
  the key type (this store mixes keyword-keyed maps with the registry's
  string-keyed records)."
  [m]
  (when (map? m)
    (some (fn [[k v]]
            (when (and v (contains? #{"approved-by" "approved_by"} (kname k))) v))
          m)))

(defn- registers-for
  "The SSoT registers a committed op of this kind actually writes, read
  back through the Store protocol. This is the probe: whether the
  approver survives is decided by what is IN these maps at render time,
  not by anything this namespace claims."
  [db op subject]
  (case op
    :member/intake       [(store/member db subject)]
    :account/verify      [(store/account-of db subject)]
    :duediligence/screen [(store/duediligence-screen-of db subject)]
    :actuation/open-reserve-account
    (into [(store/member db subject)]
          (filter #(= subject (get % "member_id")) (store/reserve-account-history db)))
    :actuation/release-settlement-batch
    (into [(store/member db subject)]
          (filter #(= subject (get % "member_id")) (store/settlement-batch-history db)))
    []))

(defn- register-names [op]
  (case op
    :member/intake       "member record"
    :account/verify      "account register (:account/set payload)"
    :duediligence/screen "due-diligence register (:duediligence/set payload)"
    :actuation/open-reserve-account     "member record + reserve-account-opening draft"
    :actuation/release-settlement-batch "member record + settlement-batch-release draft"
    ""))

(defn- approval-trail
  "One row per human decision the run actually took. `:decided-by` is
  read off the run's own audit fact; `:retained` is read back OUT of the
  store. The two are reported separately on purpose -- a reader must be
  able to tell 'nobody approved' from 'the store dropped it'."
  [db runs]
  (let [by-thread (into {} (for [r runs :when (not (:resume? r))]
                             [(:thread r) r]))]
    (for [r runs
          :when (:resume? r)
          :let [f (last (filter #(#{:approval-granted :approval-rejected} (:t %)) (:audit r)))
                src (get by-thread (:thread r))
                o (:op src)
                s (:subject src)]
          :when f]
      {:thread (:thread r)
       :op o
       :subject s
       :outcome (:t f)
       :decided-by (:by f)
       :register (register-names o)
       :retained (when (= :approval-granted (:t f))
                   (some approver-in (registers-for db o s)))})))

(defn- cross-reference-note
  "The `cloud-itonami-isic-8291` cross-reference sentence is only
  emitted if that hold is ACTUALLY in this run's ledger. If the
  cross-reference stops finding the correspondent bank's sanctions hit,
  the claim disappears with it instead of describing a hold the page no
  longer shows (member-4's local flag would keep the RULE row alive, so
  the rule count alone cannot be trusted to notice)."
  [ledger member-id]
  (when (some (fn [f]
                (and (= :governor-hold (:t f))
                     (= member-id (:subject f))
                     (some #{:correspondent-due-diligence-unresolved} (:basis f))))
              ledger)
    (str " The <code>" (esc member-id) "</code> due-diligence hold was produced by an actual cross-reference "
         "into <code>cloud-itonami-isic-8291</code>, whose own DisclosureGovernor escalated the correspondent "
         "bank's sanctions hit to ITS OWN reviewer — a pending review, which this domain's two-valued "
         "vocabulary must treat as unresolved rather than as a clear.")))

(defn- approver-disclosure
  "The attribution sentence, DERIVED from the trail this run actually
  produced rather than stated as prose. Which registers keep the
  approver is a property of `reserve.store/commit-record!`, not of this
  page: the actuation branches re-mint their record from the member id
  and jurisdiction and never read `:payload`, while `:account/set` and
  `:duediligence/set` persist the payload whole. If that is changed,
  this sentence changes with it -- a hard-coded claim here would have
  become a lie the moment someone fixed the store."
  [trail]
  (let [granted (filter #(= :approval-granted (:outcome %)) trail)
        ops (fn [xs] (str/join ", " (map #(code* %) (distinct (map :op xs)))))
        kept (filter :retained granted)
        lost (remove :retained granted)]
    (cond
      (empty? granted)
      "No approval was granted in this run, so there is nothing to attribute."

      (empty? lost)
      (str "Measured on this run: the approver survived on every register written by "
           (ops kept) ".")

      (empty? kept)
      (str "Measured on this run: the approver was dropped by every register written by "
           (ops lost) " — it exists only on the audit fact.")

      :else
      (str "Measured on this run: the approver survived on the payload-backed registers written by "
           (ops kept) ", and was dropped by " (ops lost)
           " — those records are re-minted by <code>reserve.registry</code> from the member id and "
           "jurisdiction alone, so the <code>:payload</code> carrying <code>approved-by</code> is never read."))))

;; ----------------------------- rendering -----------------------------

(defn- last-fact-for [ledger member-id]
  (last (filter #(= (:subject %) member-id) ledger)))

(defn- status-cell [ledger member-id]
  (let [f (last-fact-for ledger member-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map kname (:basis f)))) "</span>")
      (= :approval-rejected (:t f))
      "<span class=\"warn\">approver rejected</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- yn [b klass-true klass-false t f]
  (if b (str "<span class=\"" klass-true "\">" t "</span>")
      (str "<span class=\"" klass-false "\">" f "</span>")))

(defn- member-row [ledger {:keys [id member-name jurisdiction reserve-ratio
                                  minimum-reserve-ratio-required
                                  proposed-settlement-amount available-reserve-balance
                                  correspondent-due-diligence-unresolved?
                                  reserve-account-opened? settlement-batch-released?
                                  account-number batch-number]}]
  (row (code* id)
       (esc member-name)
       (esc jurisdiction)
       (str "<span class=\"num\">" (esc reserve-ratio) " / " (esc minimum-reserve-ratio-required) "</span>")
       (str "<span class=\"num\">" (esc proposed-settlement-amount) " / " (esc available-reserve-balance) "</span>")
       (yn correspondent-due-diligence-unresolved? "critical" "ok" "unresolved" "resolved")
       (if reserve-account-opened?
         (str "<span class=\"ok\">opened &middot; " (code* account-number) "</span>")
         "<span class=\"muted\">not opened</span>")
       (if settlement-batch-released?
         (str "<span class=\"ok\">released &middot; " (code* batch-number) "</span>")
         "<span class=\"muted\">not released</span>")
       (status-cell ledger id)))

(defn- phase-row [[p {:keys [label writes auto]}]]
  (row (str "<span class=\"num\">" p "</span>")
       (esc label)
       (if (seq writes)
         (str/join " " (map #(code* %) (sort-by str writes)))
         "<span class=\"muted\">none</span>")
       (if (seq auto)
         (str/join " " (map #(code* %) (sort-by str auto)))
         "<span class=\"muted\">none &middot; every write needs a human</span>")))

(defn- op-gate-row
  "Calls the REAL `reserve.phase/gate` at the default phase for both a
  governor-clean and a governor-held base disposition, so the two
  right-hand columns are the gate's own answers."
  [op]
  (let [clean (phase/gate phase/default-phase {:op op} :commit)
        held (phase/gate phase/default-phase {:op op} :hold)
        stakes? (contains? governor/high-stakes op)]
    (row (code* op)
         (case (:disposition clean)
           :commit "<span class=\"ok\">auto-commit when governor-clean</span>"
           :escalate (str "<span class=\"warn\">human approval</span>"
                          (when (:reason clean) (str " <span class=\"muted\">&middot; " (esc (kname (:reason clean))) "</span>")))
           (str "<span class=\"critical\">hold</span>"
                (when (:reason clean) (str " <span class=\"muted\">&middot; " (esc (kname (:reason clean))) "</span>"))))
         (str "<span class=\"critical\">" (esc (kname (:disposition held))) "</span>")
         (if stakes?
           "<span class=\"critical\">yes &middot; never auto at any phase</span>"
           "<span class=\"muted\">no</span>"))))

(defn- hard-check-row [{:keys [rule detail count subjects]}]
  (row (code* rule)
       (str "<span class=\"num\">" count "</span>")
       (str/join " " (map #(code* %) (distinct subjects)))
       (esc detail)))

(defn- jurisdiction-row [iso3]
  (let [sb (facts/spec-basis iso3)]
    (row (code* iso3)
         (if sb (esc (:name sb)) "<span class=\"critical\">no spec-basis on file</span>")
         (if sb (esc (:owner-authority sb)) "<span class=\"muted\">&mdash;</span>")
         (if sb (esc (:legal-basis sb)) "<span class=\"muted\">&mdash;</span>")
         (if sb (str "<span class=\"num\">" (count (:required-evidence sb)) "</span>")
             "<span class=\"num\">0</span>")
         (if sb (str "<code>" (esc (:provenance sb)) "</code>")
             "<span class=\"critical\">proposals citing it are HARD-held</span>"))))

(defn- approval-row [{:keys [op subject outcome decided-by register retained]}]
  (row (code* op)
       (code* subject)
       (if (= :approval-granted outcome)
         "<span class=\"ok\">approved</span>"
         "<span class=\"warn\">rejected</span>")
       (if decided-by
         (str "<code>" (esc decided-by) "</code>")
         "<span class=\"warn\">not recorded on the audit fact</span>")
       (esc register)
       (cond
         (not= :approval-granted outcome) "<span class=\"muted\">n/a &middot; nothing committed</span>"
         retained (str "<span class=\"ok\">retained &middot; <code>" (esc retained) "</code></span>")
         :else (str "<span class=\"warn\">audit only &mdash; not retained in record</span>"))))

(defn- draft-row [r]
  (row (code* (get r "record_id"))
       (esc (get r "kind"))
       (code* (get r "member_id"))
       (esc (get r "jurisdiction"))
       (yn (get r "immutable") "ok" "warn" "immutable" "mutable")))

(defn- ledger-row [{:keys [t op subject basis summary]}]
  (row (case t
         :committed "<span class=\"ok\">committed</span>"
         :governor-hold "<span class=\"critical\">governor-hold</span>"
         :approval-rejected "<span class=\"warn\">approval-rejected</span>"
         (str "<span class=\"muted\">" (esc (kname t)) "</span>"))
       (code* (or op :n-a))
       (code* subject)
       (esc (str/join ", " (map kname basis)))
       (esc (or summary ""))))

(defn- section [title lede headers body-rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" body-rows "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn render
  "Renders the whole document from a `{:db .. :runs ..}` produced by
  `run-demo!` (or any other real scenario)."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        members (store/all-members db)
        used-jurisdictions (vec (distinct (map :jurisdiction members)))
        cov (facts/coverage used-jurisdictions)
        groups (hard-rule-groups ledger)
        trail (approval-trail db runs)
        n-hard (count (hard-holds ledger))]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-6411 &middot; central-bank reserve &amp; settlement operator console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Central banking (ISIC 6411) — Reserve &amp; Settlement Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · reserve-account opening and settlement-batch release are always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Member banks (SSoT snapshot)"
      (str "Every row is <code>reserve.store/all-members</code> after the run — "
           "build-time-generated by <code>reserve.render-html</code> "
           "(<code>clojure -M:dev:render-html</code>), never hand-written. "
           "Reserve ratio and settlement figures are the member's OWN recorded fields; "
           "the governor recomputes both independently before either actuation.")
      ["Member" "Name" "Jurisdiction" "Reserve ratio / min required"
       "Proposed settlement / available balance" "Correspondent DD"
       "Reserve account" "Settlement batch" "Last op status"]
      (rows (map (partial member-row ledger) members)))

     (section
      "Action gate — what the phase gate actually answers"
      (str "The two right-hand columns are produced by CALLING "
           "<code>reserve.phase/gate</code> at phase " phase/default-phase
           " (<code>" (esc (:label (get phase/phases phase/default-phase))) "</code>) with a clean and with a held "
           "governor verdict. A HARD governor violation always stays a hold — the phase gate can only add caution, "
           "never remove it. The right-most column is membership of "
           "<code>reserve.governor/high-stakes</code>: two independent layers agree that a real "
           "reserve-account opening and a real interbank settlement-batch release are always a human call.")
      ["Op" "Governor clean →" "Governor HARD →" "High-stakes actuation"]
      (rows (map op-gate-row (sort-by str phase/write-ops))))

     (section
      "Rollout phases"
      (str "Read straight out of <code>reserve.phase/phases</code>. "
           "Note that <code>:actuation/open-reserve-account</code> and "
           "<code>:actuation/release-settlement-batch</code> appear in no phase's auto set, "
           "including the last one — a permanent structural fact, not a milestone still to come.")
      ["Phase" "Label" "Writes allowed" "May auto-commit when governor-clean"]
      (rows (map phase-row (sort-by key phase/phases))))

     (section
      (str "HARD governor holds fired by this run (" n-hard " holds, " (count groups) " distinct rules)")
      (str "Grouped out of the run's real <code>:governor-hold</code> facts. "
           "A HARD violation is un-overridable: it never reaches a human approver at all, "
           "which the build verifies structurally (no <code>:approval-requested</code> in any held run's audit) "
           "rather than claiming here."
           (cross-reference-note ledger "member-6"))
      ["Rule" "Times fired" "Members" "Detail (from the governor)"]
      (rows (map hard-check-row groups)))

     (section
      "Jurisdiction spec-basis coverage"
      (str "<code>reserve.facts/coverage</code> over the jurisdictions the seeded members actually carry: "
           "<span class=\"num\">" (:covered cov) "</span> of <span class=\"num\">" (:requested cov)
           "</span> covered"
           (when (seq (:missing-jurisdictions cov))
             (str ", missing " (str/join ", " (map #(code* %) (:missing-jurisdictions cov)))))
           ". " (esc (:note cov)))
      ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence" "Provenance"]
      (rows (map jurisdiction-row (sort used-jurisdictions))))

     (section
      "Human approval trail — and what the store actually kept"
      (str "Left half is the run's own audit fact; the right-most column is read back OUT of the store "
           "at render time by looking for an <code>approved-by</code> key in the registers that op writes. "
           "It is derived, not declared: if the store is later changed to keep the approver on a register it "
           "currently drops, this column starts saying <em>retained</em> without anyone editing this page. "
           (approver-disclosure trail))
      ["Op" "Member" "Decision" "Decided by (audit fact)" "Store register inspected" "Approver in the record?"]
      (rows (map approval-row trail)))

     (section
      "Registry drafts minted by this run"
      (str "The append-only book-of-record drafts <code>reserve.registry</code> actually produced. "
           "Every one is a DRAFT with an unsigned certificate — signing is the central bank's own act, "
           "not this actor's.")
      ["Record id" "Kind" "Member" "Jurisdiction" "Mutability"]
      (rows (concat (map draft-row (store/reserve-account-history db))
                    (map draft-row (store/settlement-batch-history db)))))

     (section
      (str "Audit ledger (this run — " (count ledger) " facts)")
      "Append-only decision-fact log: every commit, every hold, in the order the actor produced them."
      ["Fact" "Op" "Member" "Basis" "Summary"]
      (rows (map ledger-row ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>reserve.render-html</code> from a real "
     "<code>reserve.operation</code> → <code>reserve.governor</code> → <code>reserve.store</code> run. "
     "Deterministic and timestamp-free: two consecutive builds are byte-identical.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- build-time invariants -----------------------------

(defn- assert-real-holds!
  "Invariants 1-4. Throws; never warns. A generator that renders a page
  with no HARD hold in it has not demonstrated the thing this repo
  exists to demonstrate, and a page whose HARD-check section outlived
  the run that justified it is a lie with a table around it."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        holds (hard-holds ledger)
        rules (into #{} (mapcat #(map :rule (:violations %)) holds))
        groups (hard-rule-groups ledger)]
    (println "HARD-HOLDS\t" (count holds))
    (println "DISTINCT-HARD-RULES\t" (count rules))
    (when (zero? (count holds))
      (throw (ex-info "render-html: the run produced ZERO :governor-hold facts -- refusing to write a console that shows no HARD hold"
                      {:ledger-facts (count ledger)})))
    (when (< (count rules) min-distinct-hard-rules)
      (throw (ex-info "render-html: fewer distinct HARD rules fired than the evidence floor requires"
                      {:fired (vec (sort-by str rules))
                       :count (count rules)
                       :floor min-distinct-hard-rules})))
    ;; invariant 3 -- a HARD hold must never have asked a human
    (doseq [r runs]
      (let [ts (into #{} (map :t (:audit r)))]
        (when (and (contains? ts :governor-hold) (contains? ts :approval-requested))
          (throw (ex-info "render-html: a run that HARD-held ALSO escalated to a human approver"
                          {:thread (:thread r) :op (:op r) :subject (:subject r)})))))
    ;; invariant 4 -- no rendered HARD-check row without a ledger rule
    (doseq [{:keys [rule]} groups]
      (when-not (contains? rules rule)
        (throw (ex-info "render-html: HARD-check row has no backing ledger fact"
                        {:rule rule}))))
    ;; invariant 5 -- the SOFT gate must have shown BOTH of its outcomes.
    ;; Without this floor an empty approval trail renders as a legitimate-
    ;; looking (but empty) table, and the derived attribution sentence
    ;; degrades to "nothing to attribute" -- silence that reads like a pass.
    (let [trail (approval-trail db runs)
          outcomes (frequencies (map :outcome trail))]
      (println "APPROVALS-GRANTED\t" (get outcomes :approval-granted 0))
      (println "APPROVALS-REJECTED\t" (get outcomes :approval-rejected 0))
      (when (zero? (get outcomes :approval-granted 0))
        (throw (ex-info "render-html: no approval was GRANTED -- the approval trail and the approver-attribution probe would both be vacuous"
                        {:trail (count trail)})))
      (when (zero? (get outcomes :approval-rejected 0))
        (throw (ex-info "render-html: no approval was REJECTED -- the SOFT gate's other outcome was never demonstrated"
                        {:trail (count trail)}))))
    {:holds (count holds) :rules (vec (sort-by str rules))}))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        {:keys [holds rules]} (assert-real-holds! result)
        html (render result)]
    (spit out html)
    (println "wrote" out
             (str "(" (count (store/ledger (:db result))) " ledger facts, "
                  holds " HARD holds over " (count rules) " distinct rules, "
                  (count (store/reserve-account-history (:db result))) " reserve-account drafts, "
                  (count (store/settlement-batch-history (:db result))) " settlement-batch drafts, "
                  (count html) " bytes)"))
    (println "HARD-RULES\t" (str/join " " (map str rules)))))
