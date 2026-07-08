(ns reserve.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [reserve.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Trust Bank" (:member-name (store/member s "member-1"))))
      (is (= "JPN" (:jurisdiction (store/member s "member-1"))))
      (is (= 5 (:reserve-ratio (store/member s "member-1"))))
      (is (false? (:correspondent-due-diligence-unresolved? (store/member s "member-1"))))
      (is (= 1 (:reserve-ratio (store/member s "member-3"))))
      (is (true? (:correspondent-due-diligence-unresolved? (store/member s "member-4"))))
      (is (= 1000 (:proposed-settlement-amount (store/member s "member-5"))))
      (is (false? (:reserve-account-opened? (store/member s "member-1"))))
      (is (false? (:settlement-batch-released? (store/member s "member-1"))))
      (is (= ["member-1" "member-2" "member-3" "member-4" "member-5"]
             (mapv :id (store/all-members s))))
      (is (nil? (store/duediligence-screen-of s "member-1")))
      (is (nil? (store/account-of s "member-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/reserve-account-history s)))
      (is (= [] (store/settlement-batch-history s)))
      (is (zero? (store/next-account-sequence s "JPN")))
      (is (zero? (store/next-settlement-sequence s "JPN")))
      (is (false? (store/member-already-account-opened? s "member-1")))
      (is (false? (store/member-already-settlement-released? s "member-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :member/upsert
                                 :value {:id "member-1" :member-name "Sato Trust Bank"}})
        (is (= "Sato Trust Bank" (:member-name (store/member s "member-1"))))
        (is (= 5 (:reserve-ratio (store/member s "member-1"))) "unrelated field preserved"))
      (testing "account / duediligence payloads commit and read back"
        (store/commit-record! s {:effect :account/set :path ["member-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/account-of s "member-1")))
        (store/commit-record! s {:effect :duediligence/set :path ["member-1"]
                                 :payload {:member-id "member-1" :correspondent-due-diligence-unresolved? false}})
        (is (= {:member-id "member-1" :correspondent-due-diligence-unresolved? false} (store/duediligence-screen-of s "member-1"))))
      (testing "reserve-account opening drafts a record and advances the sequence"
        (store/commit-record! s {:effect :member/mark-account-opened :path ["member-1"]})
        (is (= "JPN-RSV-000000" (get (first (store/reserve-account-history s)) "record_id")))
        (is (= "reserve-account-opening-draft" (get (first (store/reserve-account-history s)) "kind")))
        (is (true? (:reserve-account-opened? (store/member s "member-1"))))
        (is (= 1 (count (store/reserve-account-history s))))
        (is (= 1 (store/next-account-sequence s "JPN")))
        (is (true? (store/member-already-account-opened? s "member-1")))
        (is (false? (store/member-already-account-opened? s "member-2"))))
      (testing "settlement-batch release drafts a record and advances the sequence"
        (store/commit-record! s {:effect :member/mark-settlement-released :path ["member-1"]})
        (is (= "JPN-STL-000000" (get (first (store/settlement-batch-history s)) "record_id")))
        (is (= "settlement-batch-release-draft" (get (first (store/settlement-batch-history s)) "kind")))
        (is (true? (:settlement-batch-released? (store/member s "member-1"))))
        (is (= 1 (count (store/settlement-batch-history s))))
        (is (= 1 (store/next-settlement-sequence s "JPN")))
        (is (true? (store/member-already-settlement-released? s "member-1")))
        (is (false? (store/member-already-settlement-released? s "member-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/member s "nope")))
    (is (= [] (store/all-members s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/reserve-account-history s)))
    (is (= [] (store/settlement-batch-history s)))
    (is (zero? (store/next-account-sequence s "JPN")))
    (is (zero? (store/next-settlement-sequence s "JPN")))
    (store/with-members s {"x" {:id "x" :member-name "n"
                                :reserve-ratio 5 :minimum-reserve-ratio-required 2
                                :proposed-settlement-amount 100 :available-reserve-balance 500
                                :correspondent-due-diligence-unresolved? false
                                :reserve-account-opened? false :settlement-batch-released? false
                                :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:member-name (store/member s "x"))))))
