(ns reserve.corporate-intel
  "Optional integration with `cloud-itonami-isic-8291` (Dossier-LLM ⊣
  DisclosureGovernor corporate/compliance intelligence actor) --
  cross-references a correspondent-banking member (a bank, not a
  person) against 8291's sourced company-flag data via the SAME
  governed `:disclosure/query` op any other licensed consumer would
  use -- there is no bypass of 8291's own DisclosureGovernor from this
  side either. In particular, when 8291 escalates a real sanctions hit
  for ITS OWN human reviewer to confirm, this namespace does NOT peek
  at Dossier-LLM's un-vetted draft proposal to get an early answer --
  it reports `:pending-human-review?` and lets
  `reserve.reserveadvisor/screen-duediligence` treat that the same as
  any other unresolved signal (this repo's vocabulary has no separate
  'incomplete' state, so unresolved wins over clear -- see
  `reserve.reserveadvisor`'s docstring on
  `:correspondent-due-diligence-unresolved?`).

  Swappable like every other dependency in this fleet (Store/Advisor/
  Phase): `screen-company` defaults to a demo 8291 MemStore + fresh
  actor per call, but takes an already-built `:actor` for production
  (one built once, against a real Store, under a real contract this
  blueprint's operator negotiated with the 8291 operator)."
  (:require [langgraph.graph :as g]
            [dossier.store :as dstore]
            [dossier.operation :as dop]))

(def default-tenant
  "This blueprint's own tenant id under an 8291 contract. A real
  deployment registers this (or an operator-chosen tenant id) with
  whichever 8291 instance/operator it has a compliance-tier contract
  with."
  "cloud-itonami-isic-6411")

(defn demo-store
  "An 8291 MemStore seeded with 8291's own demo data, PLUS a contract
  for THIS blueprint's tenant at `:tier/compliance` (a company profile
  query with `:flags` requires at least that tier -- see
  cloud-itonami-isic-8291's `dossier.policy/tier-columns`). Replaces
  8291's own demo tenant-acme/tenant-basic/tenant-graph contracts
  entirely: this is 6411's OWN isolated offline view, not a shared
  runtime instance with 8291's demo fixtures."
  []
  (-> (dstore/seed-db)
      (dstore/with-contracts
       {default-tenant {:tenant default-tenant :tier :tier/compliance
                         :active? true :purpose :correspondent-banking-due-diligence}})))

(defn build
  "Compiles an 8291 OperationActor bound to `store` (default: `demo-store`)."
  ([] (build (demo-store)))
  ([store] (dop/build store)))

(defn screen-company
  "Runs a `:disclosure/query` op against 8291 for `member-name` (a
  correspondent bank's company name, resolved via 8291's
  `dossier.store/company-by-name`). Returns one of:
    {:company-id .. :flags {..}}                             -- a
      governor-approved company profile (disposition :commit): the
      bank is either clean or not on file at all (a nil `:company-id`
      with empty `:flags`).
    {:pending-human-review? true :reason kw}                -- 8291
      itself escalated a real sanctions hit to ITS OWN human reviewer;
      treat as unresolved, not as a hit or a clear.
    {:held? true :reason [kw ..]}                            -- the
      query itself was rejected by 8291's DisclosureGovernor (e.g.
      this tenant's contract is missing/inactive/wrong tier on the
      Store actually in use) -- a configuration problem on the calling
      side, not a finding about `member-name`. Never silently treated
      as clear.

  opts:
    :actor     -- a pre-built 8291 OperationActor (default: fresh `build`)
    :tenant    -- tenant id to query under (default: `default-tenant`)
    :thread-id -- langgraph-clj thread id (default: derived from `member-name`)"
  ([member-name] (screen-company member-name {}))
  ([member-name {:keys [actor tenant thread-id]
                 :or {actor (build) tenant default-tenant}}]
   (let [thread-id (or thread-id (str "compcheck-" tenant "-" member-name))
         res (g/run* actor
                     {:request {:op :disclosure/query :subject tenant :company-name member-name}
                      :context {:actor-id default-tenant :actor-role :client :tenant tenant}}
                     {:thread-id thread-id})]
     (case (get-in res [:state :disposition])
       :commit    (get-in res [:state :record :value])
       :escalate  {:pending-human-review? true :reason (-> res :state :audit last :reason)}
       :hold      {:held? true :reason (-> res :state :audit last :basis)}
       {:held? true :reason [:corporate-intel-actor-error]}))))
