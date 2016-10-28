;
; Copyright 2016 OrgSync.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;   http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;

(ns oskr-email.core
  (:require [com.stuartsierra.component :as component]
            [oskr-email.processor :as processor]
            [oskr-email.consumer :as consumer]
            [manifold.deferred :as d]
            [environ.core :refer [env]]
            [clojure.tools.logging :refer [error]])
  (:gen-class))

(defn create-system []
  (let [kafka-bootstrap (env "KAFKA_BOOTSTRAP" "kafka.service.consul:9092")]
    (-> (component/system-map
          :processor (processor/new-processor)
          :consumer (consumer/new-consumer
                      kafka-bootstrap
                      (env "KAFKA_GROUP_ID" "oskr-email")
                      (env "KAFKA_SPEC_TOPIC" "Communications.Deliveries.Email")))
        (component/system-using
          {:processor [:consumer]}))))

(def default-system
  (create-system))

(defn -main [& _]
  (let [system (component/start default-system)]
    (Thread/setDefaultUncaughtExceptionHandler
      (reify Thread$UncaughtExceptionHandler
        (uncaughtException [_ thread ex]
          (error "Uncaught exception on" (.getName thread) "-"
                 (.getMessage ex)))))

    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(component/stop system)))

    @(d/deferred)))

(comment
  (do
    (def s (create-system))
    (alter-var-root #'s component/start)))

(comment
  (alter-var-root #'s component/stop))
