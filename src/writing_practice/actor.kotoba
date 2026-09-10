(ns writing-practice.actor
  "WritingOpsActor — the ISCO-08 2641 independent author/writer actor as a
  `langgraph.graph/state-graph` (per ADR-2607011000 / CLAUDE.md Actors
  section). One graph run = one writing-practice operation request (intake →
  advise → govern → decide → commit/hold, with a human-approval interrupt for
  escalated proposals). No infinite internal loop; checkpointed per superstep
  so an interrupted run can resume after human sign-off.

  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit           (:ok? true)
                                             +-> :request-approval  (:escalate? true, interrupt-before)
                                             +-> :hold              (:hard? true)
  ```

  The unconditional invariant: the WritingOpsAdvisor can never directly
  commit a record the WritingGovernor refuses — every commit-record! call is
  gated behind `:decide`, and episode commits carry shousetsu-built datom
  ops (kotoba-lang/shousetsu) so the body-as-blob invariant is enforced all
  the way into the record."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [shousetsu.serialization :as serialization]
            [writing-practice.advisor :as advisor]
            [writing-practice.governor :as governor]
            [writing-practice.store :as store]))

(defn- episode-record [request proposal]
  (let [work-id (:work-id request)
        index (long (or (:index request) 1))
        episode-id (serialization/episode-id
                    (serialization/work-slug-of work-id) index)]
    {:work-id work-id
     :op (:op proposal)
     :episode-id episode-id
     :payload proposal
     :tx-ops (serialization/episode-meta->ops
              {:episode_id episode-id
               :work_id work-id
               :index index
               :title (or (:title request) (str "第" index "話"))
               :body_blob_key (:body-blob-key request)
               :char_count (or (:char-count request) 0)
               :status (if (= :publish-episode (:op proposal))
                         "published" "draft")})}))

(defn build-graph
  "Build a compiled WritingOpsActor graph. `store` implements
  `writing-practice.store/Store`. `advisor` implements
  `writing-practice.advisor/Advisor` (defaults to `mock-advisor`).
  `checkpointer` defaults to an in-memory one."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                  (fn [{:keys [request]}]
                    (let [p (advisor/-advise advisor store request)]
                      {:proposal p
                       :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                  (fn [{:keys [request context proposal]}]
                    (let [v (governor/check request context proposal store)]
                      {:verdict v
                       :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide
                  (fn [{:keys [verdict]}]
                    {:disposition (cond
                                    (:hard? verdict) :hold
                                    (:escalate? verdict) :request-approval
                                    :else :commit)}))
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit
                  (fn [{:keys [request proposal]}]
                    (let [record (if (contains? #{:draft-episode :publish-episode}
                                                (:op proposal))
                                   (episode-record request proposal)
                                   {:work-id (:work-id request)
                                    :op (:op proposal)
                                    :payload proposal})]
                      (store/commit-record! store record)
                      (store/append-ledger! store {:disposition :commit :record record})
                      {:record record
                       :audit [{:node :commit :record record}]})))
      (g/add-node :hold
                  (fn [{:keys [verdict]}]
                    (store/append-ledger! store {:disposition :hold :verdict verdict})
                    {:audit [{:node :hold :verdict verdict}]}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                        :interrupt-before #{:request-approval}})))

(defn run-request!
  "Run one operation request to completion or interrupt. `thread-id` scopes
  checkpointing for resume after human approval."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances straight to `:commit` on resume (approval is the human-signed
  rights/publication decision)."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
