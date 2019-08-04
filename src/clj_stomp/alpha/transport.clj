(ns clj-stomp.alpha.transport
  (:import (java.util.concurrent Future)))

(defprotocol Transport
  (-connected? [transport])

  (-connect! ^Future [transport connection-params listener])

  (-close! ^Future [transport])

  (-send! ^Future [transport frame]))

(defmulti new-transport :stomp/transport)
