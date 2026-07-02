(ns writing-practice.governor
  "WritingGovernor — the independent rights/manuscript-integrity layer for
  the ISCO-08 2641 independent author/writer actor. Wired as its own
  `:govern` node in `writing-practice.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of work provenance, publishing
  rights, or the body-as-blob invariant, so this MUST be a separate system
  able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. work provenance — the request's work must be registered.
    2. no-actuation    — proposal :effect must be :propose.
    3. body-as-blob    — the shousetsu invariant (kotoba-lang/shousetsu,
       ADR-2607023000): long-form text never becomes a datom. A request
       carrying an inline :body instead of a content-addressed
       :body-blob-key is structurally refused.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. :license-reprint — publishing-rights decisions are human-signed.
    5. publish/draft without a :body-blob-key (manuscript not yet
       uploaded — a human decides whether to proceed).
    6. low confidence (< `confidence-floor`)."
  (:require [writing-practice.store :as store]))

(def confidence-floor 0.6)

(def ^:private episode-ops #{:draft-episode :publish-episode})

(defn- hard-violations [{:keys [request proposal]} work-record]
  (cond-> []
    (nil? work-record)
    (conj {:rule :no-work :detail (str "未登録 work " (:work-id request))})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (and (contains? episode-ops (:op proposal))
         (some? (:body request)))
    (conj {:rule :body-as-blob
           :detail "本文は datom に入れない（shousetsu 不変条件）— :body-blob-key を渡す"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `writing-practice.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request _context proposal store]
  (let [work-record (store/work store (:work-id request))
        hard (hard-violations {:request request :proposal proposal} work-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        rights? (= :license-reprint (:op proposal))
        no-blob? (and (contains? episode-ops (:op proposal))
                      (nil? (:body request))
                      (nil? (:body-blob-key request)))]
    {:ok? (and (not hard?) (not low?) (not rights?) (not no-blob?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? rights? no-blob?))}))
