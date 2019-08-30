(ns clj-stomp.alpha.specs
  (:require [clojure.spec.alpha :as s]))

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

(defn valid-send-headers? [f]
  (some? (get-in f [:stomp.frame/headers :destination])))

(def basic-frame?
  (s/keys :req [:stomp.frame/command :stomp.frame/headers]
          :opt [:stomp.frame/body]))

(defmulti frame? :stomp.frame/command)
(defmethod frame? :stomp.command/send [_]
  (s/and
    (s/keys :req [:stomp.frame/command :stomp.frame/headers :stomp.frame/body])
    valid-send-headers?))

(defmethod frame? :default [_] basic-frame?)

(s/def :stomp/frame (s/multi-spec frame? :tag))


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

(s/def :stomp/transport #(satisfies? clj-stomp.alpha.transport/Transport %))

; can't fdef protocol methods: https://clojure.atlassian.net/browse/CLJ-2109
; (s/fdef clj-stomp.alpha.transport/-send!
(s/fdef clj-stomp.alpha.transport.netty/netty-send!
        :args (s/cat :t :stomp/transport :f :stomp/frame))
