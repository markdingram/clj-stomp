(ns clj-stomp.alpha.client
  (:require [clojure.tools.logging :as log]
            [clj-stomp.alpha.transport :as transport])
  (:import (java.io Closeable)
           (java.util.concurrent Future)))

(def headers #:stomp.header{:accept-version "accept-version"
                            :host "host"
                            :login "login"
                            :passcode "passcode"
                            :heart-beat "heart-beat"
                            :version "version"
                            :session "session"
                            :server "server"
                            :destination "destination"
                            :id "id"
                            :ack "ack"
                            :transaction "transaction"
                            :receipt "receipt"
                            :message-id "message-id"
                            :subscription "subscription"
                            :receipt-id "receipt-id"
                            :message "message"
                            :content-length "content-length"
                            :content-type "content-type"})

(defn ->send-frame [destination body]
  {:stomp.frame/command :stomp.command/send
   :stomp.frame/headers {:destination destination}
   :stomp.frame/body body})

(defn ->subscribe-frame [destination id]
  {:stomp.frame/command :stomp.command/subscribe
   :stomp.frame/headers {:id id
                         :destination destination}})

(defn ->connect-frame [{:stomp.connection/keys [host login passcode]}]
  {:stomp.frame/command :stomp.command/connect
   :stomp.frame/headers (cond-> #:stomp.header{:accept-version "1.2"
                                               :host           host
                                               :heart-beat     "0,10000"}
                                login (assoc :stomp.header/login login)
                                passcode (assoc :stomp.header/passcode passcode))})

(defn- success? [^Future fut]
  (.get fut)
  true)

(defn- customise-frame [client msg]
  (let [customise-fn (get-in client [:opts :customise-msg-fn] identity)]
    (customise-fn msg)))

(defn send! [{:keys [:transport] :as client} frame]
  (->> frame
       (customise-frame frame)
       (transport/-send! transport)))


(defn subscribe! [{:keys [opts] :as client} destination cb]
  (log/info "Subscribing to" destination)
  (let [id-fn (:id-fn opts)
        id (str "sub-" (id-fn))
        fut (send! client (->subscribe-frame destination id))]
    (if (success? fut)
      (do
        (log/info "Subscribed to" destination " with " id)
        (swap! (:state client) assoc-in [:subs id] cb)))))

(defn close! [{:keys [:transport] :as client}]
  ;; TODO: send disconnect and wait for response
  (transport/-close! transport))

(defn get-cb [client msg]
  (let [msg-id (get-in msg [:stomp.frame/headers (headers :stomp.header/subscription)])]
    (get-in @(:state client) [:subs msg-id])))

(defn on-message-received [client msg]
  (log/info "on-message-received" msg)
  ;(println @(:state client))
  (when-let [cb (get-cb client msg)]
    (cb msg)))

(defn connect! [{:keys [transport] :as client} connection-params]
  @(transport/-connect! transport connection-params (partial on-message-received client))
  (log/info "Connected")
  @(transport/-send! transport (->connect-frame connection-params))
  (log/info "Sent login"))

(defn info [client]
  {:state :connected})

(def default-opts {:stomp/transport :stomp.transport/netty
                   :customise-msg-fn identity
                   :id-fn (let [counter (atom 0)]
                            (fn []
                              (swap! counter inc)))})

(defrecord Client [transport opts state]
  Closeable
  (close [this]
    (close! this)))

(defn new-client [opts]
  (let [merged-opts (merge default-opts opts)
        transport (transport/new-transport merged-opts)]
    (->Client transport merged-opts (atom {}))))