(ns reserve.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean member bank
  through intake -> account verification -> correspondent-banking
  due-diligence screening -> reserve-account-opening proposal (always
  escalates) -> human approval -> commit, then through settlement-
  batch-release proposal (always escalates) -> human approval ->
  commit, then shows five HARD holds (a jurisdiction with no spec-
  basis, a member whose own recorded reserve ratio falls short of its
  own recorded minimum requirement, a member whose own recorded
  correspondent-banking due-diligence concern has NOT been resolved
  [screened directly via `:duediligence/screen` -- never via an
  actuation op against an unscreened member -- see this actor's own
  governor ns docstring / the lesson `parksafety`'s ADR-2607071922
  Decision 5, `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s, `leasing`'s, `behavioral`'s,
  `secondary`'s, `card`'s, `water`'s, `telecom`'s, `aerospace`'s,
  `recovery`'s, `consulting`'s, `union`'s, `congregation`'s, `fab`'s,
  `energy`'s, `care`'s, `navigator`'s, `learning`'s, `banking`'s,
  `advertising`'s, `polling`'s, `research`'s, `design`'s, `nursing`'s,
  `sports`'s, `alliedhealth`'s, `laundry`'s, `holdco`'s, `photo`'s,
  `personalservice`'s, `edsupport`'s, `headoffice`'s, `residential`'s
  and `cultural`'s ADR-0001s already recorded], a member whose own
  proposed settlement amount exceeds its own recorded available
  reserve balance, and a double opening/release of an already-
  processed member) that never reach a human at all, and prints the
  audit ledger + the draft reserve-account-opening and settlement-
  batch-release records."
  (:require [langgraph.graph :as g]
            [reserve.store :as store]
            [reserve.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :central-bank-staff :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== member/intake member-1 (JPN, clean; reserve ratio sufficient, due diligence resolved) ==")
    (println (exec! actor "t1" {:op :member/intake :subject "member-1"
                                :patch {:id "member-1" :member-name "Sato Trust Bank"}} operator))

    (println "== account/verify member-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :account/verify :subject "member-1"} operator))
    (println (approve! actor "t2"))

    (println "== duediligence/screen member-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :duediligence/screen :subject "member-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/open-reserve-account member-1 (always escalates -- actuation/open-reserve-account) ==")
    (let [r (exec! actor "t4" {:op :actuation/open-reserve-account :subject "member-1"} operator)]
      (println r)
      (println "-- human central-bank staff approves --")
      (println (approve! actor "t4")))

    (println "== actuation/release-settlement-batch member-1 (always escalates -- actuation/release-settlement-batch) ==")
    (let [r (exec! actor "t5" {:op :actuation/release-settlement-batch :subject "member-1"} operator)]
      (println r)
      (println "-- human central-bank staff approves --")
      (println (approve! actor "t5")))

    (println "== account/verify member-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :account/verify :subject "member-2" :no-spec? true} operator))

    (println "== account/verify member-3 (escalates -- human approves; sets up the reserve-ratio test) ==")
    (println (exec! actor "t7" {:op :account/verify :subject "member-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/open-reserve-account member-3 (reserve ratio 1 < required 2 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/open-reserve-account :subject "member-3"} operator))

    (println "== duediligence/screen member-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :duediligence/screen :subject "member-4"} operator))

    (println "== account/verify member-5 (escalates -- human approves; sets up the settlement-balance test) ==")
    (println (exec! actor "t10" {:op :account/verify :subject "member-5"} operator))
    (println (approve! actor "t10"))

    (println "== actuation/release-settlement-batch member-5 (proposed 1000 exceeds available balance 500 -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/release-settlement-batch :subject "member-5"} operator))

    (println "== actuation/open-reserve-account member-1 AGAIN (double-opening -> HARD hold) ==")
    (println (exec! actor "t12" {:op :actuation/open-reserve-account :subject "member-1"} operator))

    (println "== actuation/release-settlement-batch member-1 AGAIN (double-release -> HARD hold) ==")
    (println (exec! actor "t13" {:op :actuation/release-settlement-batch :subject "member-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft reserve-account-opening records ==")
    (doseq [r (store/reserve-account-history db)] (println r))

    (println "== draft settlement-batch-release records ==")
    (doseq [r (store/settlement-batch-history db)] (println r))))
