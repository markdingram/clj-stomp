(ns clj-stomp.alpha.specs
  (:require [clojure.spec.alpha :as s]
            [clj-stomp.alpha.client :as client]))

(s/def :stomp.msg/body string?)
(s/def :stomp.msg/destination string?)

(s/def :stomp/msg (s/keys :req [:stomp.msg/destination :stomp.msg/body]))

(s/def :stomp.frame/command #{:stomp.command/stomp
                              :stomp.command/connect
                              :stomp.command/connected
                              :stomp.command/send
                              :stomp.command/subscribe
                              :stomp.command/unsubscribe
                              :stomp.command/ack
                              :stomp.command/nack
                              :stomp.command/begin
                              :stomp.command/disconnect
                              :stomp.command/message
                              :stomp.command/receipt
                              :stomp.command/error
                              :stomp.command/unknown})

(s/def :stomp.frame/headers (s/map-of (s/or :header keyword? :string string?) string?))
(s/def :stomp.frame/body string?)

; TODO: s/defmulti to validate headers for different types of command?
(s/def :stomp/frame (s/keys :req [:stomp.frame/command :stomp.frame/headers :stomp.frame/body]))

;(s/def ::client #(satisfies? clj-stomp.alpha.client/Client %))
;
;(s/fdef clj-stomp.alpha.client/activate!
;        :args (s/cat :c ::client)
;        :ret any?)
;
;(s/fdef clj-stomp.alpha.client/deactivate!
;        :args (s/cat :c ::client)
;        :ret any?)
;
;(s/fdef clj-stomp.alpha.client/active?
;        :args (s/cat :c ::client)
;        :ret boolean?)
;
;(s/fdef clj-stomp.alpha.client/publish
;        :args (s/cat :c ::client :m :stomp/msg)
;        :ret future?)
;
;;; the cb isn't spec'ed further to avoid: https://clojure.atlassian.net/browse/CLJ-2217
;(s/fdef clj-stomp.alpha.client/subscribe
;        :args (s/cat :c ::client :d string? :cb ifn?)
;        :ret any?)
