(ns jehaby.wpt.web.routes.ui
  (:require [jehaby.wpt.web.middleware.exception :as exception]
            [jehaby.wpt.web.middleware.formats :as formats]
            [jehaby.wpt.web.routes.utils :as utils]
            [jehaby.wpt.web.htmx :refer [pagelet page] :as htmx]
            [jehaby.wpt.db :as db]
            [integrant.core :as ig]
            [jehaby.wpt.web.controllers.article :as article.c]
            [reitit.core]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [next.jdbc :as jdbc]
            [reitit.coercion.malli :as malli]
            [clojure.string :as str]))

(defn article-card
  ([article]
   (article-card article nil))
  ([{:article/keys [title author date doi]} opts]
   [:div.article opts
    [:div
     [:p.title title]
     [:p.author author]]
    [:div.date-doi-container
     [:p.date date]
     [:p.doi doi]]]))

(defn nav-li [uri path  name]
  [:li [:a {:href path
            :class (when (= path uri) "active")}
        (format "[ %s ]" name)]])

(defn nav [{:keys [uri] :as _req}]
  [:nav
   [:ul
    (nav-li uri "/" "Articles")
    (nav-li uri "/find" "Find")]])

(defn layout [req title content]
  (page {:lang "en"}
        [:head
         [:meta {:charset "UTF-8"}]
         [:title title]
         [:link {:rel "stylesheet" :href "/css/style.css"}]
         [:script {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js" :defer true}]]
        [:body
         (nav req)

         [:section.content
          content]]))

(def items-per-page 10)

(defn htmx-pagination-params [has-more-data? page]
  (when has-more-data?
    {:hx-get (format "/articles/?page=%d" (inc page))
     :hx-trigger "revealed"
     :hx-swap "afterend"}))

(defn articles [request]
  (let [db (->  request utils/route-data :db)
        page (get-in request [:parameters :query :page] 1)
        items (jdbc/execute! db (db/->select-articles-q {:offset (* (dec page) items-per-page)}))

        {total-items :count} (jdbc/execute-one! db (db/->total-articles-q))
        displayed-items (* page items-per-page)
        has-more-data? (< displayed-items total-items)

        content (conj
                 (->> (drop-last items) (map article-card) (into [:div]))
                 (article-card (last items) (htmx-pagination-params has-more-data? page)))]
    content))

(defn load-more-articles [request]
  (pagelet (articles request)))

(defn main-page [request]
  (layout request
          "Articles"
          (articles request)))

(defn find-and-save-articles [request]
  (let [{:keys [db elsevier-client]} (-> request utils/route-data)
        search-params (-> request
                          (get-in [:form-params "q"])
                          (str/split #","))
        search-params (map str/trim search-params)]
    (article.c/find-and-save-articles! elsevier-client db search-params)))

(defn find-page [req]
  (layout
   req
   "Find articles"
   [:div
    [:form {:hx-post "/find"
            :hx-target "#search-res-msg"
            :method "post"}
     [:div
      [:textarea {:name "q"
                  :rows 10 :cols 40
                  :placeholder "Enter keywords separated by commas."}]]
     [:div
      [:button {:type "submit"}
       "Search"]
      [:span.loader.htmx-indicator {:style {:margin-left "1em"}} " Loading..."]]]
    [:div#search-res-msg]]))

;; Routes
(defn ui-routes [_opts]
  [["/" {:get main-page}]
   ["/articles" {:get load-more-articles
                 :parameters
                 {:query
                  {:page [int? {:min 1 :max 100000}]}}}]

   ["/find" {:get find-page
             :post {:handler find-and-save-articles}}]])

(defn route-data [opts]
  (merge opts
         {:coercion malli/coercion
          :muuntaja   formats/instance
          :middleware
          [;; Default middleware for ui
           ;; query-params & form-params
           parameters/parameters-middleware
           ;; exception handling
           coercion/coerce-exceptions-middleware
           ;; decoding request body
           muuntaja/format-request-middleware
           ;; exception handling
           coercion/coerce-request-middleware
           ;; encoding response body
           muuntaja/format-response-middleware
           ;; exception handling
           exception/wrap-exception]}))

(derive :reitit.routes/ui :reitit/routes)

(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  (fn [] [base-path (route-data opts) (ui-routes opts)]))
