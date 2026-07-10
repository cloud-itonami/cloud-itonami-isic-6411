(ns reserve.store
  "SSoT for the central-banking actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/reserve/store_contract_test.clj), which is the whole point:
  the actor, the Central Bank Reserve Governor and the audit ledger
  never know which SSoT they run on.

  Like every prior dual-actuation sibling (`nursing`/8710, `laundry`/
  9601, `holdco`/6420, `residential`/8790), this actor has TWO
  actuation events (opening a reserve account, releasing an interbank
  settlement batch) acting on the SAME entity (a `member` bank), each
  with its OWN history collection, sequence counter, and dedicated
  double-actuation-guard boolean (`:reserve-account-opened?`/
  `:settlement-batch-released?`, never a single `:status` value) --
  the same discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320).

  NOTE on naming: the protocol's per-entity accessor is `member`
  directly -- not a Clojure special form, so no `-of` suffix
  workaround was needed.

  Per this fleet's standing convention (see `holdco`/6420's own
  ADR-0001), this R0 implementation does NOT add a real dependency on
  the blueprint's own named `:banking` capability
  (`kotoba-lang/banking`) -- it implements the specific ground-truth
  checks a Central Bank Reserve Governor needs (reserve-ratio
  sufficiency, settlement-balance sufficiency) directly, the same
  'implement the concrete check, not the whole capability lib'
  discipline every R0 governed-actor build in this fleet follows.

  The ledger stays append-only on every backend: 'which member was
  screened for an unresolved correspondent-banking due-diligence
  concern, which reserve account was opened, which settlement batch
  was released, on what jurisdictional basis, approved by whom' is
  always a query over an immutable log -- the audit trail a member
  bank trusting a central bank needs, and the evidence a central bank
  needs if an opening or release decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [reserve.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (member [s id])
  (all-members [s])
  (duediligence-screen-of [s member-id] "committed correspondent-banking due-diligence screening verdict for a member, or nil")
  (account-of [s member-id] "committed reserve-account evidence assessment, or nil")
  (ledger [s])
  (reserve-account-history [s] "the append-only reserve-account-opening history (reserve.registry drafts)")
  (settlement-batch-history [s] "the append-only settlement-batch-release history (reserve.registry drafts)")
  (next-account-sequence [s jurisdiction] "next account-number sequence for a jurisdiction")
  (next-settlement-sequence [s jurisdiction] "next batch-number sequence for a jurisdiction")
  (member-already-account-opened? [s member-id] "has this member's reserve account already been opened?")
  (member-already-settlement-released? [s member-id] "has this member's settlement batch already been released?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-members [s members] "replace/seed the member directory (map id->member)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained member set covering both actuation
  lifecycles (opening a reserve account, releasing a settlement
  batch) plus each distinctive HARD-check failure mode so the actor
  + tests run offline."
  []
  {:members
   {"member-1" {:id "member-1" :member-name "Sato Trust Bank"
               :reserve-ratio 5 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 100 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? false
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "JPN" :status :intake}
    "member-2" {:id "member-2" :member-name "Atlantis Federal Bank"
               :reserve-ratio 5 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 100 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? false
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "ATL" :status :intake}
    "member-3" {:id "member-3" :member-name "鈴木相互銀行"
               :reserve-ratio 1 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 100 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? false
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "JPN" :status :intake}
    "member-4" {:id "member-4" :member-name "田中商業銀行"
               :reserve-ratio 5 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 100 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? true
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "JPN" :status :intake}
    "member-5" {:id "member-5" :member-name "高橋信用金庫"
               :reserve-ratio 5 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 1000 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? false
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "JPN" :status :intake}
    ;; member-6: clean on every LOCAL field (no
    ;; `:correspondent-due-diligence-unresolved?`) but its
    ;; `:member-name` is EXACTLY cloud-itonami-isic-8291's own demo
    ;; sanctions-flagged company's `:legal-name` ("Northwind Capital
    ;; Holdings Ltd (demo)", `co-200` in `dossier.store/demo-data`).
    ;; Exists purely to prove `reserve.corporate-intel`'s
    ;; cross-reference into 8291 catches a correspondent bank this
    ;; repo's local-only due-diligence check alone would silently
    ;; clear -- see `test/reserve/corporate_intel_test.clj`.
    "member-6" {:id "member-6" :member-name "Northwind Capital Holdings Ltd (demo)"
               :reserve-ratio 5 :minimum-reserve-ratio-required 2
               :proposed-settlement-amount 100 :available-reserve-balance 500
               :correspondent-due-diligence-unresolved? false
               :reserve-account-opened? false :settlement-batch-released? false
               :jurisdiction "GBR" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- open-reserve-account!
  "Backend-agnostic `:member/mark-account-opened` -- looks up the
  member via the protocol and drafts the reserve-account-opening
  record, and returns {:result .. :member-patch ..} for the caller
  to persist."
  [s member-id]
  (let [m (member s member-id)
        seq-n (next-account-sequence s (:jurisdiction m))
        result (registry/register-reserve-account-opening member-id (:jurisdiction m) seq-n)]
    {:result result
     :member-patch {:reserve-account-opened? true
                   :account-number (get result "account_number")}}))

(defn- release-settlement-batch!
  "Backend-agnostic `:member/mark-settlement-released` -- looks up the
  member via the protocol and drafts the settlement-batch-release
  record, and returns {:result .. :member-patch ..} for the caller to
  persist."
  [s member-id]
  (let [m (member s member-id)
        seq-n (next-settlement-sequence s (:jurisdiction m))
        result (registry/register-settlement-batch-release member-id (:jurisdiction m) seq-n)]
    {:result result
     :member-patch {:settlement-batch-released? true
                   :batch-number (get result "batch_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (member [_ id] (get-in @a [:members id]))
  (all-members [_] (sort-by :id (vals (:members @a))))
  (duediligence-screen-of [_ id] (get-in @a [:duediligence-screens id]))
  (account-of [_ member-id] (get-in @a [:accounts member-id]))
  (ledger [_] (:ledger @a))
  (reserve-account-history [_] (:account-openings @a))
  (settlement-batch-history [_] (:settlement-releases @a))
  (next-account-sequence [_ jurisdiction] (get-in @a [:account-sequences jurisdiction] 0))
  (next-settlement-sequence [_ jurisdiction] (get-in @a [:settlement-sequences jurisdiction] 0))
  (member-already-account-opened? [_ member-id] (boolean (get-in @a [:members member-id :reserve-account-opened?])))
  (member-already-settlement-released? [_ member-id] (boolean (get-in @a [:members member-id :settlement-batch-released?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :member/upsert
      (swap! a update-in [:members (:id value)] merge value)

      :account/set
      (swap! a assoc-in [:accounts (first path)] payload)

      :duediligence/set
      (swap! a assoc-in [:duediligence-screens (first path)] payload)

      :member/mark-account-opened
      (let [member-id (first path)
            {:keys [result member-patch]} (open-reserve-account! s member-id)
            jurisdiction (:jurisdiction (member s member-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:account-sequences jurisdiction] (fnil inc 0))
                       (update-in [:members member-id] merge member-patch)
                       (update :account-openings registry/append result))))
        result)

      :member/mark-settlement-released
      (let [member-id (first path)
            {:keys [result member-patch]} (release-settlement-batch! s member-id)
            jurisdiction (:jurisdiction (member s member-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:settlement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:members member-id] merge member-patch)
                       (update :settlement-releases registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-members [s members] (when (seq members) (swap! a assoc :members members)) s))

(defn seed-db
  "A MemStore seeded with the demo member set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :accounts {} :duediligence-screens {} :ledger []
                           :account-sequences {} :account-openings []
                           :settlement-sequences {} :settlement-releases []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (account/duediligence-screen payloads, ledger
  facts, account-opening/settlement-release records) are stored as
  EDN strings so `langchain.db` doesn't expand them into sub-entities
  -- the same convention every sibling actor's store uses."
  {:member/id                {:db/unique :db.unique/identity}
   :account/member-id         {:db/unique :db.unique/identity}
   :duediligence/member-id      {:db/unique :db.unique/identity}
   :ledger/seq                     {:db/unique :db.unique/identity}
   :account-opening/seq              {:db/unique :db.unique/identity}
   :settlement-release/seq              {:db/unique :db.unique/identity}
   :account-sequence/jurisdiction          {:db/unique :db.unique/identity}
   :settlement-sequence/jurisdiction          {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- member->tx [{:keys [id member-name reserve-ratio minimum-reserve-ratio-required
                          proposed-settlement-amount available-reserve-balance
                          correspondent-due-diligence-unresolved?
                          reserve-account-opened? settlement-batch-released?
                          jurisdiction status account-number batch-number]}]
  (cond-> {:member/id id}
    member-name                                          (assoc :member/member-name member-name)
    reserve-ratio                                          (assoc :member/reserve-ratio reserve-ratio)
    minimum-reserve-ratio-required                           (assoc :member/minimum-reserve-ratio-required minimum-reserve-ratio-required)
    proposed-settlement-amount                                 (assoc :member/proposed-settlement-amount proposed-settlement-amount)
    available-reserve-balance                                    (assoc :member/available-reserve-balance available-reserve-balance)
    (some? correspondent-due-diligence-unresolved?)                 (assoc :member/correspondent-due-diligence-unresolved? correspondent-due-diligence-unresolved?)
    (some? reserve-account-opened?)                                   (assoc :member/reserve-account-opened? reserve-account-opened?)
    (some? settlement-batch-released?)                                  (assoc :member/settlement-batch-released? settlement-batch-released?)
    jurisdiction                                                          (assoc :member/jurisdiction jurisdiction)
    status                                                                  (assoc :member/status status)
    account-number                                                          (assoc :member/account-number account-number)
    batch-number                                                             (assoc :member/batch-number batch-number)))

(def ^:private member-pull
  [:member/id :member/member-name :member/reserve-ratio :member/minimum-reserve-ratio-required
   :member/proposed-settlement-amount :member/available-reserve-balance
   :member/correspondent-due-diligence-unresolved? :member/reserve-account-opened?
   :member/settlement-batch-released? :member/jurisdiction :member/status
   :member/account-number :member/batch-number])

(defn- pull->member [m]
  (when (:member/id m)
    {:id (:member/id m) :member-name (:member/member-name m)
     :reserve-ratio (:member/reserve-ratio m)
     :minimum-reserve-ratio-required (:member/minimum-reserve-ratio-required m)
     :proposed-settlement-amount (:member/proposed-settlement-amount m)
     :available-reserve-balance (:member/available-reserve-balance m)
     :correspondent-due-diligence-unresolved? (boolean (:member/correspondent-due-diligence-unresolved? m))
     :reserve-account-opened? (boolean (:member/reserve-account-opened? m))
     :settlement-batch-released? (boolean (:member/settlement-batch-released? m))
     :jurisdiction (:member/jurisdiction m) :status (:member/status m)
     :account-number (:member/account-number m) :batch-number (:member/batch-number m)}))

(defrecord DatomicStore [conn]
  Store
  (member [_ id]
    (pull->member (d/pull (d/db conn) member-pull [:member/id id])))
  (all-members [_]
    (->> (d/q '[:find [?id ...] :where [?e :member/id ?id]] (d/db conn))
         (map #(pull->member (d/pull (d/db conn) member-pull [:member/id %])))
         (sort-by :id)))
  (duediligence-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?k :duediligence/member-id ?mid] [?k :duediligence/payload ?p]]
              (d/db conn) id)))
  (account-of [_ member-id]
    (dec* (d/q '[:find ?p . :in $ ?mid
                :where [?a :account/member-id ?mid] [?a :account/payload ?p]]
              (d/db conn) member-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (reserve-account-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :account-opening/seq ?s] [?e :account-opening/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (settlement-batch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :settlement-release/seq ?s] [?e :settlement-release/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-account-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :account-sequence/jurisdiction ?j] [?e :account-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-settlement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :settlement-sequence/jurisdiction ?j] [?e :settlement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (member-already-account-opened? [s member-id]
    (boolean (:reserve-account-opened? (member s member-id))))
  (member-already-settlement-released? [s member-id]
    (boolean (:settlement-batch-released? (member s member-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :member/upsert
      (d/transact! conn [(member->tx value)])

      :account/set
      (d/transact! conn [{:account/member-id (first path) :account/payload (enc payload)}])

      :duediligence/set
      (d/transact! conn [{:duediligence/member-id (first path) :duediligence/payload (enc payload)}])

      :member/mark-account-opened
      (let [member-id (first path)
            {:keys [result member-patch]} (open-reserve-account! s member-id)
            jurisdiction (:jurisdiction (member s member-id))
            next-n (inc (next-account-sequence s jurisdiction))]
        (d/transact! conn
                     [(member->tx (assoc member-patch :id member-id))
                      {:account-sequence/jurisdiction jurisdiction :account-sequence/next next-n}
                      {:account-opening/seq (count (reserve-account-history s)) :account-opening/record (enc (get result "record"))}])
        result)

      :member/mark-settlement-released
      (let [member-id (first path)
            {:keys [result member-patch]} (release-settlement-batch! s member-id)
            jurisdiction (:jurisdiction (member s member-id))
            next-n (inc (next-settlement-sequence s jurisdiction))]
        (d/transact! conn
                     [(member->tx (assoc member-patch :id member-id))
                      {:settlement-sequence/jurisdiction jurisdiction :settlement-sequence/next next-n}
                      {:settlement-release/seq (count (settlement-batch-history s)) :settlement-release/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-members [s members]
    (when (seq members) (d/transact! conn (mapv member->tx (vals members)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:members ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [members]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-members s members))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo member set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
