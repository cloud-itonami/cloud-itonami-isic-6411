(ns reserve.corporate-intel-test
  "Proves the value `reserve.corporate-intel` actually adds: a
  correspondent-banking member (a bank, not a person) that is clean on
  every LOCAL field (no `:correspondent-due-diligence-unresolved?`)
  but IS sanctions-flagged in `cloud-itonami-isic-8291`'s own demo
  data no longer silently resolves -- something 6411's local-only
  checks alone would have missed entirely (see `member-6` in
  `reserve.store/demo-data`, whose `:member-name` is EXACTLY 8291's
  sanctions-flagged demo company's own `:legal-name`).

  Vocabulary note (this repo has NO 'incomplete' concept, unlike
  `cloud-itonami-isic-6910`'s officer-screening 3-way `:hit`/
  `:incomplete`/`:clear`): `reserve.reserveadvisor/screen-duediligence`
  only ever produces `:correspondent-due-diligence-unresolved?`
  true/false. So EVERY non-clean 8291 signal -- a definitive
  sanctions-flag hit, 8291's own pending human review, or 8291's query
  itself being held -- collapses onto the SAME `true` verdict a local
  flag would produce. `reserve.governor`'s
  `correspondent-banking-due-diligence-unresolved-violations` check
  reads this UNCONDITIONALLY (see its docstring /
  `test/reserve/governor_contract_test.clj`'s
  `correspondent-due-diligence-unresolved-is-held-and-unoverridable`),
  so unlike 6910 (where an inconclusive 8291 signal degrades to a SOFT
  `:incomplete` that still escalates for a human), here it is a HARD,
  un-overridable HOLD -- EMPIRICALLY VERIFIED below (not assumed) to
  settle IMMEDIATELY, with no interrupt, even against the REAL,
  unmocked 8291 actor: 8291's own DisclosureGovernor escalates the
  underlying sanctions hit for ITS OWN human reviewer first (a
  `:disclosure/query` company-profile lookup is high-stakes there
  too), but this repo's binary vocabulary has no middle ground between
  resolved and unresolved, so that escalation collapses onto `true`,
  which 6411's own governor then HARD-holds unconditionally -- more
  conservative than the 3-way repos, the same shape
  `cloud-itonami-isic-6419`'s/`6420`'s own binary-collapse integration
  already establishes."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [reserve.store :as store]
            [reserve.operation :as op]
            [reserve.reserveadvisor :as reserveadvisor]
            [reserve.corporate-intel :as ci]))

(def operator {:actor-id "op-1" :actor-role :central-bank-staff :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- wired-actor []
  (let [db (store/seed-db)]
    [db (op/build db {:advisor (reserveadvisor/mock-advisor {:corporate-intel-screen ci/screen-company})})]))

(deftest local-checks-alone-would-miss-the-8291-flagged-member
  (testing "sanity: without the integration, member-6 passes the local check and resolves clean"
    (let [db (store/seed-db)
          actor (op/build db)                          ; NO corporate-intel wired in
          res (exec-op actor "sanity" {:op :duediligence/screen :subject "member-6"} operator)]
      (is (= :interrupted (:status res)) "duediligence/screen always escalates for approval, clean or not")
      (approve! actor "sanity")
      (is (false? (:correspondent-due-diligence-unresolved?
                   (store/duediligence-screen-of db "member-6")))
          "without the integration, member-6 screens resolved (false) -- this is the gap being closed"))))

(deftest corporate-intel-catches-the-hit-local-checks-miss
  (testing "with the REAL (unmocked) 8291 actor wired in, member-6 hard-holds instead of silently resolving"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t1" {:op :duediligence/screen :subject "member-6"} operator)]
      (is (= :hold (get-in res [:state :disposition]))
          "8291 itself escalates a real sanctions hit for ITS OWN human review first -- 6411 never
           peeks behind that gate, but this repo has no :incomplete middle ground, so a
           :pending-human-review? signal collapses onto true, which HARD-holds immediately here
           (settles without an interrupt, empirically confirmed by this test)")
      (is (some #{:correspondent-due-diligence-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/duediligence-screen-of db "member-6"))
          "critically: no clearance is ever written, unlike the unwired sanity case above"))))

(deftest corporate-intel-definitive-hit-hard-holds
  (testing "screen-duediligence's sanctions-flag branch itself is a HARD, un-overridable hold -- proven
            directly with a stub (a real 8291 hit always escalates for 8291's own human first, so this
            branch is only reachable end-to-end after that human confirms; unit-testing it here keeps
            the assertion deterministic)"
    (let [db (store/seed-db)
          definitive-hit (fn [_member-name] {:flags {:sanctions? true}})
          actor (op/build db {:advisor (reserveadvisor/mock-advisor {:corporate-intel-screen definitive-hit})})
          res (exec-op actor "t2" {:op :duediligence/screen :subject "member-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (some #{:correspondent-due-diligence-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/duediligence-screen-of db "member-6")) "no clearance written"))))

(deftest corporate-intel-held-screen-also-hard-holds
  (testing "if 6411's own tenant contract with 8291 is missing/misconfigured, 8291 itself holds the
            query -- 6411 must treat that as unresolved (never resolved), and because there is no
            :incomplete middle ground here, that HARD-holds immediately rather than merely escalating"
    (let [db (store/seed-db)
          broken-screen (fn [_member-name] {:held? true :reason [:licensed-disclosure]})
          actor (op/build db {:advisor (reserveadvisor/mock-advisor {:corporate-intel-screen broken-screen})})
          res (exec-op actor "t3" {:op :duediligence/screen :subject "member-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (some #{:correspondent-due-diligence-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/duediligence-screen-of db "member-6"))))))

(deftest corporate-intel-clean-member-still-resolves
  (testing "a member with no local signal, and no match in 8291's demo data, still resolves clean --
            additive, not stricter-by-default (a confident not-found is not treated as a hit)"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t4" {:op :duediligence/screen :subject "member-1"} operator)]
      (is (= :interrupted (:status res)))
      (approve! actor "t4")
      (is (false? (:correspondent-due-diligence-unresolved?
                   (store/duediligence-screen-of db "member-1")))))))

(deftest corporate-intel-local-unresolved-short-circuits-before-8291-is-consulted
  (testing "a local :correspondent-due-diligence-unresolved? decides the verdict first -- 8291 is never
            even queried"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t5" {:op :duediligence/screen :subject "member-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:correspondent-due-diligence-unresolved} (-> (store/ledger db) first :basis))))))
