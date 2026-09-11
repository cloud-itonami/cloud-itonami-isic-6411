(ns reserve.facts-test
  (:require [clojure.test :refer [deftest is]]
            [reserve.facts :as facts]))

(deftest known-jurisdictions-have-a-spec-basis
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU" "SAU" "IND"]]
    (is (some? (facts/spec-basis iso3)) (str iso3 " should have a spec-basis"))
    (is (= 4 (count (:required-evidence (facts/spec-basis iso3)))))))

(deftest saudi-arabia-spec-basis-has-the-catalog-shape
  (let [sau (facts/spec-basis "SAU")]
    (is (= "Saudi Arabia" (:name sau)))
    (is (= "Saudi Central Bank (SAMA) -- renamed by law from Saudi Arabian Monetary Authority in 2020"
           (:owner-authority sau)))
    ;; SAU's :legal-basis cites the Royal Decree approving the Saudi
    ;; Central Bank Law (verified from SAMA's own 57th Annual Report this
    ;; session -- see facts.cljc catalog comment for sourcing detail).
    (is (re-find #"Saudi Central Bank Law" (:legal-basis sau)))
    (is (re-find #"36/M" (:legal-basis sau)))
    ;; The SAR/USD peg is recorded as SAMA policy, not a legal-basis item
    ;; -- honest-gap discipline (never assert a formal legal peg that
    ;; wasn't independently confirmed as a statutory provision).
    (is (re-find #"policy" (:national-spec sau)))
    (is (= "https://www.sama.gov.sa/en-US/About/Pages/default.aspx" (:provenance sau)))
    (is (= 4 (count (:required-evidence sau))))))

(deftest india-spec-basis-has-the-catalog-shape
  (let [ind (facts/spec-basis "IND")]
    (is (= "India" (:name ind)))
    (is (= "Reserve Bank of India (RBI)" (:owner-authority ind)))
    ;; IND's :legal-basis cites RBI Act 1934 Section 42(1) (Cash Reserve
    ;; Ratio, verified from the Wayback-archived Act PDF this session --
    ;; the live rbidocs.rbi.org.in host bot-detection-blocked the PDF, so
    ;; the Act text was verified via the Internet Archive instead, per this
    ;; fleet's fallback policy) and the KYC Master Direction's paragraph 73
    ;; Correspondent Banking due-diligence requirements (verified live from
    ;; rbi.org.in this session).
    (is (re-find #"Reserve Bank of India Act, 1934" (:legal-basis ind)))
    (is (re-find #"Section 42\(1\)" (:legal-basis ind)))
    (is (re-find #"Know Your Customer" (:legal-basis ind)))
    (is (re-find #"Correspondent Banking" (:legal-basis ind)))
    ;; CRR is a periodically-renotified rate, not permanent law -- honest
    ;; discipline: recorded as a dated snapshot, not a fixed figure.
    (is (re-find #"3\.00%" (:national-spec ind)))
    (is (re-find #"as of 2026-07-23" (:national-spec ind)))
    (is (= "https://www.rbi.org.in/Scripts/BS_ViewMasterDirections.aspx" (:provenance ind)))
    (is (= 4 (count (:required-evidence ind))))))

(deftest unknown-jurisdiction-has-no-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-is-honest
  (let [c (facts/coverage ["JPN" "USA" "ATL"])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions c)))
    (is (= ["ATL"] (:missing-jurisdictions c)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [checklist (facts/evidence-checklist "JPN")]
    (is (= 4 (count checklist)))
    (is (facts/required-evidence-satisfied? "JPN" checklist))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest checklist))))
    (is (not (facts/required-evidence-satisfied? "ATL" checklist)))))
