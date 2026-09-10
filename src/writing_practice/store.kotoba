(ns writing-practice.store
  "SSoT for the ISCO-08 2641 independent author/writer sole-proprietor actor.
  Store is a protocol injected into the `writing-practice.actor` StateGraph —
  `MemStore` is the default, deterministic, zero-dep backend; a
  Datomic/kotoba-server-backed implementation can be swapped in without
  touching the actor or governor (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  Domain:

    work     — a registered serialized work (:work/id \"work:<slug>\" per
               shousetsu.serialization, :work/title, rights metadata)
    record   — a committed operating record under a work (episode publish,
               reprint license) — written ONLY via commit-record!, never
               mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (work [s work-id])
  (records-of [s work-id])
  (ledger [s])
  (register-work! [s work])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (work [_ work-id] (get-in @a [:works work-id]))
  (records-of [_ work-id] (filter #(= work-id (:work-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-work! [s work]
    (swap! a assoc-in [:works (:work/id work)] work) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:works {} :records [] :ledger []} seed)))))
