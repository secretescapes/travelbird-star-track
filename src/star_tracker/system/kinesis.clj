(ns star-tracker.system.kinesis
  (:require 
    [cheshire.core                          :refer :all]
    [amazonica.core                         :refer [with-credential defcredential]]
    [amazonica.aws.kinesis                  :as kinesis]
    [clojure.core.async                     :as async :refer [alts! go chan]]
    [com.stuartsierra.component             :as component]
    [com.climate.claypoole                  :as cp]
    [metrics.meters                         :refer (meter mark! defmeter rates)]
    [taoensso.timbre                        :as timbre
         :refer (log trace debug info warn error fatal report sometimes)])
  (:import [java.util UUID]))

(defn send-record
  [stream meters msg]
  (let [event-id (UUID/randomUUID)]
    ; (sometimes 0.1 (format "Publishing event-id %s -> %s" event-id msg))
    (try
      (debug stream msg)
      (kinesis/put-record stream msg event-id)
      (mark! (:publish meters))
    (catch Throwable t
      (do
        (mark! (:publish-error meters))
        (error t)
        (info msg)
        )))))

(defrecord KinesisProducer [aws-key aws-secret aws-endpoint aws-kinesis-stream meters prod-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (warn "Starting KINESIS PRODUCER Component" )
      (defcredential aws-key aws-secret aws-endpoint)

      (let [worker-pool (cp/threadpool 10)
            producing-channel (chan 65532)
            sender (partial send-record aws-kinesis-stream meters)]
        (go (while true
          (let [[[topic msg] ch] (alts! [producing-channel])]
            ;; use a thread-pool to send messages to Kinesis
            ;; otherwise under high load, we are going to drop messages
            ;; till we have enough capacity to cope with the load (AWS Auto Scaling)
            ;; Current Kinesis latency is 20ms.
            (cp/future worker-pool (sender msg)))))
         (assoc component :channel producing-channel))
      (catch Throwable t 
        (do 
          (warn "[KINESIS-PROD] FAILED")
          (error t)

          (assoc component :channel nil)))))
  (stop [component]

    )
  )



(defn kinesis-producer
  "Returns a new kinesis producer with the given options"
  [options]
  (map->KinesisProducer options))