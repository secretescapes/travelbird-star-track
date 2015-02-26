(ns star-tracker.log
  (:require 
    [clojure.string :as str]
    [taoensso.timbre :refer :all]
    ))

(def ip-address  (.getHostAddress (java.net.InetAddress/getLocalHost)))

(defn fmt-output-fn
  [{:keys [level throwable message timestamp hostname ns]}
   ;; Any extra appender-specific opts:
   & [{:keys [nofonts?] :as appender-fmt-output-opts}]]
  ;; <timestamp> <hostname> <LEVEL> [<ns>] - <message> <throwable>
  (format "[STARTRACK]%s %s %s [%s] - %s%s"
    timestamp ip-address (-> level name str/upper-case) ns (or message "")
    (or (stacktrace throwable "\n" (when nofonts? {})) "")))

(def log-config
  "APPENDERS
     An appender is a map with keys:
      :doc             ; (Optional) string.
      :min-level       ; (Optional) keyword, or nil (no minimum level).
      :enabled?        ; (Optional).
      :async?          ; (Optional) dispatch using agent (good for slow appenders).
      :rate-limit      ; (Optional) [ncalls-limit window-ms].
      :fmt-output-opts ; (Optional) extra opts passed to `fmt-output-fn`.
      :fn              ; (fn [appender-args-map]), with keys described below.
      :args-hash-fn    ; Experimental. Used by rate-limiter, etc.
     An appender's fn takes a single map with keys:
      :level         ; Keyword.
      :error?        ; Is level an 'error' level?
      :throwable     ; java.lang.Throwable.
      :args          ; Raw logging macro args (as given to `info`, etc.).
      :message       ; Stringified logging macro args, or nil.
      :output        ; Output of `fmt-output-fn`, used by built-in appenders
                     ; as final, formatted appender output. Appenders may (but
                     ; are not obligated to) use this as their output.
      :ap-config     ; Content of config's :shared-appender-config key.
      :profile-stats ; From `profile` macro.
      :instant       ; java.util.Date.
      :timestamp     ; String generated from :timestamp-pattern, :timestamp-locale.
      :hostname      ; String.
      :ns            ; String.
      ;; Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
      :file          ; String.
      :line          ; Integer.
   MIDDLEWARE
     Middleware are fns (applied right-to-left) that transform the map
     dispatched to appender fns. If any middleware returns nil, no dispatching
     will occur (i.e. the event will be filtered).
  The `example-config` code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {;; Prefer `level-atom` to in-config level when possible:
   ;; :current-logging-level :debug

   ;;; Control log filtering by namespace patterns (e.g. ["my-app.*"]).
   ;;; Useful for turning off logging in noisy libraries, etc.
   :ns-whitelist []
   :ns-blacklist []

   ;; Fns (applied right-to-left) to transform/filter appender fn args.
   ;; Useful for obfuscating credentials, pattern filtering, etc.
   :middleware []

   ;;; Control :timestamp format
   :timestamp-pattern "yyyy-MM-dd'T'HH:mm:ss.SSSZ" ; SimpleDateFormat pattern
   :timestamp-locale  nil ; A Locale object, or nil

   :prefix-fn ; DEPRECATED, here for backwards comp
   (fn [{:keys [level timestamp hostname ns]}]
     (str timestamp " " hostname " " (-> level name str/upper-case)
          " [" ns "]"))

   ;; Output formatter used by built-in appenders. Custom appenders may (but are
   ;; not required to use) its output (:output). Extra per-appender opts can be
   ;; supplied as an optional second (map) arg.
   :fmt-output-fn fmt-output-fn

   :shared-appender-config {} ; Provided to all appenders via :ap-config key
   :appenders
   {:standard-out
    {:doc "Prints to *out*/*err*. Enabled by default."
     :min-level :info :enabled? true :async? false :rate-limit nil
     :fn (fn [{:keys [error? output]}] ; Can use any appender args
           (binding [*out* (if error? *err* *out*)]
             (str-println output)))}

    :spit
    {:doc "Spits to `(:spit-filename :shared-appender-config)` file."
     :min-level nil :enabled? false :async? false :rate-limit nil
     :fn (fn [{:keys [ap-config output]}] ; Can use any appender args
           (when-let [filename (:spit-filename ap-config)]
             (try (spit filename (str output "\n") :append true)
                  (catch java.io.IOException _))))}}})