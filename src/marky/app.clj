(ns marky.app
  (:import [javax.xml.transform.stream StreamSource]
           [org.jsoup Jsoup])
  (:require [overtone.at-at :as at-]
            [clojure.string :as st]
            [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [cheshire.core :as json]
            [cbdrawer.client :as cb]
            [cbdrawer.view :as cbv]
            [cbdrawer.transcoders :as xcoders]
            [clj-http.client :as http]
            [twitter.oauth :as tw-oauth]
            [twitter.api.restful :as tw-rest]
            [marky.generate :as gen]))

; Collected item
; {:body "body text" :item-id "item id" :source-id "sourceid"}

(def default-configuration
  {:bucket "default"
   :pass ""
   :cburl "http://localhost:8091/"
   :jobs
   [{:type :twitter :user "damienkatz" :period 3600}
    {:type :rss :url "http://damienkatz.net/rss.php" :period 86400}]})

(defn get-configuration []
  (or (try (read-string (slurp "marky-config.clj"))) default-configuration))

(defn cb-safe 
  "The Couchbase Java SDK doesn't like some characters..."
  [s] (st/escape s {\space "_" \newline "_" \return "_"}))

(defn xml-text [el]
  (st/join " " (filter string? (tree-seq :content :content el))))

(defn fetch-rss [url]
  (let [sourceid (str "rss=" url)
        parsed (xml/parse (StreamSource. url))
        entries (filter #(= :entry (:tag %)) (tree-seq :content :content parsed))]
    (for [entry entries]
           {:source-id sourceid
            :item-id (str sourceid ",title=" 
                          (cb-safe (xml-text (first (filter #(= :title (:tag %)) (:content entry))))))
            :body (.text (Jsoup/parse
                           (xml-text (first (filter #(= :content (:tag %)) (:content entry))))))})))

(def twitter-status-url "http://api.twitter.com/1/statuses/user_timeline.json")

(defn fetch-twitter [user]
  (let [sourceid (str "twitter=" user)
        tweets (:body (http/get twitter-status-url {:as :json :query-params {:screen_name user}}))
        not-replies (filter #(not (:in_reply_to_screen_name %)) tweets)]
    (for [tweet not-replies]
      {:source-id sourceid
       :item-id (str sourceid ",id=" (:id_str tweet))
       :body (:text tweet)})))

(defn send-tweet [cfg]
  (let [creds (apply tw-oauth/make-oauth-creds ((juxt :app-key
                                                      :app-secret
                                                      :user-token
                                                      :user-secret)
                                                  (:twitter cfg)))
        txt (subs (gen/generate-text cfg 140) 0 (min 140 (+ 40 (rand-int 140))))
        tweet (subs txt 0 (.lastIndexOf txt " "))]
    (println "Tweeting:" tweet)
    (tw-rest/update-status :oauth-creds creds :params {:status tweet})))

(defn now-seconds []
  (long (/ (.getTime (java.util.Date.)) 1000)))

(defn fetchwrap [fetchjobfn & parmkeys]
  (fn [job]
    (let [items (apply fetchjobfn ((apply juxt parmkeys) job))]
      (println "\tFetched. Inserting into Couchbase.")
      (doseq [item items]
        (println "\tItem:" (pr-str item))
        (cb/force! (:couchclient job) (:item-id item) item
                   (if-let [ttl-days (:ttl job)]
                     (+ (now-seconds) (* 86400 ttl-days))
                     0))))))

(def jobfns
  {:rss (fetchwrap fetch-rss :url)
   :twitter (fetchwrap fetch-twitter :user)
   :send-tweet (fn [{:keys [config]}] (send-tweet config))})

(defn job-exec-fn [job]
  (fn []
    (println "Executing:" (pr-str (dissoc job :config :couchclient)))
    (if-let [jobfn (jobfns (:type job))]
      (jobfn job)
      (println ".. don't know how to do that!"))))

(defn install-design-doc [fact]
  (let [ddoc {:views {:marky {:map (slurp (io/resource "map.js"))
                              :reduce "_sum"}}}
        ddoc-json (json/encode ddoc)]
    (cbv/install-ddoc (cb/capi-bases fact) "marky" ddoc-json)))

(defn -main [& args]
  (cb/set-transcoder! xcoders/json-transcoder)
  (let [cfg (get-configuration)
        atpool (at-/mk-pool)
        cbfactory (cb/factory (:bucket cfg) (:pass cfg) (:cburl cfg))
        cbc (cb/client cbfactory)]
    (install-design-doc cbfactory)
    (doseq [{:keys [period] :as job} (:jobs cfg)]
      (at-/every (* 1000 period)
                 (job-exec-fn (merge job {:config cfg :couchclient cbc})) atpool
                 :initial-delay (* 1000 (:after job 0))))))

