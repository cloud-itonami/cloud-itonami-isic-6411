# wasm/ — kotoba-wasm deployment of the reserve-ratio-sufficiency check

`reserve_ratio.kotoba` is a port of `reserve.registry/reserve-ratio-
insufficient?`'s pure ground-truth comparison — does a member bank's own
recorded reserve ratio fall short of its own recorded minimum-required
reserve ratio? (see `src/reserve/registry.cljc` lines ~85-94, and its
independent recompute in `src/reserve/governor.cljc` lines ~195-206 and
the integer-coded safety kernel in `src/reserve/kernels/gate.cljc` lines
~93-102) — into the minimal `.kotoba` language subset, compiled to a real
WASM module via `kotoba wasm emit`, and hosted via `kototama.tender`
(`test/wasm/reserve_ratio_test.clj`).

This follows the same `kotoba wasm emit` → `kototama.tender` pattern
already proven by `cloud-itonami-isic-6492`'s `wasm/affordability.kotoba`,
`cloud-itonami-isic-6511`'s `wasm/underwriting_decision.kotoba` and
`cloud-itonami-isic-6512`'s `wasm/claim_coverage.kotoba` (ADR-2607062330
addendum 5) — this fleet's central-banking actor's hot-path decision
function ported to real WASM.

## Why the source differs from `reserve.registry`

The `.kotoba` compiler's actual WASM code-generator supports only a small,
empirically-verified subset: the special forms `do`/`let`/`if` plus
`+ - * quot / rem mod = < > <= >= zero? not inc dec` (confirmed by reading
`compile-wasm-expr` in `kotoba-lang/kotoba/src/kotoba/runtime.clj` — no
`pos?`/`neg?`/`and`/`or`/`when`, unlike the broader tree-walking
interpreter). The port therefore:

- Ports ONLY the pure ground-truth arithmetic core — the direct comparison
  of `reserve-ratio` against `minimum-reserve-ratio-required` — never the
  `store/member` lookup or the `:actuation/open-reserve-account`
  op-dispatch, both of which stay in Clojure and never get ported (no
  maps, no protocols, no op-keyword dispatch in the wasm-compilable
  subset).
- Uses plain positional integer args instead of `{:keys [...]}` map
  destructuring (no maps in the wasm-compilable subset), and drops the
  `(and (number? ...) (number? ...))` guard the original registry
  function makes on its map fields (irrelevant here — the two i32 inputs
  are always numeric by construction of the wasm ABI).
- Compares `reserve-ratio >= minimum-reserve-ratio-required` directly as
  plain integers (the SAME integer percentage-point units the member
  record itself uses, per `reserve.kernels.gate`'s ns docstring) instead
  of the registry's `(< reserve-ratio minimum-reserve-ratio-required)` —
  no ratio/division needed at all, unlike `affordability.kotoba`'s 43%
  DTI ceiling cross-multiplication: `reserve-ratio-insufficient?` already
  compares two like-quantities directly in the member's own recorded
  units, so this is the SIMPLEST possible port in this fleet's wasm
  series — one direct integer comparison, no formula, no zero-guard
  branch, no cross-multiplication.
- Inverts the polarity relative to `reserve.registry`'s violation check:
  `reserve-ratio-insufficient?` returns true (a violation) when the ratio
  falls STRICTLY short of the minimum, whereas this module's
  `reserve-ratio-sufficient?` (and `main`) returns `1` when the ratio is
  AT OR ABOVE the minimum (i.e. NOT a violation) and `0` when it falls
  short — the more natural "is this OK" polarity for a boolean-shaped
  WASM export, same polarity convention as `affordability.kotoba`'s
  `affordable?` and `claim_coverage.kotoba`'s `claim-within-coverage?`.

## ABI — parameterized invocation

`kotoba wasm emit` rejects any `main` with parameters (`:main-arity` — the
compiler only ever exports a 0-arity `main`, see `compile-wasm-expr` in
`kotoba-lang/kotoba/src/kotoba/runtime.clj`), so real inputs are passed
through the guest's exported linear memory instead — the same convention
`cloud-itonami-isic-6492`'s `affordability.kotoba`, `cloud-itonami-isic-
6511`'s `underwriting_decision.kotoba` and `cloud-itonami-isic-6512`'s
`claim_coverage.kotoba` use. A host writes two little-endian i32 values
before calling `main()`:

| offset | field                            |
|--------|----------------------------------|
| 0      | `reserve-ratio`                  |
| 4      | `minimum-reserve-ratio-required` |

Both fields are integer percentage points in the SAME units the member
record itself uses (see `reserve.kernels.gate`'s ns docstring). `main()`
returns `1` (sufficient — reserve-ratio at or above the minimum, no
violation) or `0` (insufficient — a HARD `:reserve-ratio-insufficient`
violation per `reserve.governor`, gating `:actuation/open-reserve-
account`). Both offsets are well below `heap-base` (2048), so they never
collide with anything the compiler itself places in memory.

## Rebuilding

```sh
cd ../../kotoba-lang/kotoba   # sibling checkout, west-managed
bin/kotoba-clj wasm emit ../../cloud-itonami/cloud-itonami-isic-6411/wasm/reserve_ratio.kotoba \
  --package-lock kotoba.lock.edn \
  --output ../../cloud-itonami/cloud-itonami-isic-6411/wasm/reserve_ratio.wasm --json
```

Fleet deployment: not attempted in this pass — see cloud-itonami-isic-6492/6511 for the established pattern.
