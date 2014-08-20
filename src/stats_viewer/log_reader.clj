(ns stats-viewer.log-reader
  (:require [clojure.java.io :refer [reader file]]
            [clojure.set :refer [rename-keys]]
            [stats-viewer.util :refer :all]
            [edn-config.core :refer [env]])
  (:import
    [java.nio.file FileSystems Path Paths StandardWatchEventKinds]
    java.util.Date
    java.text.SimpleDateFormat
    java.io.RandomAccessFile))

;(def log-path (atom "/var/log/glassfish-access-logs/"))
;(defonce log-path (atom "logs/"))
(defonce logs (atom nil))

(defn rounded-access-time [line]
  (-> (SimpleDateFormat. "dd/MMM/yyyy:HH:mm:ss zzzzz")
      (.parse (second (re-find #"\[(.*?)\]" line)))
      (round-ms-down-to-nearest-sec)))

(defn parse-log [line]
  (merge
    {:ip (re-find #"\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b" line)
     :access-time (try
                    (.parse (SimpleDateFormat. "dd/MMM/yyyy:HH:mm:ss zzzzz")
                            (second (re-find #"\[(.*?)\]" line)))
                    (catch Exception _))}
    (into {} (map vector [:route :path :browser] (re-seq #"\".*?\"" line)))))

(defn sorted-logs [path limit]
  (println "loading logs from" path)
  (when-let [logs (not-empty (->> path file file-seq rest))]
    (take-last limit (sort-by (memfn lastModified) logs))))

(defn parse-files [files]
  (mapcat
    #(with-open [rdr (reader %)]
       (doall (->> rdr line-seq (map parse-log) (filter :access-time))))
    files))

(defn get-logs [path n]
  (parse-files (sorted-logs path n)))

(defn get-logs-after [access-time]
  (when access-time
    (take-while #(.after (:access-time %) access-time) @logs)))

(defn register-events! [dir watch-service opts]
  (.register dir watch-service
    (-> opts
       (select-keys [StandardWatchEventKinds/ENTRY_CREATE
                     StandardWatchEventKinds/ENTRY_MODIFY
                     StandardWatchEventKinds/ENTRY_DELETE
                     StandardWatchEventKinds/OVERFLOW])
       (keys)
       (into-array))))

(defn rename-event-keys [opts]
  (rename-keys opts
    {:create StandardWatchEventKinds/ENTRY_CREATE
     :modify StandardWatchEventKinds/ENTRY_MODIFY
     :delete StandardWatchEventKinds/ENTRY_DELETE
     :overflow StandardWatchEventKinds/OVERFLOW}))

(defn watch-loop [watch-service opts stop-event]
  (loop []
    (let [k (.take watch-service)]
      (doseq [event (.pollEvents k)]
        (if-let [handler (get opts (.kind event))]
          (handler event)))
      (when (and (not @stop-event) (.reset k)) (recur)))))

(defn watch [path opts stop-event]
  (let [dir  (-> path (file) (.toURI) (Paths/get))
        opts (rename-event-keys opts)]
    (with-open [watch-service (.newWatchService (FileSystems/getDefault))]
      (register-events! dir watch-service opts)
      (try
        (watch-loop watch-service opts stop-event)
        (catch IllegalMonitorStateException _)))))

(defn start-watcher! [path opts]
  (let [stop-event (atom false)]
    (doto (Thread. #(watch path opts stop-event))
           (.setDaemon true)
           (.start))
    stop-event))

(defn set-reader! [state input]
  (if-let [r (:reader @state)]
    (.close r))
  (swap! state assoc :reader (when input (RandomAccessFile. (str (env :log-path) input) "r"))))

(defn stop! [state]
  (when-let [stop-watcher (:stop-watcher @state)]
    (reset! stop-watcher true))
  (set-reader! state nil))

(defn line-handler [state]
  (when-let [log (try (-> (:reader @state) (.readLine) (parse-log))
                  (catch Exception _))]
    (when (and (:access-time log) (not= "/stats-viewer/logs-after" (:route log)))
      (swap! logs #(cons log (rest %))))))

(defn start! []
  (reset! logs (get-logs (env :log-path) 2))
  (let [state (atom {})]
    (set-reader! state (.getName (first (sorted-logs (env :log-path) 1))))
    (.seek (:reader @state) (.length (:reader @state)))
    (swap! state assoc :stop-watcher
           (start-watcher! (env :log-path)
             {:create (fn [event]
                        (set-reader! (-> event (.context) (.toString)))
                        (line-handler state))
              :modify (fn [event] (line-handler state))}))

    state))

(def watcher (atom nil))

(reset! logs (get-logs (env :log-path) 3))

;(reset! watcher (start!))
;; todo try channels
;put the loop in the channel instead of using a thread
;when done close the channel
