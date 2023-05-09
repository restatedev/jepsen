(ns jepsen.ops)

(defn r [_ _] {:type :invoke, :f :read, :value nil})
(defn w [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(def all [r w])
(defmulti op->grpc-method :f)
(defmulti op->argument :f)
(defmulti op->handle-ok (fn [op _] (:f op)))

;;
;; read
;;
(defmethod op->grpc-method :read [_] "/Read")
(defmethod op->argument :read [_]
  {:key "r0"})
(defmethod op->handle-ok :read [op res]
  (let [value (:value res)]
    (assoc op :type :ok, :value (if (nil? value) 0 value))))
;;
;; write
;;

(defmethod op->grpc-method :write [_] "/Write")
(defmethod op->argument :write [op]
  {:key "r0" :value (:value op)})
(defmethod op->handle-ok :write [op _]
  (assoc op :type :ok))

;;
;; cas
;;

(defmethod op->grpc-method :cas [_] "/Cas")
(defmethod op->argument :cas [op]
  (let [[compare exchange] (:value op)]
    {:key "r0" :compare compare :exchange exchange}
    ))
(defmethod op->handle-ok :cas [op _]
  (assoc op :type :ok))


