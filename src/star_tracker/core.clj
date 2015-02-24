(ns star-tracker.core
  (:require 
     [clojure.java.io :as io]
     [org.httpkit.server          :refer [run-server]]
     [star-tracker.utils           :refer :all]
     [star-tracker.log             :as log-base]
     [ring.adapter.jetty           :refer [run-jetty]]
     [cheshire.core                :refer :all]
     [compojure.route              :as route]
     [compojure.core               :refer [defroutes GET POST DELETE ANY context]]
     [compojure.handler            :refer [site]]
     [clojure.core.async :as async :refer [go >! chan]]
     [com.stuartsierra.component   :as component]
     [star-tracker.system.kafka    :as sys.kafka]
     [taoensso.timbre              :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)])
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

; (defonce server (atom nil))

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
       :headers (:headers req) ; in near future remove duplicates
     }
    (catch Throwable t (error t))))

(defn log-request
  [req & [pipe]]
  (let [data (build-log-map req)]
    (info "JSON" data)
     
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

(defn base-request [req]
  (info (build-log-map req))
  {:status  204
   :headers default-headers})

(defrecord HTTP [port kafka conf server]
  component/Lifecycle

  (start [this]
    (info "Starting HTTP Component")
    (let [pipe (:channel kafka)]
      (try 
        (defroutes app-routes
          (GET "/"  base-request)
          (GET "/r" [] redirect-request)
          (GET "/pixel.gif" request (img-request request pipe))
          (GET "/ping" {:status  200 :body "pong" })
          (route/not-found "<p>Page not found.</p>"))

        (let [
          ; server (run-server (site #'app-routes) {:port port})
          server (run-jetty (site #'app-routes) {:port port :join? true})
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
  (let [{:keys [zookeeper port]} options]
  (-> (component/system-map 
        :kafka (sys.kafka/kafka-producer zookeeper)
        :app (component/using 
            (http-server port)
            [:kafka]
          )))))

(defn -main
  "I don't do a whole lot ... yet."
  [port zk & args]
  (info "Arranging settings and logging..")
  (reset! timbre/config log-base/log-config )
  (info "Starting up engines..")
  (let [settings {:port (Integer/parseInt port) :zookeeper zk}
        sys (component/start (app-system settings))]
    
  ; (start-up settings)
  ))
