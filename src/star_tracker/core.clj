(ns star-tracker.core
  (:require 
     [clojure.tools.cli             :refer [parse-opts]]
     [clojure.java.io               :as io]
     [org.httpkit.server            :refer [run-server]]
     [star-tracker.utils           :refer :all]
     [star-tracker.log             :as log-base]
     [ring.adapter.jetty           :refer [run-jetty]]
     [ring.middleware.params       :refer [wrap-params]]
     [cheshire.core                :refer [generate-string]]
     [compojure.route              :as route :refer [resources]]
     [compojure.core               :refer [defroutes GET POST DELETE ANY HEAD context]]
     [compojure.handler            :refer [site]]
     [clojure.core.async :as async :refer [go >! chan]]
     [com.stuartsierra.component   :as component]
     [metrics.core                           :refer [new-registry]]
     [metrics.meters                         :refer (meter mark! defmeter rates)]
     [metrics.histograms                     :refer [defhistogram update! percentiles mean std-dev number-recorded]]
     [metrics.timers                         :refer [deftimer time!] :as timers]
     [metrics.reporters.console              :as console]
     [metrics.reporters.jmx                  :as jmx]
     [star-tracker.system.kafka    :as sys.kafka]
     [star-tracker.system.kinesis  :as sys.kinesis]
     [taoensso.timbre              :as timbre
           :refer (log trace debug info warn error fatal report sometimes)])
  (:import [org.apache.commons.io FileUtils])
  (:gen-class))

(defmeter  message-ingested )
(defmeter  message-published )
(defmeter  message-publish-error )
(def meters {
  :publish message-published
  :ingest message-ingested
  :publish-error message-publish-error
  })

(def JR (jmx/reporter {}))
(def CR (console/reporter {}))

(def settings (atom {:json true}))

(def default-headers {"Expires" "0"
                      "Pragma" "no-cache"
                      "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"})

(def image-headers (merge {"Content-Type" "image/png"} default-headers))

(def pixel-img (FileUtils/readFileToByteArray (io/file "1x1.png")))

(def utc-formatter (doto
                    (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    (.setTimeZone (java.util.TimeZone/getTimeZone "UTC"))))

(defn build-log-map
  [req]
  (let [ts (java.util.Date.)]
  (try 
      {:params (:params req)
       :ip (remove nil? [(:remote-addr req) (get-in req [:headers "x-real-ip"])])
       :host (get-in req [:headers "host"])
       :ua (get-in req [:headers "user-agent"])
       :refer (get-in req [:headers "referer"])
       :m (:request-method req) ; for debugging purposes
       :serv (:server-name req)
       :uri (:uri req)
       :body (body-as-string req)
       :headers (:headers req) ; in near future remove duplicates
       :time (.format utc-formatter ts)
       :epoch (str (.getTime ts))
     }
    (catch Throwable t (error t)))))

(defn log-request
  [req & [pipe]]
  (let [data (build-log-map req)]
    (sometimes 0.1 data)
    (mark! message-ingested)
    (go (>! pipe ["test" (generate-string data)]))
    ))

(defn redirect-request [req]
  {:status  204
   :headers default-headers})

(defn img-request 
  [req pipe]
  (log-request req pipe)
  {:status  200
    :body (java.io.ByteArrayInputStream. pixel-img)
   :headers image-headers})

(defn base-request [req pipe]
  (log-request req pipe)
  {:status  204
   :headers default-headers})

(defrecord HTTP [port pipe listener conf server]
  component/Lifecycle

  (start [this]
    (info "Starting HTTP Component")
    (let [pipe (:channel pipe)]
      (try 
        (defroutes app-routes
          (resources "/")
          (HEAD "/" [] "")
          (GET "/"  request (base-request request pipe))
          (GET "/report"  request (base-request request pipe))
          (GET "/r" [] redirect-request)
          (GET "/pixel.gif" request (img-request request pipe))
          (GET "/ping" request {:status  200 :body "pong" })
          (route/not-found "<p>Page not found.</p>"))

        (def http-handler
          (site #'app-routes))

        (def app
          (-> http-handler
            (wrap-params)
            ))

        (let [server (run-jetty app {:port port :join? false})]

          (info "Listening events now...")

        (assoc this :server server))
      (catch Throwable t 
        (do 
          (error t))))))

  (stop [this]
    (.stop server)))

(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn app-system 
  [options]
  (let [{:keys [zk port aws-key aws-secret aws-endpoint aws-kinesis-stream pipe]} options
      event-pipe (if (= pipe "kinesis")
                    (sys.kinesis/kinesis-producer (merge {:meters meters }
                      (select-keys options [:aws-key :aws-secret :aws-endpoint :aws-kinesis-stream])))
                    (sys.kafka/kafka-producer zk))]
  (-> (component/system-map 
        :pipe event-pipe
        :app (component/using 
            (http-server port)
            [:pipe]
          )))))

(def log-levels [:trace :debug :info :warn :error :fatal :report])
(def log-level-map (zipmap (range (count log-levels)) log-levels))

(def cli-options 
  [["-p" "--port PORT" "Port number"
    :default 10000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
    ["-m" "--pipe PIPE" :default "kinesis"]
    [nil "--zk ZOOKEEPER" :default "localhost:2181"]
    [nil "--aws-key KEY" "AWS KEY" ]
    [nil "--aws-secret SECRET" "AWS SECRET" ]
    [nil "--aws-endpoint ENDPOINT" "Aws ENDPOINT to use" :defaut "eu-west-1"]
    [nil "--aws-kinesis-stream STREAM" "AWS Kinesis Stream name" ]
    [nil "--stats-interval INTERVAL" "How frequently publish stats" :default 10]
    [nil "--log-level LEVEL" :default 2 ; INFO
            :parse-fn #(Integer/parseInt %)
            :validate [#(<= 0 % 6) (str "Must be a number between 0 and 6. Mapping " log-level-map)]]
    ])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (info "Arranging settings and logging..")
  (reset! timbre/config log-base/log-config)
  
  (info "Starting up engines..")
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)
      log-level (get log-level-map (int (:log-level options)))]

    (info log-level)

    (when (:help options)
      (println summary)
      (System/exit 0))

    (when errors
      (println errors)
      (System/exit 1))

    (timbre/set-level! log-level)
    (info options)

    (jmx/start JR)
    ; report to console in every 100 seconds
    (console/start CR (:stats-interval options))

    (let [sys (component/start (app-system options))]
      (info "System started..")
      )))
