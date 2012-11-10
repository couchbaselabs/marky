# marky is a nonsensical tweet bot that leverages Couchbase

Looks for a marky-config.clj file in current directory.

Sample:

```clojure
{:bucket "default"
 :pass ""
 :cburl "http://localhost:8091/"
 :twitter {:app-key "XXXXXXXXX"
           :app-secret "XXXXXXXXXX"
           :user-token "XXXXXXXX"
           :user-secret "XXXXXXXX"}
 :jobs
 [; :period, :after is in seconds, :ttl is in days.
  {:type :twitter :user "damienkatz" :period 3600 :ttl 60}
  {:type :twitter :user "apage43" :period 3600 :ttl 60}
  {:type :send-tweet :period 3600 :after 600}
  {:type :rss :url "http://damienkatz.net/rss.php" :period 86400 :ttl 60}]}
```

Run the bot process

    $ lein run -m marky.app

