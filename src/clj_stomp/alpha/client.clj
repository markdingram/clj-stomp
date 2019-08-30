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

; receipt will be added before sending
(defn ->disconnect-frame []
  {:stomp.frame/command :stomp.command/disconnect})

(defn next-id [client prefix]
  (let [id-fn (get-in client [:opts :id-fn])]
    (str prefix (id-fn))))

(defn- success? [^Future fut]
  (.get fut)
  true)

(defn- customise-frame [client frame]
  (let [customise-fn (get-in client [:opts :customise-msg-fn] identity)]
    (customise-fn frame)))

(defn- add-receipt [receipt-id frame]
  (log/info "add-receipt" receipt-id frame)
  (assoc-in frame [:stomp.frame/headers (headers :stomp.header/receipt)] receipt-id))

(defn send!
  ([{:keys [:transport] :as client} frame]
   (->> frame
        (customise-frame client)
        (transport/-send! transport)))
  ([{:keys [:transport] :as client} frame receipt-promise]
   (let [receipt-id (next-id client "message-")]
     (swap! (:state client) assoc-in [:receipts receipt-id] receipt-promise)
     (->> frame
        (add-receipt receipt-id)
        (send! client)))))

(defn subscribe! [{:keys [opts] :as client} destination cb]
  (log/info "Subscribing to" destination)
  (let [id-fn (:id-fn opts)
        id (str "sub-" (id-fn))
        receipt-promise (promise)
        fut (send! client (->subscribe-frame destination id) receipt-promise)]
    (if (success? fut)
      (do
        (log/info "Subscribed to" destination " with " id)
        (swap! (:state client) assoc-in [:subs id] cb)))
    receipt-promise))

(defn close! [{:keys [:transport] :as client}]
  (let [disconnect-promise (promise)]
    (send! client (->disconnect-frame) disconnect-promise)
    (deref disconnect-promise 5000 true)
    (transport/-close! transport)))

(defn get-cb [client msg]
  (let [msg-id (get-in msg [:stomp.frame/headers (headers :stomp.header/subscription)])]
    (get-in @(:state client) [:subs msg-id])))

(defn remove-receipt [state receipt-id]
  (update-in state [:receipts] dissoc receipt-id))

(defn get-receipt-promise [client frame]
  (let [receipt-id (get-in frame [:stomp.frame/headers (headers :stomp.header/receipt-id)])
        [before _](swap-vals! (:state client) remove-receipt receipt-id)]
    (get-in before [:receipts receipt-id])))

(defn on-message-received [client frame]
  (log/info "on-message-received" frame)
  (if (= :stomp.command/receipt (:stomp.frame/command frame))
    (when-let [receipt-promise (get-receipt-promise client frame)]
      (deliver receipt-promise frame))
    (when-let [cb (get-cb client frame)]
      (cb frame))))

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