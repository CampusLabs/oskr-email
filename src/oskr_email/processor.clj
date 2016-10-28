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

(ns oskr-email.processor
  (:require [clojure.tools.logging :refer [info debug error warn]]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [net.cgrand.xforms :as x]
            [oskr-email.protocols :as p]
            [postal.smtp :refer [smtp-send]]))

(defn ack-deliveries [consumer message-batch]
  (let [acks (into {} (comp
                        (map meta)
                        (x/by-key :partition (comp
                                               (map :offset)
                                               (x/reduce max 0))))
                   message-batch)]
    (doseq [[partition offset] acks]
      (p/commit! consumer partition offset))))

(defn deliver! [message-batch]
  (info "Starting delivery")

  (let [host (env "SMTP_HOST" "mailcatcher.service.consul")
        port (env "SMTP_PORT" 1025)]
    (apply smtp-send (list* {:host host :port port} message-batch))))

(defn make-delivery-handler [consumer]
  (fn [message-batch]
    (info "handling delivery")
    (d/chain' (deliver! message-batch)
      (fn [_] (ack-deliveries consumer message-batch)))))

(defn process [message-stream consumer]
  (let [delivery-handler (make-delivery-handler consumer)]
    (info "Starting process loop")
    (-> (d/loop []
          (d/let-flow [message-batch (s/take! message-stream)]
            (info "message-batch" message-batch)
            (if message-batch
              (d/chain' (delivery-handler message-batch)
                (fn [_] (d/recur))))))
        (d/catch' #(error %)))))

(defrecord Processor [consumer batched-stream finished?]
  component/Lifecycle
  (start [processor]
    (info "Starting processor")
    (let [message-stream (:message-stream consumer)
          batched-stream (s/batch 1000 200 message-stream)
          finished? (process batched-stream consumer)]
      (assoc processor :message-stream message-stream
                       :batched-stream batched-stream
                       :finished? finished?)))

  (stop [processor]
    (info "Stopping processor")
    (s/close! batched-stream)
    @finished?
    (assoc processor :batched-stream nil
                     :finished? nil)))

(defn new-processor []
  (Processor. nil nil nil))

#_(comment
    (let [offset-atom (atom 0)
          messages (as-> "resources/deliveries.json" $
                         (java.io.File. $)
                         (slurp $)
                         (json/parse-string $ true)
                         (map delivery->message $)
                         (map (fn [message]
                                (with-meta message {:topic     "Deliveries"
                                                    :partition (rand-int 5)
                                                    :offset    (swap! offset-atom inc)}))
                              $))]
      (def msgs messages)

      (do
        (deliver! messages))))
