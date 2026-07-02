(ns writing-practice.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [writing-practice.actor :as actor]
            [writing-practice.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-work! {:work/id "work:reincarnated-librarian"
                             :work/title "Reincarnated Librarian"})))

(deftest commits-a-clean-episode-publish
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:work-id "work:reincarnated-librarian" :op :publish-episode
                 :index 3 :title "第三話" :body-blob-key "deadbeef"
                 :char-count 3000 :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (let [record (get-in result [:state :record])]
      (is (= "episode:reincarnated-librarian:3" (:episode-id record)))
      (testing "committed datom ops keep the body as a blob key"
        (let [flat (pr-str (:tx-ops record))]
          (is (clojure.string/includes? flat ":ep/bodyBlobKey"))
          (is (not (clojure.string/includes? flat ":ep/body "))))))
    (is (= 1 (count (store/records-of st "work:reincarnated-librarian"))))))

(deftest holds-inline-body-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:work-id "work:reincarnated-librarian" :op :publish-episode
                 :index 1 :body "inline 本文" :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "work:reincarnated-librarian")))))

(deftest interrupts-then-commits-reprint-license-on-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:work-id "work:reincarnated-librarian" :op :license-reprint
                 :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "work:reincarnated-librarian")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= :license-reprint (:op (get-in resumed [:state :record]))))
      (is (= 1 (count (store/records-of st "work:reincarnated-librarian")))))))
