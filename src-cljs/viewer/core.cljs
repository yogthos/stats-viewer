(ns viewer.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :as ajax]))

(enable-console-print!)

(def logs (atom {}))
(def dynamic-logs (clojure.core/atom []))
(def dynamic-plots (clojure.core/atom []))

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
  (->> logs
      (reduce #(update-in %1 [(:access-time %2)] (fnil inc 0)) {})
      (sort-by #(first %))))

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

(defn millis [d] (.getTime d))

(defn piechart [logs target]
  (.plot js/$ target
         (clj->js logs)
         (clj->js
          {:series
           {:pie
            {:innerRadius 0.4
             :show true
             :combine {:color "#999"
                       :threshold 0.03}
             :label
             {:show true}}}})))

(defn timeseries [logs target]
  (.plot js/$ target
         (clj->js [(map #(update-in % [0] millis) logs)])
         (clj->js
           {:colors ["#0000ff"]
            :series {:shadowSize 0}
            :xaxis {:mode "time" :minTickSize [1 "minute"]}
            :xaxes [{:position "bottom" :axisLabel "Hits"}]
            :yaxes [{:position "left" :axisLabel "Time"}]})))

(defn dynamic-timeseries [logs target]
  (swap! dynamic-plots conj (timeseries logs target)))

(defn rotate-logs [new-logs]
  (when (not-empty new-logs)
    (println "new logs:" (count new-logs)))
  (when (not-empty new-logs)
    (let [grouped-logs (group-by-time new-logs)]
      (swap! dynamic-logs #(concat (drop (count grouped-logs) %) grouped-logs))
      (doseq [plot @dynamic-plots]
      (.setData plot [@dynamic-logs])))))

(declare fetch-logs)
(defn fetch-logs []
  (when (not-empty @dynamic-logs)
    (POST "/logs-after" {:params {:access-time (first (last @dynamic-logs))}
                         :handler rotate-logs}))
  (js/setTimeout #(fetch-logs) 1000))

(defn chart [div handler]
     (with-meta
       (fn [] div)
       {:component-did-mount
        (fn [this]
          (let [node (reagent.core/dom-node this)]
            (handler (js/$ node))))}))

(defn charts []
  (if (not-empty @logs)
    (let [unique-logs (->> @logs (group-by :ip) (map (fn [log] (first (second log)))))
          logs-by-time (group-by-time @logs)]
      (reset! dynamic-logs logs-by-time)
      (println "first:" (first @dynamic-logs))
      (println "last:" (last @dynamic-logs))
      (fetch-logs)
      [:div
       [:h2 "Total Hits: " (count @logs)]
       [(chart [:div.timeseries] #(dynamic-timeseries logs-by-time %))]
       [:h2 "Unique Hits: " (count unique-logs)]
       [(chart [:div.timeseries] #(dynamic-timeseries (group-by-time unique-logs) %))]
       [:table
        [:tr [:td [:h2 "Hits by Browser"]] [:td [:h2 "Hits by OS"]]]
        [:tr [:td [(chart [:div.piechart] #(piechart (group-by-browser unique-logs) %))]]
             [:td [(chart [:div.piechart] #(piechart (group-by-os unique-logs) %))]]]
        [:tr [:td {:col-span 2} [:h2 "Hits by Route"]]]
        [:tr [:td {:col-span 2} [(chart [:div.piechart.os] #(piechart (group-by-route unique-logs) %))]]]]])
    [:div.spinner
     [:div.bounce1]
     [:div.bounce2]
     [:div.bounce3]]))

(defn init! []
  (GET "/logs" {:handler #(reset! logs %)})
  (reagent/render-component
    [charts]
    (.getElementById js/document "app")))

(init!)

