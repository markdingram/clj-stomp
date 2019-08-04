(ns user
 (:require [clj-stomp.alpha.client :as c]
           [clj-stomp.alpha.transport.netty]))

(def opts {:host         "datafeeds.networkrail.co.uk"
           :port         61618
           :login        (System/getenv "NETWORK_RAIL_LOGIN")
           :passcode     (System/getenv "NETWORK_RAIL_PASSCODE")})


(defonce client (c/new-client opts))


(defn subscribe-fn [client headers]
  (assoc headers "activemq.subscriptionName" (:stomp/destination headers)))

(comment
  (c/activate! client)
  ;@(c/subscribe client {"destination" "/topic/TRAIN_MVT_HB_TOC"
  ;                      "activemq.subscriptionName"}
  ;              #(println %))
  (c/deactivate! client))