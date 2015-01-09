(ns star-track.core
  (:require 
    [org.httpkit.server :refer [run-server]]
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
     [star-track.utils :refer :all]
     [compojure.route :as route]
     [compojure.core :refer [defroutes GET POST DELETE ANY context]]
     [compojure.handler :refer [site]])
  
  (:gen-class))

(defn redirect-request [req]
  {:status  204
   :headers {"Content-Type" "text/html"
             "Pragma" "no-cache"
             "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
             "Expires" "0"
             }})

(defn img-request [req]
  ;; lets be dump right now.
  {:status  204
   :headers {"Content-Type" "text/html"
             "Pragma" "no-cache"
             "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
             "Expires" "0"
             }}
  )

(defn log-request [req]
  ; :remote-addr :headers :async-channel :server-port :content-length 
  ; :websocket? :content-type :character-encoding :uri :server-name :query-string :body :scheme :request-method
  (debug (select-keys req [:headers]))

  (let [data (-> (select-keys req [:uri :server-name :query-string :request-method :headers])
                  (assoc :qs (:query-string req))
                  (assoc :ip (:remote-addr req))
                  (assoc :host (get-in req [:headers "host"]))
                  (assoc :ua (get-in req [:headers "user-agent"]))
                  (assoc :refer (get-in req [:headers "referer"])))]

  (info data)
  {:status  204
   :headers {"Content-Type" "text/html"
             "Pragma" "no-cache"
             "Cache-Control" "private, no-cache, no-cache=Set-Cookie, proxy-revalidate"
             }}))

(defonce server (atom nil))

(defroutes app-routes
  (GET "/" [] log-request)
  (GET "/r" [] redirect-request)
  (GET "/pixel.gif" [] log-request)
  (route/not-found "<p>Page not found.</p>"))

(defn start-up
  [{:keys [port] :or {port 8080}}]
  (reset! server (run-server (site #'app-routes) {:port port}))
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
  (let [settings {}]
  (start-up settings)))
