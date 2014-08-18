(ns stats-viewer.util
  (:import java.util.Date
    java.util.Calendar
    java.text.SimpleDateFormat))

(defn round-ms-down-to-nearest-sec [date]
  (when date
    ( * 1000 (quot (.getTime date) 1000))))

(defn to-date [s]
  (.parse (SimpleDateFormat. "dd MMM yyyy") s))

(defn format-date [date & [fmt]]
  (.format (SimpleDateFormat. (or fmt "yyyy-MM-dd")) date))
