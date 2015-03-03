(ns star-tracker.core
  (:require 
     [clojure.tools.cli             :refer [parse-opts]]
     [clojure.java.io               :as io]
     [org.httpkit.server            :refer [run-server]]
     [star-tracker.utils           :refer :all]
     [star-tracker.log             :as log-base]
     [ring.adapter.jetty           :refer [run-jetty]]
     [cheshire.core                :refer :all]
     [compojure.route              :as route :refer [resources]]
     [compojure.core               :refer [defroutes GET POST DELETE ANY HEAD context]]
     [compojure.handler            :refer [site]]
     [clojure.core.async :as async :refer [go >! chan]]
     [com.stuartsierra.component   :as component]
     [star-tracker.system.kafka    :as sys.kafka]
     [star-tracker.system.kinesis  :as sys.kinesis]
     [taoensso.timbre              :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report sometimes)])
  (:import [org.apache.commons.io FileUtils])
  (:gen-class))

; http-kit keys
; ==========
; :remote-addr :headers :async-channel :server-port :content-length 
; :websocket? :content-type :character-encoding :uri :server-name :query-string :body :scheme :request-method
; (debug (select-keys req [:headers]))
; [org.httpkit.server :refer [run-server]]
; (reset! server (run-server (site #'app-routes) {:port port}))

(def settings (atom {:json true}))

(def default-headers {"Expires" "0"
                      "Pragma" "no-cache"
                      "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"})

(def image-headers (merge {"Content-Type" "image/png"} default-headers))

(def pixel-img (FileUtils/readFileToByteArray (io/file "1x1.png")))

(defn build-log-map
  [req]
  (try 
      {:qs (:query-string req)
       :ip (remove nil? [(:remote-addr req) (get-in req [:headers "x-real-ip"])])
       :host (get-in req [:headers "host"])
       :ua (get-in req [:headers "user-agent"])
       :refer (get-in req [:headers "referer"])
       :m (:request-method req) ; for debugging purposes
       :serv (:server-name req)
       :uri (:uri req)
       :body (body-as-string req)
       :headers (:headers req) ; in near future remove duplicates
     }
    (catch Throwable t (error t))))

(defn log-request
  [req & [pipe]]
  (let [data (build-log-map req)]
    (sometimes 0.1 data)
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

        (let [
          ; server (run-server (site #'app-routes) {:port port})
          server (run-jetty (site #'app-routes) {:port port :join? false})
          ]
          (info "Listening events now...")

        (assoc this :server server))
      (catch Throwable t 
        (do 
          (error t))))))

  (stop [this]
    (.stop server)
    ; (server :timeout 100)
    ))


(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn app-system 
  [options]
  (let [{:keys [zookeeper port aws-key aws-secret aws-endpoint aws-kinesis-stream pipe]} options
      event-pipe (if (= pipe "kinesis")
                    (sys.kinesis/kinesis-producer (select-keys options [:aws-key :aws-secret :aws-endpoint :aws-kinesis-stream]))
                    (sys.kafka/kafka-producer zookeeper))]
  (-> (component/system-map 
        :pipe event-pipe
        :app (component/using 
            (http-server port)
            [:pipe]
          )))))

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
    ])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (info "Arranging settings and logging..")
  ; (reset! timbre/config log-base/log-config )
  (timbre/set-level! :info)
  (info "Starting up engines..")
  (let [parsed-options (parse-opts args cli-options)
        options (:options parsed-options)]
    (info options)
    
  ; (start-up settings)
  (let [sys (component/start (app-system options))]
    (info "System started..")
    )))
