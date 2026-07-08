(ns reserve.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/open-reserve-account`/`:actuation/release-
  settlement-batch` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [reserve.phase :as phase]))

(deftest open-reserve-account-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real reserve-account opening"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/open-reserve-account))
          (str "phase " n " must not auto-commit :actuation/open-reserve-account")))))

(deftest release-settlement-batch-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real settlement-batch release"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/release-settlement-batch))
          (str "phase " n " must not auto-commit :actuation/release-settlement-batch")))))

(deftest duediligence-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :duediligence/screen))
          (str "phase " n " must not auto-commit :duediligence/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":member/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:member/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :member/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/open-reserve-account} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/release-settlement-batch} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :member/intake} :commit)))))
