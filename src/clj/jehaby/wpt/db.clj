(ns jehaby.wpt.db
  (:require
   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [honey.sql.helpers :as h]
   [migratus.core]
   [integrant.core :as ig]))

(defmethod ig/init-key :db/datasource [_ {:keys [jdbc-url] :as _opts}]
  (-> (jdbc/get-datasource jdbc-url)
      (jdbc/with-options jdbc/snake-kebab-opts)))

(defn ->migratus-cfg [system]
  {:store :database
   :db {:datasource (-> system :db/datasource jdbc/get-datasource)}})

(defn migrate [system]
  (migratus.core/init (->migratus-cfg system))
  system)

(defn ->insert-articles-q [articles]
  (-> (h/insert-into :article)
      (h/values articles)
      (h/on-conflict :doi (h/do-nothing))
      (sql/format)))

(defn ->select-articles-q [{:keys [limit offset order-by]
                            :or {limit 10
                                 offset 0
                                 order-by [:id :DESC]}}]
  (-> (h/select :*)
      (h/from :article)
      (h/offset offset)
      (h/order-by order-by)
      (h/limit limit)
      (sql/format)))

(defn ->total-articles-q []
  (-> (h/select [:%count.* :count])
      (h/from :article)
      (sql/format)))
