(ns reserve.reserveadvisor
  "ReserveOps-LLM client -- the *contained intelligence node* for the
  central-banking actor.

  It normalizes member intake, drafts a per-jurisdiction reserve-
  requirement/correspondent-banking evidence checklist, screens
  members for an unresolved correspondent-banking due-diligence
  concern, drafts the reserve-account-opening action, and drafts the
  settlement-batch-release action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real reserve-
  account opening/settlement-batch release. Every output is censored
  downstream by `reserve.governor` before anything touches the SSoT,
  and `:actuation/open-reserve-account`/`:actuation/release-
  settlement-batch` proposals NEVER auto-commit at any phase -- see
  README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/open-reserve-account | :actuation/release-settlement-batch | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [reserve.facts :as facts]
            [reserve.registry :as registry]
            [reserve.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the member or jurisdiction. High confidence, low
  stakes."
  [_db {:keys [patch]}]
  {:summary    (str "会員金融機関記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :member/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-account
  "Per-jurisdiction reserve-requirement/correspondent-banking evidence
  checklist draft. `:no-spec?` injects the failure mode we must
  defend against: proposing a checklist for a jurisdiction with NO
  official spec-basis in `reserve.facts` -- the Central Bank Reserve
  Governor must reject this (never invent a jurisdiction's
  requirements)."
  [db {:keys [subject no-spec?]}]
  (let [m (store/member db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction m))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "reserve.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :account/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :account/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(def default-corporate-intel-screen
  "No-op corporate-intelligence cross-reference: always 'nothing on
  file'. This is the default so every existing caller of
  `screen-duediligence`/`infer`/`mock-advisor` keeps its exact prior
  behavior unless it explicitly wires in
  `reserve.corporate-intel/screen-company` (or an equivalent). Not
  required from this namespace directly -- keeping the dependency
  optional at the reserveadvisor level, injected only by whoever
  builds the advisor."
  (constantly {:flags {}}))

(defn- screen-duediligence
  "Correspondent-banking due-diligence screening draft.
  `:correspondent-due-diligence-unresolved?` on the member record
  injects the failure mode: the Central Bank Reserve Governor must
  HOLD, un-overridably, on any unresolved concern.

  `screen-fn` (member name -> corporate-intel result, see
  `reserve.corporate-intel/screen-company`) is consulted ONLY once the
  local flag is otherwise clean -- it can turn a would-be :resolved
  into :unresolved, but a local unresolved flag is decided first,
  cheaply, without depending on an external actor at all. Unlike a
  KYC-style screen with an identification-document concept, this
  member-level screen has only two verdicts
  (`:correspondent-due-diligence-unresolved?` true/false) -- there is
  no middle 'incomplete' state here, so ANY non-clean signal from 8291
  (a definitive sanctions-flag hit on the correspondent bank itself,
  8291's own pending human review, or 8291's query being held/
  rejected) lands on the SAME `true` verdict a local flag would
  produce -- never silently `false`."
  [db {:keys [subject]} screen-fn]
  (let [m (store/member db subject)]
    (cond
      (nil? m)
      {:summary "対象会員金融機関記録が見つかりません" :rationale "no member record"
       :cites [] :effect :duediligence/set :value {:member-id subject :correspondent-due-diligence-unresolved? nil}
       :stake nil :confidence 0.0}

      (true? (:correspondent-due-diligence-unresolved? m))
      {:summary    (str (:member-name m) ": コルレス銀行デューデリジェンスが未解決")
       :rationale  "スクリーニングが未解決状態を検出。人手確認とホールドが必須。"
       :cites      [:duediligence-check]
       :effect     :duediligence/set
       :value      {:member-id subject :correspondent-due-diligence-unresolved? true}
       :stake      nil
       :confidence 0.95}

      :else
      (let [ci (screen-fn (:member-name m))]
        (cond
          (:pending-human-review? ci)
          {:summary    (str (:member-name m) ": corporate-intelligence 照会が人手レビュー待ち")
           :rationale  "cloud-itonami-isic-8291 側の DisclosureGovernor が high-stakes escalate 中(コルレス銀行自身の制裁フラグ疑い)。確定するまで未解決として扱う(この会員金融機関の語彙に中間状態は無い)。"
           :cites      [:duediligence-check :corporate-intelligence]
           :effect     :duediligence/set
           :value      {:member-id subject :correspondent-due-diligence-unresolved? true}
           :stake      nil
           :confidence 0.5}

          (:held? ci)
          {:summary    (str (:member-name m) ": corporate-intelligence 照会が拒否された(契約/設定の問題)")
           :rationale  (str "cloud-itonami-isic-8291 の DisclosureGovernor が本テナントの照会を拒否: " (pr-str (:reason ci)))
           :cites      [:duediligence-check :corporate-intelligence]
           :effect     :duediligence/set
           :value      {:member-id subject :correspondent-due-diligence-unresolved? true}
           :stake      nil
           :confidence 0.4}

          (get-in ci [:flags :sanctions?])
          {:summary    (str (:member-name m) ": corporate-intelligence 照会でコルレス銀行自身の制裁フラグを検出")
           :rationale  "cloud-itonami-isic-8291 の企業照会でこのコルレス銀行自身に制裁フラグが確認された。人手確認とホールドが必須。"
           :cites      [:duediligence-check :corporate-intelligence]
           :effect     :duediligence/set
           :value      {:member-id subject :correspondent-due-diligence-unresolved? true}
           :stake      nil
           :confidence 0.9}

          :else
          {:summary    (str (:member-name m) ": デューデリジェンスは解決済み")
           :rationale  "デューデリジェンス・スクリーニング完了 + corporate-intelligence 照会クリア(または未収載)。"
           :cites      [:duediligence-check :corporate-intelligence]
           :effect     :duediligence/set
           :value      {:member-id subject :correspondent-due-diligence-unresolved? false}
           :stake      nil
           :confidence 0.9})))))

(defn- propose-reserve-account-opening
  "Draft the actual RESERVE-ACCOUNT-OPENING action -- opening a real
  reserve account for a member bank. ALWAYS `:stake :actuation/open-
  reserve-account` -- this is a REAL-WORLD act, never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`reserve.phase`); the governor also
  always escalates on `:actuation/open-reserve-account`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/member db subject)
        safe? (and m (not (registry/reserve-ratio-insufficient? m))
                   (not (:correspondent-due-diligence-unresolved? m)))]
    {:summary    (str subject " 向け準備預金口座開設提案"
                      (when m (str " (member=" (:member-name m) ")")))
     :rationale  (if m
                   (str "reserve-ratio=" (:reserve-ratio m)
                        " minimum-reserve-ratio-required=" (:minimum-reserve-ratio-required m))
                   "会員金融機関記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :member/mark-account-opened
     :value      {:member-id subject}
     :stake      :actuation/open-reserve-account
     :confidence (if safe? 0.9 0.3)}))

(defn- propose-settlement-batch-release
  "Draft the actual SETTLEMENT-BATCH-RELEASE action -- releasing a
  real interbank settlement batch for a member bank. ALWAYS `:stake
  :actuation/release-settlement-batch` -- this is a REAL-WORLD act,
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`reserve.phase`);
  the governor also always escalates on `:actuation/release-
  settlement-batch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [m (store/member db subject)
        safe? (and m (not (registry/settlement-batch-exceeds-available-reserve-balance? m))
                   (not (:correspondent-due-diligence-unresolved? m)))]
    {:summary    (str subject " 向け決済バッチ実行提案"
                      (when m (str " (member=" (:member-name m) ")")))
     :rationale  (if m
                   (str "proposed-settlement-amount=" (:proposed-settlement-amount m)
                        " available-reserve-balance=" (:available-reserve-balance m))
                   "会員金融機関記録が見つかりません")
     :cites      (if m [subject] [])
     :effect     :member/mark-settlement-released
     :value      {:member-id subject}
     :stake      :actuation/release-settlement-batch
     :confidence (if safe? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}
  `screen-fn` (default: `default-corporate-intel-screen`, a no-op) is
  only consulted by `:duediligence/screen`, once the local flag is
  otherwise clean."
  ([db request] (infer db request default-corporate-intel-screen))
  ([db {:keys [op] :as request} screen-fn]
   (case op
     :member/intake                       (normalize-intake db request)
     :account/verify                      (verify-account db request)
     :duediligence/screen                 (screen-duediligence db request screen-fn)
     :actuation/open-reserve-account       (propose-reserve-account-opening db request)
     :actuation/release-settlement-batch  (propose-settlement-batch-release db request)
     {:summary "未対応の操作" :rationale (str op) :cites []
      :effect :noop :stake nil :confidence 0.0})))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere.
  opts:
    :corporate-intel-screen -- member name -> corporate-intel result (see
      `reserve.corporate-intel/screen-company`). Default: no-op (never
      changes a screen-duediligence verdict), so `(mock-advisor)` with
      no args keeps every existing caller's exact prior behavior."
  ([] (mock-advisor {}))
  ([{:keys [corporate-intel-screen]
     :or   {corporate-intel-screen default-corporate-intel-screen}}]
   (reify Advisor (-advise [_ st req] (infer st req corporate-intel-screen)))))

(def ^:private system-prompt
  (str "あなたは中央銀行業務(準備預金口座開設・銀行間決済)の実行エージェントの"
       "助言者です。与えられた事実のみに基づき、提案を1つだけEDNマップで"
       "返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:member/upsert|:account/set|:duediligence/set|"
       ":member/mark-account-opened|:member/mark-settlement-released) "
       ":stake(:actuation/open-reserve-account か :actuation/release-settlement-batch か nil) "
       ":confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :account/verify                      {:member (store/member st subject)}
    :duediligence/screen                 {:member (store/member st subject)}
    :actuation/open-reserve-account       {:member (store/member st subject)}
    :actuation/release-settlement-batch  {:member (store/member st subject)}
    {:member (store/member st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Central Bank Reserve
  Governor escalates/holds -- an LLM hiccup can never auto-open a
  reserve account or auto-release a settlement batch."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :reserveadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
