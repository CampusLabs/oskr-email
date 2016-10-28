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

(defproject oskr-email "0.1.0"
  :description "A companion to oskr-events that delivers messages via email"
  :url "http://github.com/orgsync/oskr-email"
  :license {:name "Apache 2.0"
            :url  "http://www.apache.org/licenses/LICENSE.md-2.0"}
  :dependencies [[byte-streams "0.2.2"]
                 [cheshire "5.6.3"]
                 [com.draines/postal "2.0.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [environ "1.1.0"]
                 [log4j/log4j "1.2.17"]
                 [manifold "0.1.6-alpha1"]
                 [net.cgrand/xforms "0.4.0"]
                 [org.apache.kafka/kafka-clients "0.9.0.1"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [org.slf4j/slf4j-api "1.7.21"]]
  :profiles {:repl    {:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"]}
             :uberjar {:jvm-opts     ["-Dclojure.compiler.direct-linking=true"]
                       :aot          :all
                       :uberjar-name "oskr-email.jar"}}
  :main oskr-email.core)
