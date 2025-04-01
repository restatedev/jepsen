(ns jepsen-patched.util
  (:require
   [clojure.tools.logging :refer [info]]
   [slingshot.slingshot :refer [throw+ try+]]))

(defn ^Long linear-time-nanos
  "A linear time source in nanoseconds."
  []
  (System/nanoTime))

(defn await-fn
  "Invokes a function (f) repeatedly. Blocks until (f) returns, rather than
  throwing. Returns that return value. Catches Exceptions (except for
  InterruptedException) and retries them automatically. Options:

    :retry-interval   How long between retries, in ms. Default 1s.
    :log-interval     How long between logging that we're still waiting, in ms.
                      Default `retry-interval.
    :log-message      What should we log to the console while waiting?
    :timeout          How long until giving up and throwing :type :timeout, in
                      ms. Default 60 seconds."
  ([f]
   (await-fn f {}))
  ([f opts]
   (let [log-message    (:log-message opts)
         status-fn      (:status-fn opts (fn [_opts]))
         retry-interval (long (:retry-interval opts 1000))
         log-interval   (:log-interval opts retry-interval)
         timeout        (:timeout opts 60000)
         t0             (linear-time-nanos)
         log-deadline   (atom (+ t0 (* 1e6 log-interval)))
         deadline       (+ t0 (* 1e6 timeout))]
     (loop []
       (let [res (try
                   (f)
                   (catch InterruptedException e
                     (throw e))
                   (catch Exception e
                     (let [now (linear-time-nanos)]
                       ; Are we out of time?
                       (when (<= deadline now)
                         (throw+ {:type :timeout} e))

                       ; Should we log something?
                       (when (<= @log-deadline now)
                         (when log-message (info log-message))
                         (when status-fn (try+ (status-fn opts)))
                         (swap! log-deadline + (* log-interval 1e6)))

                       ; Right, sleep and retry
                       (Thread/sleep retry-interval)
                       ::retry)))]
         (if (= ::retry res)
           (recur)
           res))))))
