(ns wasm.reserve-ratio-test
  "Hosts wasm/reserve_ratio.wasm (compiled from wasm/reserve_ratio.kotoba,
  see wasm/README.md) via kototama.tender -- proves reserve.registry's
  reserve-ratio-insufficient? check (src/reserve/registry.cljc) runs as a
  real WASM guest, not just as JVM Clojure.

  ABI: main is 0-arity (kotoba wasm emit rejects a parameterized main --
  :main-arity); the two real i32 inputs are written into the guest's
  exported linear memory at fixed offsets before calling main() -- see
  wasm/reserve_ratio.kotoba's ns docstring for the offset layout."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kototama.contract :as contract]
            [kototama.tender :as tender]))

(defn- wasm-bytes []
  (.readAllBytes (io/input-stream (io/file "wasm/reserve_ratio.wasm"))))

(defn- run-reserve-ratio-sufficient? [reserve-ratio minimum-reserve-ratio-required]
  (let [instance (tender/instantiate (wasm-bytes) [] (contract/host-caps {}))
        memory (.memory instance)]
    (.writeI32 memory 0 reserve-ratio)
    (.writeI32 memory 4 minimum-reserve-ratio-required)
    (tender/call-main instance)))

(deftest reserve-ratio-wasm-approves-well-above-minimum
  (testing "reserve-ratio well above minimum-reserve-ratio-required -> sufficient"
    (is (= 1 (run-reserve-ratio-sufficient? 5 2)))))

(deftest reserve-ratio-wasm-rejects-below-minimum
  (testing "reserve-ratio below minimum-reserve-ratio-required -> insufficient"
    (is (= 0 (run-reserve-ratio-sufficient? 1 2)))))

(deftest reserve-ratio-wasm-approves-exact-boundary
  (testing "reserve-ratio exactly equal to minimum -> sufficient (>=, not strict >)"
    (is (= 1 (run-reserve-ratio-sufficient? 2 2)))))

(deftest reserve-ratio-wasm-rejects-zero-ratio-against-positive-minimum
  (testing "zero reserve-ratio against a positive minimum -> insufficient"
    (is (= 0 (run-reserve-ratio-sufficient? 0 1)))))
