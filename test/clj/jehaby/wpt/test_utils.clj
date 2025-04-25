(ns jehaby.wpt.test-utils
  (:require
   [jehaby.wpt.core :as core]
   [jehaby.wpt.config :as config]
   [next.jdbc :as jdbc]
   [integrant.core :as ig]
   [integrant.repl.state :as state]
   [jehaby.wpt.db :as db]
   [clojure.tools.logging :as log]))

(defn system-state
  []
  (or @core/system state/system))

(defmethod ig/init-key :api-client/elsevier-mock [_ resp]
  (fn [query]
    (log/debug "doing api call" query (keys resp))
    (or
     (get resp query)
     (throw (ex-info "Couldnt' find key for " {:query query})))))

(defn ->config []
  (config/system-config {:profile :test}))

(defn with-elsevier-client-mock [resp]
  (fn [cfg] (-> cfg
                (assoc [:api-client/elsevier-mock] (or resp {}))
                (assoc-in [:reitit.routes/ui :elsevier-client] (ig/ref :api-client/elsevier-mock)))))

(defn purge-db [cfg]
  (jdbc/execute! (:db/datasource cfg) ["delete from article"])
  cfg)

(defn start-app [cfg]
  (->> cfg
       (ig/expand)
       (ig/init)
       (db/migrate)
       (purge-db)
       (reset! core/system)))

(defn system-fixture
  [{:as _cfg
    :keys [elsevier-api-resp]}]
  (fn [f]
    (when (nil? (system-state))
      (-> (->config)
          ((with-elsevier-client-mock elsevier-api-resp))
          start-app)
      (f)
      (core/stop-app))))