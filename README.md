clj-stomp
=========

A minimal JVM [stomp](http://stomp.github.io/) client, with a Netty transport. 




To create and subscribe:

````
(require [clj-stomp.alpha.client :as c])
..

(let [client (c/new-client #:stomp.connection{:host     "datafeeds.networkrail.co.uk"
                                              :port     61618
                                              :login    (System/getenv "NETWORK_RAIL_LOGIN")
                                              :passcode (System/getenv "NETWORK_RAIL_PASSCODE")})
  @(c/subscribe! client "/topic/TRAIN_MVT_HB_TOC"
                        #(println %)))
````

Retrieve info with `(c/info client)`

Close with `(c/close! client)`

Testing
=======

Before running tests start a local Stomp Broker:

````
docker run -it --rm \
  --ulimit nofile=122880:122880 \
  -p 8161:8161 \
  -p 61613:61613 \
  -p 61616:61616 \
  vromero/activemq-artemis
````

The UI is on port 8161, login with: artemis / simetraehcapa

Run tests with `bin\koacha`.


License
=======

EPL V2
