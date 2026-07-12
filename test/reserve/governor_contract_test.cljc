(ns reserve.governor-contract-test
  "The governor contract as executable tests -- the central-banking
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    ReserveOps-LLM never opens a reserve account or releases a
    settlement batch the Central Bank Reserve Governor would reject,
    `:actuation/open-reserve-account`/`:actuation/release-settlement-
    batch` NEVER auto-commit at any phase, `:member/intake` (no
    direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [reserve.store :as store]
            [reserve.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :central-bank-staff :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving an account
  evidence checklist on file. Uses distinct thread-ids per call site
  by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :account/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :member/intake :subject "member-1"
                   :patch {:id "member-1" :member-name "Sato Trust Bank"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Trust Bank" (:member-name (store/member db "member-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest account-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :account/verify :subject "member-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/account-of db "member-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an account/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :account/verify :subject "member-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/account-of db "member-1")) "no account written"))))

(deftest open-reserve-account-without-account-is-held
  (testing "actuation/open-reserve-account before any account verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/open-reserve-account :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest release-settlement-batch-without-account-is-held
  (testing "actuation/release-settlement-batch before any account verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t5" {:op :actuation/release-settlement-batch :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest reserve-ratio-insufficient-is-held
  (testing "a member whose own recorded reserve ratio falls short of its own recorded minimum requirement -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "member-3")
          res (exec-op actor "t6" {:op :actuation/open-reserve-account :subject "member-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:reserve-ratio-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/reserve-account-history db))))))

(deftest correspondent-due-diligence-unresolved-is-held-and-unoverridable
  (testing "an unresolved correspondent-banking due-diligence concern on a member -> HOLD, and never reaches request-approval -- exercised via :duediligence/screen DIRECTLY, not via an actuation op against an unscreened member (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's, banking's, advertising's, polling's, research's, design's, nursing's, sports's, alliedhealth's, laundry's, holdco's, photo's, personalservice's, edsupport's, headoffice's, residential's and cultural's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :duediligence/screen :subject "member-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:correspondent-due-diligence-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/duediligence-screen-of db "member-4")) "no clearance written"))))

(deftest settlement-batch-exceeds-available-reserve-balance-is-held
  (testing "a member whose own proposed settlement exceeds its own recorded available reserve balance -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "member-5")
          res (exec-op actor "t8" {:op :actuation/release-settlement-batch :subject "member-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:settlement-batch-exceeds-available-reserve-balance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/settlement-batch-history db))))))

(deftest open-reserve-account-always-escalates-then-human-decides
  (testing "a clean, fully-assessed member still ALWAYS interrupts for human approval -- actuation/open-reserve-account is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "member-1")
          r1 (exec-op actor "t9" {:op :actuation/open-reserve-account :subject "member-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, reserve-account-opening record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:reserve-account-opened? (store/member db "member-1"))))
          (is (= 1 (count (store/reserve-account-history db))) "one draft account-opening record"))))))

(deftest release-settlement-batch-always-escalates-then-human-decides
  (testing "a clean, fully-assessed member still ALWAYS interrupts for human approval -- actuation/release-settlement-batch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "member-1")
          r1 (exec-op actor "t10" {:op :actuation/release-settlement-batch :subject "member-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, settlement-batch-release record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:settlement-batch-released? (store/member db "member-1"))))
          (is (= 1 (count (store/settlement-batch-history db))) "one draft settlement-release record"))))))

(deftest double-account-opening-is-held
  (testing "opening the same member's reserve account twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "member-1")
          _ (exec-op actor "t11a" {:op :actuation/open-reserve-account :subject "member-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :actuation/open-reserve-account :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-account-opened} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/reserve-account-history db))) "still only the one earlier opening"))))

(deftest double-settlement-release-is-held
  (testing "releasing the same member's settlement batch twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "member-1")
          _ (exec-op actor "t12a" {:op :actuation/release-settlement-batch :subject "member-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :actuation/release-settlement-batch :subject "member-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-settlement-released} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/settlement-batch-history db))) "still only the one earlier release"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :member/intake :subject "member-1"
                          :patch {:id "member-1" :member-name "Sato Trust Bank"}} operator)
      (exec-op actor "b" {:op :account/verify :subject "member-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
