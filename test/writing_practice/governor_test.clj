(ns writing-practice.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [writing-practice.governor :as governor]
            [writing-practice.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-work! {:work/id "work:reincarnated-librarian"
                             :work/title "Reincarnated Librarian"})))

(def ^:private ok-proposal
  {:op :publish-episode :effect :propose :stake :low :confidence 0.95})

(defn- req
  ([] (req {}))
  ([extra] (merge {:work-id "work:reincarnated-librarian"
                   :op :publish-episode
                   :index 1
                   :body-blob-key "deadbeef"}
                  extra)))

(deftest ok-on-clean-publish
  (let [v (governor/check (req) {} ok-proposal (fresh-store))]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-holds
  (testing "unregistered work"
    (let [v (governor/check (req {:work-id "work:no-such"}) {} ok-proposal (fresh-store))]
      (is (:hard? v))
      (is (some #(= :no-work (:rule %)) (:violations v)))))
  (testing "non-propose effect (actuation attempt)"
    (let [v (governor/check (req) {} (assoc ok-proposal :effect :write!) (fresh-store))]
      (is (:hard? v))
      (is (some #(= :no-actuation (:rule %)) (:violations v)))))
  (testing "inline body violates the shousetsu body-as-blob invariant"
    (let [v (governor/check (req {:body "長文をそのまま datom に入れようとする"})
                            {} ok-proposal (fresh-store))]
      (is (:hard? v))
      (is (some #(= :body-as-blob (:rule %)) (:violations v))))))

(deftest escalations
  (testing "reprint licensing is human-signed"
    (let [v (governor/check (req {:op :license-reprint})
                            {} (assoc ok-proposal :op :license-reprint) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))))
  (testing "publish without an uploaded body blob"
    (let [v (governor/check (req {:body-blob-key nil}) {} ok-proposal (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))))
  (testing "low confidence"
    (let [v (governor/check (req) {} (assoc ok-proposal :confidence 0.2) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v)))))
