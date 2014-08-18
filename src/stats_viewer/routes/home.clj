(ns stats-viewer.routes.home
  (:require [compojure.core :refer :all]
            [cheshire.core :refer [generate-string]]
            [noir.response :refer [edn content-type]]
            [stats-viewer.layout :as layout]
            [stats-viewer.log-reader :refer [logs get-logs-after]]
            [stats-viewer.util :as util]))

(defn home-page []
  (layout/render "home.html"))

(defn json [content]
  (content-type "application/json; charset=utf-8"
                (generate-string content {:date-format "yyyy-MM-dd HH:mm:ss"})))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/logs" [] (edn @logs))
  (POST "/logs-after" [inst] (edn (get-logs-after inst))))
