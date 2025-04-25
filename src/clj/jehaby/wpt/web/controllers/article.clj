(ns jehaby.wpt.web.controllers.article
  (:require
   [jehaby.wpt.web.htmx :refer [pagelet] :as htmx]
   [jehaby.wpt.db :as db]
   [next.jdbc :as jdbc]
   [clojure.core.async :as async]
   [clojure.tools.logging :as log]
   [jehaby.wpt.elsevier :as elsevier]))

(def Keyword
  [:re #"[A-Za-z0-9\-]{3,20}"])

;; (malli.core/validate Keyword "Fora")

(defn parallel
  "Returns a channel which will contain a transient vector of results.
   Works similar to `pmap`.
  from   https://bsless.github.io/mapping-parallel-side-effects/"
  ([n f xs]
   (let [out (async/chan)]
     (async/pipeline-blocking
      n
      out
      (map f)
      (async/to-chan! xs))
     (async/reduce conj! (transient []) out))))

(def n-parallel-requests 4)

(defn find-and-save-articles! [elsevier-client db kwds]
  (let [ch (parallel n-parallel-requests
                     #(elsevier/fetch-scopus-publications elsevier-client %)
                     kwds)
        {res true errs false}
        (->> (persistent! (async/<!! ch))
             (group-by sequential?))

        res (apply concat res)]

    (log/debugf "got %d scopus publications from API. " (count res))
    (doseq [err errs]
      (log/errorf "elsevier API error. input: %s. err: %v", (-> err ex-data :keyword) err))
    (let [insert-res (next.jdbc/execute-one! db (db/->insert-articles-q res))
          update-count (::jdbc/update-count insert-res)]
      (pagelet nil
               (if (zero? update-count)
                 "No new articles were found"
                 (format "Added %d new articles", update-count))))))
