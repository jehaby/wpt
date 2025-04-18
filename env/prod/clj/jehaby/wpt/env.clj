(ns jehaby.wpt.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[wpt starting]=-"))
   :start      (fn []
                 (log/info "\n-=[wpt started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[wpt has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
