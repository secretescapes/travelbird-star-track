(defproject star-track "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [com.stuartsierra/component "0.2.2"]
    [http-kit "2.1.16"]
    [com.taoensso/timbre "3.3.1-1cd4b70"]
    ]
  :main ^:skip-aot star-track.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
