(ns restate.jepsen.set-ops)

(defn w
  []
  (->> (range)
       (map (fn [x] {:type :invoke, :f :add, :value x}))))

(defn r
  []
  {:type :invoke, :f :read, :value nil})

