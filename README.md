# marky is a robotic text generator that leverages Couchbase

Looks for a marky-config.clj file in current directory.

Sample:

```clojure
{:bucket "default"
 :pass ""
 :cburl "http://localhost:8091/"
 :jobs
 [{:type :twitter :user "damienkatz" :period 3600}
  {:type :twitter :user "apage43" :period 3600}
  {:type :rss :url "http://damienkatz.net/rss.php" :period 86400}]}
```

Run the collector process

    $ lein run -m marky.collect

Generate some text

    $ lein run -m marky.generate

