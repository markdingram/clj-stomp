(ns clj-stomp.alpha.client
  (:require [clojure.tools.logging :as log]
            [clj-stomp.alpha.transport :as transport])
  (:import (java.io Closeable)))

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

(defn send-msg [destination body]
  {:stomp.frame/command :stomp.command/send
   :stomp.frame/headers {:destination destination}
   :stomp.frame/body body})

(defn subscribe-msg [destination id]
  {:stomp.frame/command :stomp.command/subscribe
   :stomp.frame/headers {:id id
                         :destination destination}})

(defn connect-msg [host login passcode]
  {:stomp.frame/command :stomp.command/connect
   :stomp.frame/headers (cond-> #:stomp.header{:accept-version "1.2"
                                               :host           host
                                               :heart-beat     "0,10000"}
                                login (assoc :stomp.header/login login)
                                passcode (assoc :stomp.header/passcode passcode))})


(defn activate! [{:keys [transport opts]}]
  (let [{:keys [host login passcode]} opts]
    (transport/-connect! transport)
    (log/info "Connected")
    (transport/-send! transport (connect-msg host login passcode))))

(defn deactivate! [client])


(defn customise-msg [client msg]
  (let [process-fn (get-in client [:opts :customise-msg-fn] identity)]
    (process-fn msg)))

(defn send [{:keys [:transport] :as client} destination msg]
  (->>
    (send-msg destination msg)
    (customise-msg client)
    (send transport msg)))

(defn info [client]
  {:state :connected})

(def default-opts {:stomp/transport :stomp.transport/netty
                   :customise-msg-fn identity})

(defn new-client [opts]
  (let [merged-opts (merge default-opts opts)
        transport (transport/new-transport merged-opts)]
    (reify
      Closeable
      (close [_]
        @(deactivate! transport)
        (.close transport)))
    {:transport transport
     :opts merged-opts
     :state (atom {})}))