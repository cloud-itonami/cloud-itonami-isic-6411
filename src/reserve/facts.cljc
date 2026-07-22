(ns reserve.facts
  "Per-jurisdiction central-banking/reserve-requirement/correspondent-
  banking regulatory catalog -- the G2-style spec-basis table the
  Central Bank Reserve Governor checks every `:account/verify`
  proposal against ('did the advisor cite an OFFICIAL public source
  for this jurisdiction's reserve-requirement/correspondent-banking
  framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official central-
  bank/monetary authority (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a
  real source, done -- never invent a jurisdiction's requirements to
  make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the member-
  identity-verification/reserve-account-application/correspondent-
  due-diligence/settlement-authorization evidence set this
  blueprint's own Offer names; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:actuation/open-reserve-account`/`:actuation/release-settlement-
  batch` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "日本銀行 (Bank of Japan)"
          :legal-basis "日本銀行法 (Bank of Japan Act) -- 準備預金制度に関する法律 (Reserve Deposit Requirement System Act)"
          :national-spec "会員金融機関の準備預金比率および日銀当座預金口座開設・決済に係る要件"
          :provenance "https://www.boj.or.jp/about/law/index.htm"
          :required-evidence ["会員金融機関身元確認記録 (member-identity-verification-record)"
                              "準備預金口座開設申請記録 (reserve-account-application-record)"
                              "コルレス銀行デューデリジェンス記録 (correspondent-due-diligence-record)"
                              "決済承認記録 (settlement-authorization-record)"]}
   "USA" {:name "United States"
          :owner-authority "Board of Governors of the Federal Reserve System"
          :legal-basis "Federal Reserve Act -- 12 CFR Part 204 (Regulation D, Reserve Requirements) -- 31 CFR §1010.610 (Bank Secrecy Act correspondent-account due diligence)"
          :national-spec "Reserve-account eligibility, reserve-ratio maintenance, and correspondent-banking due-diligence requirements for member/commercial banks"
          :provenance "https://www.federalreserve.gov/supervisionreg/reglisting.htm"
          :required-evidence ["Member identity-verification record"
                              "Reserve-account application record"
                              "Correspondent-due-diligence record"
                              "Settlement-authorization record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Bank of England"
          :legal-basis "Bank of England Act 1998 -- Sterling Monetary Framework"
          :national-spec "Reserve-account (Bank of England reserves) eligibility and interbank settlement (CHAPS/RTGS) authorization requirements"
          :provenance "https://www.bankofengland.co.uk/markets/bank-of-england-market-operations"
          :required-evidence ["Member identity-verification record"
                              "Reserve-account application record"
                              "Correspondent-due-diligence record"
                              "Settlement-authorization record"]}
   "DEU" {:name "Germany"
          :owner-authority "Deutsche Bundesbank / European Central Bank (ECB)"
          :legal-basis "Bundesbankgesetz -- EU Regulation (EC) No 1745/2003 (ECB minimum reserves)"
          :national-spec "Mindestreservepflicht und TARGET2-Zugangsvoraussetzungen für Mitgliedsinstitute"
          :provenance "https://www.ecb.europa.eu/mopo/implement/mr/html/index.en.html"
          :required-evidence ["Identitätsprüfungsprotokoll (member-identity-verification-record)"
                              "Reservekonto-Antragsprotokoll (reserve-account-application-record)"
                              "Korrespondenzbank-Sorgfaltspflichtprotokoll (correspondent-due-diligence-record)"
                              "Abwicklungsgenehmigungsprotokoll (settlement-authorization-record)"]}
   ;; SAU -- 5th jurisdiction (added 2026-07-23). Verified directly from
   ;; sama.gov.sa this session (`About/Pages/default.aspx` and
   ;; `About/Pages/SAMAHistory.aspx` were live-fetchable; the deeper
   ;; Saudi Central Bank Law detail below comes from SAMA's own 57th
   ;; Annual Report PDF, whose live URL now 500s -- fetched instead via
   ;; the Internet Archive Wayback Machine capture
   ;; `web.archive.org/web/20221116202912id_/https://www.sama.gov.sa/en-US/EconomicReports/AnnualReport/ANNUAL_Report_57th_2021.pdf`,
   ;; per this fleet's bot-detection/unreachable-source fallback policy --
   ;; this is a plain HTTP 500 on an otherwise-legitimate first-party SAMA
   ;; document, not a bot-detection challenge).
   ;;
   ;; Confirmed facts (verbatim from the Annual Report, SAMA's own text):
   ;; "Approval of the Saudi Central Bank Law and changing the name of the
   ;; Saudi Arabian Monetary Authority to the Saudi Central Bank" (2020
   ;; reforms list) -- the old name is officially superseded/retired:
   ;; "Under this law, 'Saudi Central Bank' shall replace the name 'Saudi
   ;; Arabian Monetary Authority' wherever mentioned in related laws,
   ;; regulations, instructions and orders with all rights and
   ;; obligations, and shall maintain the same abbreviation 'SAMA'."
   ;; Decree: "as per Royal Decree no. (36/M) dated November 26, 2020
   ;; (Rabi II 11, 1442H), Saudi Central Bank's Law was approved" (financial
   ;; statements' subsequent-events note); a separate passage in the same
   ;; report gives the Custodian of the Two Holy Mosques' royal approval
   ;; date as "November 24, 2020 (Rabi' II 9, 1442H)" -- both dates are
   ;; reproduced here verbatim rather than silently picked, since SAMA's
   ;; own report does not reconcile the 2-day gap between them.
   ;; Mandate: "the Saudi Central Bank Law sets out the objectives of SAMA
   ;; as follows: 1. Maintaining monetary stability. 2. Promoting the
   ;; stability of, and boosting confidence in, the financial sector.
   ;; 3. Supporting economic growth." and "the onus of developing and
   ;; managing the monetary policy and choosing its instruments and
   ;; procedures shall be on SAMA." This mission wording is corroborated
   ;; on the LIVE site: the About-page breadcrumb link's title attribute
   ;; (fetched this session) reads "The Saudi Central Bank (SAMA) aims to
   ;; maintain monetary stability, support the financial sector's
   ;; stability and promote trust therein, as well as Support economic
   ;; growth."
   ;;
   ;; HONEST GAPS (left absent/qualified, not guessed):
   ;; - No specific internal article number of the Saudi Central Bank Law
   ;;   text itself was located this session (only the Royal Decree
   ;;   number/date above) -- :legal-basis below cites the decree, not an
   ;;   article, unlike e.g. USA's "12 CFR Part 204" citation.
   ;; - The SAR/USD exchange-rate peg is explicitly described by SAMA
   ;;   itself as a *policy*, not a distinct statutory provision: "SAMA
   ;;   has also continued to implement a fixed exchange rate policy of
   ;;   the Saudi riyal against the US dollar at SAR 3.75 per one US
   ;;   dollar" and "SAMA maintained its US dollar-pegged monetary
   ;;   policy..." -- so it is recorded in :national-spec as policy
   ;;   context only, never asserted as a citable legal-basis item.
   "SAU" {:name "Saudi Arabia"
          :owner-authority "Saudi Central Bank (SAMA) -- renamed by law from Saudi Arabian Monetary Authority in 2020"
          :legal-basis "Saudi Central Bank Law, approved by Royal Decree no. (36/M) dated 26 November 2020 (Rabi' II 11, 1442H; Custodian of the Two Holy Mosques' royal approval reported elsewhere in the same source as 24 November 2020 / Rabi' II 9, 1442H) -- SAMA succeeds the former Saudi Arabian Monetary Authority in all rights/obligations and holds sole responsibility for developing/managing monetary policy and choosing its instruments and procedures"
          :national-spec "Reserve-account eligibility, correspondent-banking due-diligence and settlement-authorization requirements for member/commercial banks under SAMA supervision; the SAR/USD exchange rate (SAR 3.75 = 1 USD) is maintained as SAMA policy, not a separate statutory peg provision"
          :provenance "https://www.sama.gov.sa/en-US/About/Pages/default.aspx"
          :required-evidence ["Member identity-verification record"
                              "Reserve-account application record"
                              "Correspondent-due-diligence record"
                              "Settlement-authorization record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to open a
  reserve account or release a settlement batch on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6411 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `reserve.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
