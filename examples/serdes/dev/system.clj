(ns system
  "Functions to start and stop the system, used for interactive
  development.

  The `system/start` and `system/stop` functions are required by the
  `user` namespace and should not be called directly."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.java.shell :refer [sh]]
            [jackdaw.admin :as ja]
            [serdes]))

(def system nil)

(defn kafka-admin-client-config
  []
  {"bootstrap.servers" "localhost:9092"})

(defn create-topics
  "Takes a list of topics and creates these using the names given."
  [topic-config-list]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (ja/create-topics! client topic-config-list)))

(defn re-delete-topics
  "Takes an instance of java.util.regex.Pattern and deletes any Kafka
  topics that match."
  [re]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (let [topics-to-delete (->> (ja/list-topics client)
                                (filter #(re-find re (:topic-name %))))]
      (ja/delete-topics! client topics-to-delete))))

(defn application-id
  "Takes an application config and returns an `application.id`."
  [app-config]
  (get app-config "application.id"))

(defn destroy-state-stores
  "Takes an application config and deletes local files associated with
  internal state."
  [app-config]
  (sh "rm" "-rf" (str "/tmp/kafka-streams/" (application-id app-config)))
  (log/info "internal state is deleted"))

(defn stop
  "Stops the app, and deletes topics and internal state.
  This functions is required by the `user` namespace and should not
  be called directly."
  []
  (when system
    (serdes/stop-app (:app system)))
  (re-delete-topics (re-pattern (str "("
                                     (str/join "|" (keys (serdes/topic-metadata)))
                                     "|"
                                     (application-id (serdes/app-config))
                                     ".*)")))
  (destroy-state-stores (serdes/app-config)))

(defn start
  "Creates topics, and starts the app.
  This functions is required by the `user` namespace and should not
  be called directly."
  []
  (with-out-str (stop))
  (create-topics (vals (serdes/topic-metadata)))
  {:app (serdes/start-app (serdes/app-config))})
