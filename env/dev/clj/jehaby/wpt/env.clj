(ns jehaby.wpt.env
  (:require
    [clojure.tools.logging :as log]
    [jehaby.wpt.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[wpt starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[wpt started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[wpt has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
