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

(defonce server (atom nil))

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
  [req]
  (let [data (build-log-map req)]
    (info "JSON" 
      data
      ; (generate-string data)
      )))

(defn redirect-request [req]
  {:status  204
   :headers default-headers})

(defn img-request [req]
  (log-request req)
  {:status  200
    :body (java.io.ByteArrayInputStream. pixel-img)
   :headers image-headers})

(defn base-request [req]
  (info (build-log-map req))
  {:status  204
   :headers default-headers})

(defroutes app-routes
  (GET "/" [] base-request)
  (GET "/r" [] redirect-request)
  (GET "/pixel.gif" [] img-request)
  (route/not-found "<p>Page not found.</p>"))

(defn start-up
  [{:keys [port] :or {port 8080}}]
  ; (reset! server (run-jetty (site #'app-routes) {:port port :join? true}))
  (reset! server (run-server (site #'app-routes) {:port port}))
    (info "Listening events now!...")
  )

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (info "Arranging settings and logging..")
  (let [settings {:port (Integer/parseInt (first args))}]
    (reset! timbre/config log-base/log-config )
  (info "Starting up engines..")
  (start-up settings)))
