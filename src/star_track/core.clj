(ns star-track.core
  (:require 
    [org.httpkit.server :refer [run-server]]
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
     [star-track.utils :refer :all])
  (:gen-class))

(defn app [req]
  ; :remote-addr :headers :async-channel :server-port :content-length 
  ; :websocket? :content-type :character-encoding :uri :server-name :query-string :body :scheme :request-method
  (debug (select-keys req [:headers]))

  (let [data (-> (select-keys req [:remote-add :uri :server-name :query-string :request-method :headers])
                  (assoc :host (get-in req [:headers "host"]))
                  (assoc :ua (get-in req [:headers "user-agent"]))
                  (assoc :refer (get-in req [:headers "referer"])))]

  (info data)
  {:status  204
   :headers {"Content-Type" "text/html"}}))

(defonce server (atom nil))

(defn start-up
  [{:keys [port] :or {port 8080}}]
  (reset! server (run-server app {:port port}))
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
