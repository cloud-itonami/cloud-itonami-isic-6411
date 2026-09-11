(ns reserve.registry-test
  (:require [clojure.test :refer [deftest is]]
            [reserve.registry :as r]))

;; ----------------------------- reserve-ratio-insufficient? -----------------------------

(deftest not-insufficient-when-ratio-met
  (is (not (r/reserve-ratio-insufficient?
            {:reserve-ratio 5 :minimum-reserve-ratio-required 2})))
  (is (not (r/reserve-ratio-insufficient?
            {:reserve-ratio 2 :minimum-reserve-ratio-required 2}))))

(deftest insufficient-when-ratio-short
  (is (r/reserve-ratio-insufficient?
       {:reserve-ratio 1 :minimum-reserve-ratio-required 2})))

(deftest missing-fields-are-not-treated-as-insufficient
  (is (not (r/reserve-ratio-insufficient? {})))
  (is (not (r/reserve-ratio-insufficient? {:reserve-ratio 1}))))

;; ----------------------------- settlement-batch-exceeds-available-reserve-balance? -----------------------------

(deftest not-exceeding-when-within-balance
  (is (not (r/settlement-batch-exceeds-available-reserve-balance?
            {:proposed-settlement-amount 100 :available-reserve-balance 500})))
  (is (not (r/settlement-batch-exceeds-available-reserve-balance?
            {:proposed-settlement-amount 500 :available-reserve-balance 500}))))

(deftest exceeding-when-over-balance
  (is (r/settlement-batch-exceeds-available-reserve-balance?
       {:proposed-settlement-amount 1000 :available-reserve-balance 500})))

(deftest missing-fields-are-not-treated-as-exceeding
  (is (not (r/settlement-batch-exceeds-available-reserve-balance? {})))
  (is (not (r/settlement-batch-exceeds-available-reserve-balance? {:proposed-settlement-amount 1000}))))

;; ----------------------------- register-reserve-account-opening -----------------------------

(deftest account-opening-is-a-draft-not-a-real-opening
  (let [result (r/register-reserve-account-opening "member-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest account-opening-assigns-account-number
  (let [result (r/register-reserve-account-opening "member-1" "JPN" 7)]
    (is (= (get result "account_number") "JPN-RSV-000007"))
    (is (= (get-in result ["record" "member_id"]) "member-1"))
    (is (= (get-in result ["record" "kind"]) "reserve-account-opening-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest account-opening-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-reserve-account-opening "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-reserve-account-opening "member-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-reserve-account-opening "member-1" "JPN" -1))))

;; ----------------------------- register-settlement-batch-release -----------------------------

(deftest settlement-release-is-a-draft-not-a-real-release
  (let [result (r/register-settlement-batch-release "member-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest settlement-release-assigns-batch-number
  (let [result (r/register-settlement-batch-release "member-1" "JPN" 7)]
    (is (= (get result "batch_number") "JPN-STL-000007"))
    (is (= (get-in result ["record" "member_id"]) "member-1"))
    (is (= (get-in result ["record" "kind"]) "settlement-batch-release-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest settlement-release-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-settlement-batch-release "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-settlement-batch-release "member-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-settlement-batch-release "member-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-reserve-account-opening "member-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-reserve-account-opening "member-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-RSV-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-RSV-000001" (get-in hist2 [1 "record_id"])))))
