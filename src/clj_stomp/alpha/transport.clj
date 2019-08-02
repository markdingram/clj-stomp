(ns clj-stomp.alpha.transport
  (:require [clojure.tools.logging :as log]
            [clojure.set :as set])
  (:import [io.netty.channel ChannelOption ChannelInitializer Channel ChannelDuplexHandler ChannelInboundHandlerAdapter ChannelFuture ChannelFutureListener]
           [io.netty.handler.codec.stomp StompSubframeDecoder StompSubframeEncoder StompSubframeAggregator DefaultStompFrame StompCommand StompHeaders StompFrame]
           [java.io Closeable]
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.bootstrap Bootstrap)
           (io.netty.channel.socket.nio NioSocketChannel)
           (io.netty.handler.timeout IdleStateHandler IdleStateEvent)
           (java.util.concurrent CompletableFuture)
           (java.nio.charset StandardCharsets)))

(defn- success? [^ChannelFuture fut]
  (.isSuccess (.sync fut)))

(defn- ^StompFrame frame
  ([command headers]
   (let [m (DefaultStompFrame. command)
         h (.headers m)]
     (doseq [[k v] headers]
       (.set h (.toString k) v))
     m))
  ([command headers content]
   (let [m (frame command headers)]
     (.writeBytes (.content m) (.getBytes content StandardCharsets/UTF_8))
     m)))

(def LF (byte 0x0A))

(defn- heartbeat-msg [ctx]
  (let [buf (.buffer (.alloc ctx))]
    (.writeByte buf LF)
    buf))

(def commands {:stomp.command/stomp StompCommand/STOMP
               :stomp.command/connect StompCommand/CONNECT
               :stomp.command/connected StompCommand/CONNECTED
               :stomp.command/send StompCommand/SEND
               :stomp.command/subscribe StompCommand/SUBSCRIBE
               :stomp.command/unsubscribe StompCommand/UNSUBSCRIBE
               :stomp.command/ack StompCommand/ACK
               :stomp.command/nack StompCommand/NACK
               :stomp.command/begin StompCommand/BEGIN
               :stomp.command/disconnect StompCommand/DISCONNECT
               :stomp.command/message StompCommand/MESSAGE
               :stomp.command/receipt StompCommand/RECEIPT
               :stomp.command/error StompCommand/ERROR
               :stomp.command/unknown StompCommand/UNKNOWN})

(defn command->netty [c]
  (get commands c StompCommand/UNKNOWN))

(let [invert (set/map-invert commands)]
  (defn netty->command [c]
    (get invert c :stomp.command/unknown)))

(defn- msg->netty [{:stomp.frame/keys [command headers body]}]
  (let [^DefaultStompFrame f (DefaultStompFrame. (command->netty command))
        h (.headers f)]
    (doseq [[k v] headers]
      (print k (name k) v)
      (.set h (name k) v))
    (when body
      (.writeBytes (.content f) (.getBytes body StandardCharsets/UTF_8)))
    f))

(defn- netty->headers [^StompFrame frame]
  (let [headers (.headers frame)]
    (into {} (seq headers))))

(defn- netty->body [^StompFrame frame]
  (let [content (.content frame)]
    (when (> (.readableBytes content) 0)
      (.toString content StandardCharsets/UTF_8))))

(defn- netty->msg [^StompFrame frame]
  (let [command (netty->command frame)
        headers (netty->headers frame)
        body (netty->body frame)]
    (cond->
      {:stomp.frame/command command
       :stomp.frame/headers headers}
      body
      (assoc :stomp.frame/body body))))


(defn- frame->subscription [^StompFrame frame]
  (if (= StompCommand/MESSAGE (.command frame))
    (.getAsString (.headers frame) StompHeaders/SUBSCRIPTION)))


(defn debug-handler []
  (proxy [ChannelDuplexHandler] []
    (channelRead [ctx ^StompFrame frame]
      (log/info "Debug Inbound: " frame)
      (proxy-super channelRead ctx frame))
    (write [ctx msg prom]
      (log/info "Debug Outbound: " msg)
      (proxy-super write ctx msg prom))))

(defn channel-reader [state]
  (proxy [ChannelDuplexHandler] []
    (channelRead [ctx ^StompFrame frame]
      (log/info "Reader Inbound: " frame)

      (if-let [cb (get-in @state [:subs (frame->subscription frame)])]
        (cb (netty->msg frame)))

      (proxy-super channelRead ctx frame))
    (write [ctx msg prom]
      (log/info "Reader Outbound: " msg)
      (proxy-super write ctx msg prom))))

(defn- connect-handler [{:keys [host login passcode]}]
  (proxy [ChannelInboundHandlerAdapter] []
    ;(channelActive [ctx]
    ;  (let [ch-fut (.writeAndFlush ctx (connect-msg host login passcode))]
    ;    (log/info "Sent connect" @ch-fut (.isSuccess ch-fut))
    ;    (proxy-super channelActive ctx)))
    (userEventTriggered [ctx evt]
      (if (instance? IdleStateEvent evt)
        (do ;(log/info "Sending heartbeat..")
          (let [hb (.writeAndFlush ctx (heartbeat-msg ctx))]))))))
;(log/info "Sent" (.isSuccess (.sync hb)))))))))

(defn- new-bootstrap [{:keys [debug]
                       :or {debug false} :as opts} event-loop state]
  (doto (Bootstrap.)
    (.option ChannelOption/SO_REUSEADDR true)
    (.group event-loop)
    (.channel NioSocketChannel)
    (.handler (proxy [ChannelInitializer] []
                (initChannel [^Channel ch]
                  (doto (.pipeline ch)
                    (.addLast "decoder" (StompSubframeDecoder.))
                    (.addLast "encoder" (StompSubframeEncoder.))
                    (.addLast "aggregator" (StompSubframeAggregator. 1048576))
                    (.addLast "heartbeat" (IdleStateHandler. 0, 10, 0))
                    (.addLast "connector" (connect-handler opts))
                    (.addLast "reader" (channel-reader state)))
                  (when debug
                    (doto (.pipeline ch)
                      (.addBefore "heartbeat" "debug" (debug-handler)))))))))

(defn new-channel [{:keys [opts event-loop state]}]
  (let [{:keys [host port]} opts
        bootstrap (new-bootstrap opts event-loop state)]
    (log/info "Connecting..")
    (let [chan (-> ^ChannelFuture (.connect bootstrap host port)
                   (.sync)
                   (.channel))]
      (.addListener (.closeFuture chan)(reify ChannelFutureListener
                                         (operationComplete [_ f]
                                           (log/info "Closed!"))))
      chan)))

(defn get-channel [client]
  (if-let [channel (:channel @(:state client))]
    (if (and (instance? Channel channel) (.isActive channel))
      channel)))

(defn netty-connected? [transport]
  (some? (get-channel transport)))

(defn- netty-connect! [transport]
  (if (not (netty-connected? transport))
    (do
      (log/info "Activating..")
      (swap! (:state transport) assoc :channel (new-channel transport)))))

(defn- netty-close! [{:keys [event-loop]}]
  ;(if (is-active? client)
  ;  (do
  ;    (.writeAndFlush channel (disconnect-msg client-id))
  ;    (Thread/sleep 500)))
  ;(.await disconnect-promise 5 TimeUnit/SECONDS)))
  @(.shutdownGracefully event-loop))

(defn- netty-send! [transport msg]
  (if-let [channel (get-channel transport)]
    (.writeAndFlush channel (msg->netty msg))
    (doto (CompletableFuture.)
      (.completeExceptionally (ex-info "No active channel!" {})))))

(defprotocol Transport
  (-connected? [transport])

  (-connect! [transport])

  (-send! [transport msg]))

(defmulti new-transport :stomp/transport)



(defrecord NettyTransport [opts event-loop state]
  Closeable
  (close [transport]
    (netty-close! transport))

  Transport
  (-connected? [transport]
    (netty-connected? transport))

  (-connect! [transport]
    (netty-connect! transport))

  (-send! [transport msg]
    (netty-send! transport msg)))



(defmethod new-transport :stomp.transport/netty [opts]
  (let [event-loop (NioEventLoopGroup.)]
    (->NettyTransport opts event-loop (atom {}))))
