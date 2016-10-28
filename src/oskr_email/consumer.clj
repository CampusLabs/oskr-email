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

(ns oskr-email.consumer
  (:require [byte-streams :as byte]
            [clojure.tools.logging :refer [info debug error warn]]
            [clojure.walk :refer [stringify-keys]]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [manifold.stream :as s]
            [oskr-email.protocols :as p]
            [cheshire.core :as json])
  (:import [org.apache.kafka.common.serialization ByteArrayDeserializer StringDeserializer]
           [org.apache.kafka.clients.consumer KafkaConsumer ConsumerRecord OffsetAndMetadata OffsetCommitCallback]
           [java.util Map]
           [java.io Reader]
           [org.apache.kafka.common TopicPartition]))

(defn record->message [^ConsumerRecord record]
  (let [content (-> (.value record)
                    (byte/convert Reader)
                    (json/parse-stream true)
                    :content)
        message {:to         (:to content)
                 :from       (:from content)
                 :subject    (:subject content)
                 :body       (:content content)
                 :message-id (constantly (:deliverableId content))
                 :user-agent "Oskr Email"}]
    (with-meta message {:topic     (.topic record)
                        :partition (.partition record)
                        :offset    (.offset record)})))

(defn poll-fn [kafka-consumer message-stream]
  (info "defining poller")
  (fn []
    (when-not (s/closed? message-stream)
      (warn "polling")
      (->> (try (locking kafka-consumer
                  (.poll kafka-consumer 1000))
                (catch Exception e
                  (warn "polling failed" (.getMessage e))))
           (.iterator)
           (iterator-seq)
           (s/put-all! message-stream)
           deref)
      (recur))))

(defn spawn-poller [kafka-consumer message-stream]
  (info "spawning poller")
  (-> (Thread. ^Runnable (poll-fn kafka-consumer message-stream)
               "kafka-consumer")
      (.start)))

(defrecord Consumer [config topic message-stream kafka-consumer]
  component/Lifecycle
  (start [consumer]
    (info "Starting Consumer")
    (let [message-stream (s/stream* {:xform (map record->message)})
          kafka-consumer (try (KafkaConsumer. ^Map config)
                              (catch Exception e (warn e)))]
      (spawn-poller kafka-consumer message-stream)
      (locking kafka-consumer
        (.assignment kafka-consumer)
        (.subscribe kafka-consumer [topic]))
      (assoc consumer :kafka-consumer kafka-consumer
                      :message-stream message-stream)))

  (stop [consumer]
    (try
      (s/close! message-stream)
      (locking kafka-consumer
        (.close kafka-consumer))
      (assoc consumer :kafka-consumer nil
                      :message-stream nil)
      (catch Exception e (error e)
                         (throw e))))

  p/Commitable
  (commit! [_ partition offset]
    (info "acking to consumer")
    (let [topic (env "KAFKA_TOPIC" "Communications.Deliveries.Email")
          topic-partition (TopicPartition. topic partition)
          offset-and-metadata (OffsetAndMetadata. (inc offset))
          offsets {topic-partition offset-and-metadata}
          callback (reify OffsetCommitCallback
                     (onComplete [_ _ e]
                       (when e
                         (warn "Unable to commit"
                               [topic partition offset]
                               (.getMessage e)))))]

      (locking kafka-consumer
        (.commitAsync kafka-consumer offsets callback)))))

(defn new-consumer [kafka-bootstrap group-id topic]
  (let [config (-> {:bootstrap.servers  kafka-bootstrap
                    :key.deserializer   StringDeserializer
                    :value.deserializer ByteArrayDeserializer
                    :group.id           group-id
                    :enable.auto.commit false
                    :auto.offset.reset  "earliest"}
                   stringify-keys)]
    (Consumer. config topic nil nil)))

(comment
  (def consumer (new-consumer "kafka.service.consul:9092"
                              "oskr-email"
                              "Communications.Deliveries.Email")))

(comment
  (alter-var-root #'consumer component/start))

(comment
  (alter-var-root #'consumer component/stop))

#_(comment
    (let [consumer (new-consumer "kafka.service.consul:9092"
                                 "oskr-email"
                                 "Communications.Deliveries.Email")
          offset-atom (atom 0)
          messages (as-> "resources/deliveries.json" $
                         (java.io.File. $)
                         (slurp $)
                         (json/parse-string $ true)
                         (map #(ConsumerRecord.
                                "Communications.Deliveries.Email"
                                (rand-int 5)
                                (swap! offset-atom inc)
                                "iosdyfu938" %) $)
                         (map #(record->message %) $))]
      (clojure.pprint/pprint messages)))
