(ns viewer.core
  (:require [reagent.core :refer [atom]]
            [ajax.core :as ajax]))

(enable-console-print!)

(def logs (atom {}))

(defn GET [url params]
  (ajax/GET (str js/context url) params))

(defn POST [url params]
  (ajax/POST (str js/context url) params))

(defn group-count [[k v]]
  {:label k :data (count v)})

(defn browser-id [{:keys [browser]}]
  (cond
    (nil? browser) "other"
    (re-find #"Firefox" browser) "Firefox"
    (re-find #"WebKit" browser) "WebKit"
    (re-find #"MSIE" browser) "IE"
    :else "other"))

(defn os [{:keys [browser]}]
  (cond
    (nil? browser) "other"
    (re-find #"iPhone" browser) "iPhone"
    (re-find #"iPad" browser) "iPad"
    (re-find #"Android" browser) "Android"
    (re-find #"OS X" browser) "OS X"
    (re-find #"Linux" browser) "Linux"
    (re-find #"Windows" browser) "Windows"
    :else "other"))

(defn group-by-time [logs]
  (reduce #(update-in %1 [(:access-time %2)] (fnil inc 0)) (sorted-map) logs))

(defn group-by-browser [logs]
  (->> logs (group-by browser-id) (map group-count)))

(defn group-by-os [logs]
  (->> logs (group-by os) (map group-count)))

(defn route [{:keys [route]}]
  (let [route (second (.split route " "))]
    (if (= "/" route)
      route
      (or (re-find #"/blog/\d+" route)
          (re-find #"/blog/#/\d+" route)
          "other"))))

(defn group-by-route [logs]
  (map group-count (dissoc (group-by route logs) "other")))

(defn group-logs! [logs]
  (let [unique-logs (->> logs (group-by :ip) (map #(first (second %))))]
    (swap! logs assoc
           :alltime (group-by-time logs)
           :time    (group-by-time unique-logs)
           :os      (group-by-os logs)
           :browser (group-by-browser logs)
           :route   (group-by-route logs))))

(defn chart-by-time [logs target label]
  (.generate js/c3
    (clj->js {:bindto target
              :data
              {:x "x"
               :x_format "%Y-%m-%d"
               ;:x_format "%Y-%m-%d %H:%M:%S"
               :axis {:x {:type "timeseries"
                          ;:localtime false
                          :tick {:format "%Y-%m-%d"}
                          }}
               :columns
               [(cons "x" (map #(.toString %) (keys logs)))
                (cons label (vals logs))]}})))

(defn init! []
  #_(.get js/$
        "/logs"
        #(do
           (reset! logs (js->clj % :keywordize-keys true))
           (let [unique-logs (->> @logs (group-by :ip) (map (fn [log] (first (second log)))))
                 hits-by-time (group-by-time @logs)
                 unique-hits-by-time (group-by-time unique-logs)]
              (chart-by-time hits-by-time "#total-hits" "total hits")
              (chart-by-time unique-hits-by-time "#unique-hits" "unique hits"))))
  (GET "/logs" {:handler
                #(do
                   (reset! logs %)
                   (println (take 3 @logs))
                   (let [unique-logs (->> @logs (group-by :ip) (map (fn [log] (first (second log)))))
                         hits-by-time (group-by-time @logs)
                         unique-hits-by-time (group-by-time unique-logs)]
                    (chart-by-time hits-by-time "#total-hits" "total hits")
                    (chart-by-time unique-hits-by-time "#unique-hits" "unique hits")))}))

(init!)
