(ns clj-stomp.alpha.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as st]
            [clj-stomp.alpha.client :as c]
            [clj-stomp.alpha.netty :as n]
            [clj-stomp.alpha.specs]))

(st/instrument)

(def debug false)
(def conn #:stomp.conn{:host "localhost" :port 61613 :login "artemis" :passcode "simetraehcapa" :debug debug})

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (println ex "Uncaught exception on" (.getName thread)))))

(deftest can-send-msg
  (let [test1-received (promise)
        test1-msg #:stomp.msg{:destination "/topic/test1" :body "test1"}
        test2-received (promise)
        test2-msg #:stomp.msg{:destination "/topic/test2" :body "test2"}]
    (with-open [listener (n/new-client (assoc conn :stomp.conn/subs {"/topic/test1" #(deliver test1-received %)
                                                                     "/topic/test2" #(deliver test2-received %)}))
                publisher (n/new-client conn)]
      (c/activate! listener)
      ;@(c/subscribe listener "/topic/test1" #(deliver test1-received %))
      ;@(c/subscribe listener "/topic/test2" #(deliver test2-received %))

      ; have to wait for the server to handle the subscriptions, not sure
      ; if there is any other option..
      (Thread/sleep 2000)

      (c/activate! publisher)

      @(c/send publisher test1-msg)
      @(c/send publisher test2-msg)

      (is (= test1-msg (deref test1-received 2000 nil)))
      (is (= test2-msg (deref test2-received 2000 nil))))))

(comment
  (def listener (let [c (n/new-client conn)]
                  (c/activate! c)
                  @(c/subscribe c "/topic/test" #(println %))
                  c))

  (def client (let [c (n/new-client conn)]
                (c/activate! c)
                @(c/publish c #:stomp.msg{:destination "/topic/test" :body "test msg"})
                c)))
