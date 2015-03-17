(ns star-tracker.system.kinesis
  (:require 
    [cheshire.core                          :refer :all]
    [amazonica.core                         :refer [with-credential defcredential]]
    [amazonica.aws.kinesis                  :as kinesis]
    [clojure.core.async                     :as async :refer [alts! go chan]]
    [com.stuartsierra.component             :as component]
    [taoensso.timbre                        :as timbre
         :refer (log  trace  debug  info  warn  error  fatal  report sometimes)])
  (:import [java.util UUID]))


(defrecord KinesisProducer [aws-key aws-secret aws-endpoint aws-kinesis-stream prod-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (warn "Starting KINESIS PRODUCER Component" )
      (defcredential aws-key aws-secret aws-endpoint)

      (let [producing-channel (chan 65532)]
        (go (while true
          (let [[[topic msg] ch] (alts! [producing-channel])]
            (let [event-id (UUID/randomUUID)]
              (sometimes 0.1 (format "Publishing event-id %s -> %s" event-id msg))
              (kinesis/put-record aws-kinesis-stream msg event-id)))))
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