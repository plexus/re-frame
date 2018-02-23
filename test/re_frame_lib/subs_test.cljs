(ns re-frame-lib.subs-test
  (:require [cljs.test         :as test :refer-macros [is deftest testing]]
            [reagent.ratom     :as r :refer-macros [reaction]]
            [re-frame-lib.subs     :as subs]
            [re-frame-lib.core     :as re-frame]))

;(test/use-fixtures :each {:before (fn [] (subs/clear-all-handlers!))})

;=====test basic subscriptions  ======

(deftest test-reg-sub
  (let [state (re-frame/new-state)
        full-state (-> state
                      (re-frame/reg-sub-raw
                        :test-sub
                        (fn [db [_]]
                          (reaction (deref (:app-db state))))))
        test-sub (subs/subscribe state [:test-sub])]
    (is (= @(:app-db state) @test-sub))
    (reset! (:app-db state) 1)
    (is (= 1 @test-sub))))

#_(deftest test-chained-subs
  (re-frame/reg-sub-raw
    :a-sub
    (fn [db [_]] (reaction (:a @db))))

  (re-frame/reg-sub-raw
    :b-sub
    (fn [db [_]] (reaction (:b @db))))

  (re-frame/reg-sub-raw
    :a-b-sub
    (fn [db [_]]
      (let [a (subs/subscribe [:a-sub])
            b (subs/subscribe [:b-sub])]
        (reaction {:a @a :b @b}))))

  (let [test-sub (subs/subscribe [:a-b-sub])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1 :b 2} @test-sub))
    (swap! db/app-db assoc :b 3)
    (is (= {:a 1 :b 3} @test-sub))))

#_(deftest test-sub-parameters
  (re-frame/reg-sub-raw
    :test-sub
    (fn [db [_ b]] (reaction [(:a @db) b])))

  (let [test-sub (subs/subscribe [:test-sub :c])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= [1 :c] @test-sub))))


#_(deftest test-sub-chained-parameters
  (re-frame/reg-sub-raw
    :a-sub
    (fn [db [_ a]] (reaction [(:a @db) a])))

  (re-frame/reg-sub-raw
    :b-sub
    (fn [db [_ b]] (reaction [(:b @db) b])))

  (re-frame/reg-sub-raw
    :a-b-sub
    (fn [db [_ c]]
      (let [a (subs/subscribe [:a-sub c])
            b (subs/subscribe [:b-sub c])]
        (reaction {:a @a :b @b}))))

  (let [test-sub (subs/subscribe [:a-b-sub :c])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a [1 :c], :b [2 :c]} @test-sub))))

#_(deftest test-nonexistent-sub
  (is (nil? (re-frame/subscribe [:non-existence]))))

;============== test cached-subs ================
#_(def side-effect-atom (atom 0))

#_(deftest test-cached-subscriptions
  (reset! side-effect-atom 0)

  (re-frame/reg-sub-raw
    :side-effecting-handler
    (fn side-effect
      [db [_] [_]]
      (swap! side-effect-atom inc)
      (reaction @db)))

 (let [test-sub (subs/subscribe [:side-effecting-handler])]
    (reset! db/app-db :test)
    (is (= :test @test-sub))
    (is (= @side-effect-atom 1))
    (subs/subscribe [:side-effecting-handler])  ;; this should be handled by cache
    (is (= @side-effect-atom 1))
    (subs/subscribe [:side-effecting-handler :a]) ;; should fire again because of the param
    (is (= @side-effect-atom 2))
    (subs/subscribe [:side-effecting-handler :a]) ;; this should be handled by cache
    (is (= @side-effect-atom 2))))

;============== test clear-subscription-cache! ================

#_(deftest test-clear-subscription-cache!
  (re-frame/reg-sub
   :clear-subscription-cache!
   (fn clear-subs-cache [db _] 1))

  (testing "cold cache"
    (is (nil? (subs/cache-lookup [:clear-subscription-cache!]))))
  (testing "cache miss"
    (is (= 1 @(subs/subscribe [:clear-subscription-cache!])))
    (is (some? (subs/cache-lookup [:clear-subscription-cache!]))))
  (testing "clearing"
    (subs/clear-subscription-cache!)
    (is (nil? (subs/cache-lookup [:clear-subscription-cache!])))))

;============== test register-pure macros ================

#_(deftest test-reg-sub-macro
  (subs/reg-sub
    :test-sub
    (fn [db [_]] db))

  (let [test-sub (subs/subscribe [:test-sub])]
    (is (= @db/app-db @test-sub))
    (reset! db/app-db 1)
    (is (= 1 @test-sub))))

#_(deftest test-reg-sub-macro-singleton
  (subs/reg-sub
    :a-sub
    (fn [db [_]] (:a db)))

  (subs/reg-sub
    :a-b-sub
    (fn [_ _ _]
      (subs/subscribe [:a-sub]))
    (fn [a [_]]
      {:a a}))

  (let [test-sub (subs/subscribe [:a-b-sub])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1} @test-sub))
    (swap! db/app-db assoc :b 3)
    (is (= {:a 1} @test-sub))))

#_(deftest test-reg-sub-macro-vector
  (subs/reg-sub
    :a-sub
    (fn [db [_]] (:a db)))

  (subs/reg-sub
    :b-sub
    (fn [db [_]] (:b db)))

  (subs/reg-sub
    :a-b-sub
    (fn [_ _ _]
      [(subs/subscribe [:a-sub])
       (subs/subscribe [:b-sub])])
    (fn [[a b] [_]]
      {:a a :b b}))

  (let [test-sub (subs/subscribe [:a-b-sub])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1 :b 2} @test-sub))
    (swap! db/app-db assoc :b 3)
    (is (= {:a 1 :b 3} @test-sub))))

#_(deftest test-reg-sub-macro-map
  (subs/reg-sub
    :a-sub
    (fn [db [_]] (:a db)))

  (subs/reg-sub
    :b-sub
    (fn [db [_]] (:b db)))

  (subs/reg-sub
    :a-b-sub
    (fn [_ _ _]
      {:a (subs/subscribe [:a-sub])
       :b (subs/subscribe [:b-sub])})
    (fn [{:keys [a b]} [_]]
      {:a a :b b}))

  (let [test-sub (subs/subscribe [:a-b-sub])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1 :b 2} @test-sub))
    (swap! db/app-db assoc :b 3)
    (is (= {:a 1 :b 3} @test-sub))))

#_(deftest test-sub-macro-parameters
  (subs/reg-sub
    :test-sub
    (fn [db [_ b]] [(:a db) b]))

  (let [test-sub (subs/subscribe [:test-sub :c])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= [1 :c] @test-sub))))

#_(deftest test-sub-macros-chained-parameters
  (subs/reg-sub
    :a-sub
    (fn [db [_ a]] [(:a db) a]))

  (subs/reg-sub
    :b-sub
    (fn [db [_ b]] [(:b db) b]))

  (subs/reg-sub
    :a-b-sub
    (fn [[_ c] _]
      [(subs/subscribe [:a-sub c])
       (subs/subscribe [:b-sub c])])
    (fn [[a b] [_ c]] {:a a :b b}))

  (let [test-sub (subs/subscribe [:a-b-sub :c])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a [1 :c] :b [2 :c]} @test-sub))))

#_(deftest test-sub-macros-<-
  "test the syntactial sugar"
  (subs/reg-sub
    :a-sub
    (fn [db [_]] (:a db)))

  (subs/reg-sub
        :a-b-sub
        :<- [:a-sub]
        (fn [a [_]] {:a a}))

  (let [test-sub (subs/subscribe [:a-b-sub])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1} @test-sub))))

#_(deftest test-sub-macros-chained-parameters-<-
  "test the syntactial sugar"
  (subs/reg-sub
    :a-sub
    (fn [db [_]] (:a db)))

  (subs/reg-sub
    :b-sub
    (fn [db [_]] (:b db)))

  (subs/reg-sub
        :a-b-sub
        :<- [:a-sub]
        :<- [:b-sub]
        (fn [[a b] [_ c]] {:a a :b b}))

  (let [test-sub (subs/subscribe [:a-b-sub :c])]
    (reset! db/app-db {:a 1 :b 2})
    (is (= {:a 1 :b 2} @test-sub) )))

#_(deftest test-registering-subs-doesnt-create-subscription
  (let [sub-called? (atom false)]
    (with-redefs [subs/subscribe (fn [& args] (reset! sub-called? true))]
      (subs/reg-sub
        :a-sub
        (fn [db [_]] (:a db)))

      (subs/reg-sub
        :b-sub
        (fn [db [_]] (:b db)))

      (subs/reg-sub
        :fn-sub
        (fn [[_ c] _]
          [(subs/subscribe [:a-sub c])
           (subs/subscribe [:b-sub c])])
        (fn [db [_]] (:b db)))

      (subs/reg-sub
        :a-sugar-sub
        :<- [:a-sub]
        (fn [[a] [_ c]] {:a a}))

      (subs/reg-sub
        :a-b-sub
        :<- [:a-sub]
        :<- [:b-sub]
        (fn [[a b] [_ c]] {:a a :b b})))

    (is (false? @sub-called?))))

;; Dynamic subscriptions

#_(deftest test-dynamic-subscriptions
  (subs/reg-sub
    :dyn-sub
    (fn [db ev dynv]
      (first dynv)))

  (testing "happy case"
    (is (= 1 @(subs/subscribe [:dyn-sub] [(r/atom 1)]))))
  (testing "subscription that doesn't exist"
    (is (nil? (subs/subscribe [:non-existent] [(r/atom 1)]))))
  (testing "Passing a non-reactive value to a dynamic subscription"
    (is (thrown-with-msg? js/Error #"No protocol method IDeref" @(subs/subscribe [:dyn-sub] [1])))))
