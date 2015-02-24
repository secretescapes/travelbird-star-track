(ns star-tracker.system.kinesis
  (:require 
    [cheshire.core                          :refer :all]
    [amazonica.core                         :refer [with-credential defcredential]]
    [amazonica.aws.kinesis                  :as kinesis]
    [clojure.core.async                     :as async :refer [alts! go chan]]
    [com.stuartsierra.component             :as component]
    [taoensso.timbre                        :as timbre
         :refer (log  trace  debug  info  warn  error  fatal  report)])
  (:import [java.util UUID]))


(defrecord KinesisProducer [aws-key aws-secret aws-endpoint aws-kinesis-stream prod-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (warn "Starting KINESIS PRODUCER Component" )
      (defcredential aws-key aws-secret aws-endpoint)

      (let [producing-channel (or prod-chan (chan 65532))]
        (go (while true
          (let [[[topic msg] ch] (alts! [producing-channel])]
            (let [event-id (UUID/randomUUID)]
              (info (format "Publishing event-id %s -> %s" event-id msg))
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

(defn start-worker
  [aws-key aws-secret aws-endpoint aws-kinesis-stream]
  (kinesis/worker!  :app "kinesis-sample-consumer"
                          :stream aws-kinesis-stream
                          :checkpoint false
                          :credentials {:access-key aws-key :secret-key aws-secret :endpoint aws-endpoint }
                          :endpoint (format "kinesis.%s.amazonaws.com" aws-endpoint)
                          :processor (fn [records]
                                        (info "PROCESSOR IN ACTION!!!!!" records)
                                        (System/exit 1)
                                        (doseq [row records]
                                          (let [data (:data row)
                                                object (or (try (parse-string data) (catch Throwable t (error t))) data)]
                                            (info "=======================")
                                            (info object (:sequence-number row) (:partition-key row))
                                            (System/exit 1)
                                          ))))
  )

(defn kinesis-producer
  "Returns a new kinesis producer with the given options"
  [options]
  (map->KinesisProducer options))

(defrecord KinesisConsumer [aws-key aws-secret aws-endpoint aws-kinesis-stream cons-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (warn "Starting KINESIS CONSUMER Component %s" aws-kinesis-stream aws-key aws-secret aws-endpoint)
      ; (System/exit 1)
      ; (future 
      (start-worker aws-key aws-secret aws-endpoint aws-kinesis-stream)  

      (assoc component :channel (chan))
      (catch Throwable t 
        (do
        (warn "[KINESIS-CONSUMER] FAILED")
        (error t)))))

)

(defn kinesis-consumer
  "Returns a new kinesis producer with the given options"
  [options]
  (map->KinesisConsumer options))