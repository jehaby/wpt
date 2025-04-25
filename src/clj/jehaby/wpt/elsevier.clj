(ns jehaby.wpt.elsevier
  (:require
   [jsonista.core :as json]
   [clojure.set :as set]
   [clj-http.client :as http]
   [integrant.core :as ig]
   [clojure.string :as str]))

(def query-url "https://api.elsevier.com/content/search/scopus")

(def field-mapping
  {:prism:publicationName :article/title
   :dc:creator            :article/author
   :prism:coverDate       :article/date
   :prism:doi             :article/doi})

(def fields-from-api (keys field-mapping))
(def fields-from-api-str (->> fields-from-api (map name) (str/join ",")))

(defn ->entry [entry]
  (-> entry
      (select-keys fields-from-api)
      (set/rename-keys  field-mapping)))

(defmethod ig/init-key :api-client/elsevier
  [_ {:keys [api-key connect-timeout]
      :or {connect-timeout 5000}}]
  (fn query-elsevier-api!
    ([q] (query-elsevier-api! q nil))
    ([q {:as _opts
         :keys [count start field]
         :or {field fields-from-api-str ;; requesting only fields we're interested in
              count 10
              start 0}}]
     (http/with-connection-pool
       {:timeout connect-timeout}
       (http/get query-url {:query-params
                            {:query q #_(format "TITLE-ABS-KEY(%s)" q)
                             :count count
                             :start start
                             :field field
                             :apiKey api-key}
                            :accept :json})))))

;; Function to fetch publications for a given keyword
(defn fetch-scopus-publications [elsevier-client input]
  (try
    (let [resp (elsevier-client input)
          entries  (-> resp
                       :body
                       (json/read-value json/keyword-keys-object-mapper)
                       :search-results
                       :entry)]
      (map ->entry entries))
    (catch Exception e
      (ex-info "elsevier API error" {:keyword input} e))))
