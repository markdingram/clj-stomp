(ns clj-stomp.alpha.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as st]
            [clj-stomp.alpha.client :as c]
            [clj-stomp.alpha.transport.netty :as n]
            [clj-stomp.alpha.specs]))

(st/instrument)

(def debug false)
(def opts {:debug debug})
(def conn #:stomp.connection{:host "localhost" :port 61613 :login "artemis" :passcode "simetraehcapa"})

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (println ex "Uncaught exception on" (.getName thread)))))

(defn check-msg [sent received]
  (is (= (:stomp.frame/command received) :stomp.command/message))
  (is (= (:stomp.frame/body sent) (:stomp.frame/body received))))

(deftest can-send-msg
  (let [test1-received (promise)
        test1-msg (c/->send-frame "/topic/test1" "test1")
        test2-received (promise)
        test2-msg (c/->send-frame "/topic/test2" "test2")
        test2-receipt (promise)]
    (with-open [listener (c/new-client opts)
                publisher (c/new-client opts)]
      (c/connect! listener conn)
      @(c/subscribe! listener "/topic/test1" #(deliver test1-received %))
      @(c/subscribe! listener "/topic/test2" #(deliver test2-received %))

      (c/connect! publisher conn)

      @(c/send! publisher test1-msg)
      @(c/send! publisher test2-msg test2-receipt)

      (check-msg test1-msg (deref test1-received 2000 nil))
      (check-msg test2-msg (deref test2-received 2000 nil))

      (let [receipt-msg (deref test2-receipt 5000 false)]
        (is (= :stomp.command/receipt (:stomp.frame/command receipt-msg)))))))

(comment
  (def listener (let [c (n/new-client conn)]
                  (c/activate! c)
                  @(c/subscribe c "/topic/test" #(println %))
                  c))

  (def client (let [c (n/new-client conn)]
                (c/activate! c)
                @(c/publish c #:stomp.msg{:destination "/topic/test" :body "test msg"})
                c)))
