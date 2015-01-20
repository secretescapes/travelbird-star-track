(ns star-tracker.core
  (:require 
    [clojure.java.io :as io]
    [org.httpkit.server :refer [run-server]]
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
     [star-tracker.utils :refer :all]
     [star-tracker.log :as log-base]
     [compojure.route :as route]
     [compojure.core :refer [defroutes GET POST DELETE ANY context]]
     [compojure.handler :refer [site]])
  (:import [org.apache.commons.io FileUtils])
  (:gen-class))

; :remote-addr :headers :async-channel :server-port :content-length 
; :websocket? :content-type :character-encoding :uri :server-name :query-string :body :scheme :request-method
; (debug (select-keys req [:headers]))

 (def pixel-img (FileUtils/readFileToByteArray (io/file "1x1.png")))

(defn build-log-map
  [req]
  (try 
    (-> 
      (select-keys req [:uri :server-name :query-string :request-method :headers])
          (assoc :qs (:query-string req))
          (assoc :ip [(:remote-addr req) (get-in req [:headers "x-real-ip"])])
          (assoc :host (get-in req [:headers "host"]))
          (assoc :ua (get-in req [:headers "user-agent"]))
          (assoc :refer (get-in req [:headers "referer"])))
    (catch Throwable t (error t))))

(defn redirect-request [req]
  {:status  204
   :headers {"Content-Type" "text/html"
             "Pragma" "no-cache"
             "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
             "Expires" "0"
             }})

(defn img-request [req]
  ;; lets be dump right now.
  (info (build-log-map req))
  {:status  200
    :body (java.io.ByteArrayInputStream. pixel-img)
   :headers {"Content-Type" "text/html"
             "Pragma" "no-cache"
             "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
             "Expires" "0"
             }})


(defn log-request [req]
  
  
    (info (build-log-map req))
    {:status  204
     :headers {"Content-Type" "text/html"
               "Pragma" "no-cache"
               "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
               }})

(defonce server (atom nil))

(defroutes app-routes
  (GET "/" [] log-request)
  (GET "/r" [] redirect-request)
  (GET "/pixel.gif" [] img-request)
  (route/not-found "<p>Page not found.</p>"))

(defn start-up
  [{:keys [port] :or {port 8080}}]
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
