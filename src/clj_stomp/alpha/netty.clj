(ns clj-stomp.alpha.netty
  (:require [clojure.tools.logging :as log]
            [clj-stomp.alpha.client :as c])
  (:import [io.netty.channel ChannelOption ChannelInitializer Channel ChannelDuplexHandler ChannelInboundHandlerAdapter ChannelFuture ChannelFutureListener]
           [io.netty.handler.codec.stomp StompSubframeDecoder StompSubframeEncoder StompSubframeAggregator DefaultStompFrame StompCommand StompHeaders StompFrame]
           [java.io Closeable]
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.bootstrap Bootstrap)
           (io.netty.channel.socket.nio NioSocketChannel)
           (io.netty.handler.timeout IdleStateHandler IdleStateEvent)
           (java.util.concurrent CompletableFuture)
           (java.nio.charset StandardCharsets)))
;
;(defn- success? [^ChannelFuture fut]
;  (.isSuccess (.sync fut)))
;
;(defn- ^StompFrame frame
;  ([command headers]
;   (let [m (DefaultStompFrame. command)
;         h (.headers m)]
;     (doseq [[k v] headers]
;       (.set h (.toString k) v))
;     m))
;  ([command headers content]
;   (let [m (frame command headers)]
;     (.writeBytes (.content m) (.getBytes content StandardCharsets/UTF_8))
;     m)))
;
;(def LF (byte 0x0A))
;
;(defn- heartbeat-msg [ctx]
;  (let [buf (.buffer (.alloc ctx))]
;    (.writeByte buf LF)
;    buf))
;
;(defn- disconnect-msg [id]
;  (frame StompCommand/DISCONNECT
;         {StompHeaders/RECEIPT id}))
;
;(defn- send-msg [{:stomp.msg/keys [destination body]}]
;  (frame StompCommand/SEND
;         {StompHeaders/DESTINATION destination}
;         body))
;
;(def commands {:stomp.command/stomp StompCommand/STOMP
;               :stomp.command/connect StompCommand/CONNECT
;               :stomp.command/connected StompCommand/CONNECTED
;               :stomp.command/send StompCommand/SEND
;               :stomp.command/subscribe StompCommand/SUBSCRIBE
;               :stomp.command/unsubscribe StompCommand/UNSUBSCRIBE
;               :stomp.command/ack StompCommand/ACK
;               :stomp.command/nack StompCommand/NACK
;               :stomp.command/begin StompCommand/BEGIN
;               :stomp.command/disconnect StompCommand/DISCONNECT
;               :stomp.command/message StompCommand/MESSAGE
;               :stomp.command/receipt StompCommand/RECEIPT
;               :stomp.command/error StompCommand/ERROR
;               :stomp.command/unknown StompCommand/UNKNOWN})
;
;(defn- map->netty [{:stomp.frame/keys [command headers body]}]
;  (let [^DefaultStompFrame f (DefaultStompFrame. (get commands command StompCommand/UNKNOWN))
;        h (.headers f)]
;    (doseq [[k v] headers]
;      (.set h k v))
;    (when body
;      (.writeBytes (.content f) (.getBytes body StandardCharsets/UTF_8)))
;    f))
;
;(defn- netty->map [^StompFrame frame]
;  (if (= StompCommand/MESSAGE (.command frame))
;    {:stomp.msg/destination (.getAsString (.headers frame) StompHeaders/DESTINATION)
;     :stomp.msg/body (.toString (.content frame) StandardCharsets/UTF_8)}))
;
;(defn- frame->subscription [^StompFrame frame]
;  (if (= StompCommand/MESSAGE (.command frame))
;    (.getAsString (.headers frame) StompHeaders/SUBSCRIPTION)))
;
;(defn- connect-msg [host login passcode]
;  {:pre [(some? host)]}
;  (let [headers (cond-> {StompHeaders/ACCEPT_VERSION "1.2"
;                         StompHeaders/HOST host
;                         StompHeaders/HEART_BEAT "0,10000"}
;                  login (assoc StompHeaders/LOGIN login)
;                  passcode (assoc StompHeaders/PASSCODE passcode))]
;    (frame StompCommand/CONNECT headers)))
;
;
;(defn debug-handler []
;  (proxy [ChannelDuplexHandler] []
;    (channelRead [ctx ^StompFrame frame]
;      (log/info "Debug Inbound: " frame)
;      (proxy-super channelRead ctx frame))
;    (write [ctx msg prom]
;      (log/info "Debug Outbound: " msg)
;      (proxy-super write ctx msg prom))))
;
;(defn channel-reader [state]
;  (proxy [ChannelDuplexHandler] []
;    (channelRead [ctx ^StompFrame frame]
;      (log/info "Reader Inbound: " frame)
;
;      (if-let [cb (get-in @state [:subs (frame->subscription frame)])]
;        (cb (netty->map frame)))
;
;      (proxy-super channelRead ctx frame))
;    (write [ctx msg prom]
;      (log/info "Reader Outbound: " msg)
;      (proxy-super write ctx msg prom))))
;
;(defn- connect-handler [{:keys [host login passcode]}]
;  (proxy [ChannelInboundHandlerAdapter] []
;    (channelActive [ctx]
;      (let [ch-fut (.writeAndFlush ctx (connect-msg host login passcode))]
;        (log/info "Sent connect" @ch-fut (.isSuccess ch-fut))
;        (proxy-super channelActive ctx)))
;    (userEventTriggered [ctx evt]
;      (if (instance? IdleStateEvent evt)
;        (do ;(log/info "Sending heartbeat..")
;            (let [hb (.writeAndFlush ctx (heartbeat-msg ctx))]))))))
;              ;(log/info "Sent" (.isSuccess (.sync hb)))))))))
;
;(defn- new-bootstrap [{:keys [debug]
;                       :or {debug false} :as opts} event-loop state]
;  (doto (Bootstrap.)
;    (.option ChannelOption/SO_REUSEADDR true)
;    (.group event-loop)
;    (.channel NioSocketChannel)
;    (.handler (proxy [ChannelInitializer] []
;                (initChannel [^Channel ch]
;                  (doto (.pipeline ch)
;                    (.addLast "decoder" (StompSubframeDecoder.))
;                    (.addLast "encoder" (StompSubframeEncoder.))
;                    (.addLast "aggregator" (StompSubframeAggregator. 1048576))
;                    (.addLast "heartbeat" (IdleStateHandler. 0, 10, 0))
;                    (.addLast "connector" (connect-handler opts))
;                    (.addLast "reader" (channel-reader state)))
;                  (when debug
;                    (doto (.pipeline ch)
;                      (.addBefore "heartbeat" "debug" (debug-handler)))))))))
;
;(defn new-channel [{:keys [opts event-loop state]}]
;  (let [{:keys [host port]} opts
;        bootstrap (new-bootstrap opts event-loop state)]
;    (log/info "Connecting..")
;    (let [chan (-> ^ChannelFuture (.connect bootstrap host port)
;                (.sync)
;                (.channel))]
;      (.addListener (.closeFuture chan)(reify ChannelFutureListener
;                                         (operationComplete [_ f]
;                                           (log/info "Closed!"))))
;      chan)))
;
;(defn get-channel [client]
;  (if-let [channel (:channel @(:state client))]
;    (if (and (instance? Channel channel) (.isActive channel))
;      channel)))
;
;(defn active? [client]
;  (some? (get-channel client)))
;
;(defn- netty-activate! [client]
;  (if (not (active? client))
;    (do
;      (log/info "Activating..")
;      (swap! (:state client) assoc :channel (new-channel client)))))
;
;(defn- netty-deactivate! [{:keys [event-loop]}]
;  ;(if (is-active? client)
;  ;  (do
;  ;    (.writeAndFlush channel (disconnect-msg client-id))
;  ;    (Thread/sleep 500)))
;  ;(.await disconnect-promise 5 TimeUnit/SECONDS)))
;  @(.shutdownGracefully event-loop))
;
;(defn- netty-publish [client msg]
;  (if-let [channel (get-channel client)]
;    (.writeAndFlush channel (send-msg msg))
;    (doto (CompletableFuture.)
;      (.completeExceptionally (ex-info "No active channel!" {})))))
;
;(defn- netty-subscribe [{:keys [id-fn] :as client} destination cb]
;  (if-let [channel (get-channel client)]
;    (do
;      (log/info "Subscribing to" destination)
;      (let [id (str "sub-" (id-fn))
;            fut (.writeAndFlush channel (subscribe-msg id destination))]
;        (if (success? fut)
;          (swap! (:state client) assoc-in [:subs id] cb))
;        fut))
;    (doto (CompletableFuture.)
;      (.completeExceptionally (ex-info "No active channel!" {})))))

;(defrecord NettyClient [opts id-fn event-loop state]
;  Closeable
;  (close [client]
;    (netty-deactivate! client))
;
;  c/Client
;  (-activate! [client]
;    (netty-activate! client))
;
;  (-deactivate! [client]
;    (netty-deactivate! client))
;
;  (-active? [client]
;    (active? client))
;
;  (-publish [client msg]
;    (netty-publish client msg)))
;
;  ;(-subscribe [client destination cb]
;  ;  (netty-subscribe client destination cb)))
;
;(defn new-client [{:keys [id-fn] :as opts
;                   :or {id-fn (let [counter (atom 0)]
;                                (fn []
;                                  (swap! counter inc)))}}]
;  (let [event-loop (NioEventLoopGroup.)]
;    (->NettyClient opts id-fn event-loop (atom {}))))
