(ns jehaby.wpt.web.article-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [peridot.core :as p]
   [jehaby.wpt.fixture :as fixture]
   [jehaby.wpt.test-utils :refer [system-state system-fixture]]
   [hickory.core :as h]
   [hickory.select :as s]))

(use-fixtures :once (system-fixture {:elsevier-api-resp fixture/elsevier-api-resps}))

(defn do-request [app uri & env]
  (-> (apply p/request app uri env)
      :response))

(deftest find-articles-test []
  (testing "happy path"
    (let [handler (-> (system-state) ;
                      :handler/ring
                      p/session)
          {:keys [status body]} (:response
                                 (p/request handler
                                            "/find"
                                            :request-method :post
                                            :params {:q "bears,owls"}))]
      (testing "Articles loaded from elsevier"
        (is (= 200 status))
        (is (= "Added 20 new articles" body)))
      (testing "We can see articles"
        (let [{:keys [status body]} (do-request handler "/")
              parsed-body (-> body h/parse h/as-hickory)
              articles (s/select (s/class "article") parsed-body)]
          (is (= 200 status))
          (is (= 10 (count articles)))
          (testing "Infinite scroll attributes on the last visible article"
            (is (= "/articles/?page=2" (-> articles last :attrs :hx-get))))))
      (testing "We can load more articles"
        (let [{:keys [status body]} (do-request handler "/articles?page=2")
              parsed-body (-> body h/parse h/as-hickory)
              articles (s/select (s/class "article") parsed-body)]
          (is (= 200 status))
          (is (= 10 (count articles))))))))


