(ns jepsen.ops
  (:require [jepsen.independent :as independent])
  )

(defn r [_ _] {:type :invoke, :f :read, :value nil})
(defn w [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(def all [r w cas])
(defmulti op->grpc-method :f)
(defmulti op->request :f)
(defmulti op->handle-ok (fn [op _] (:f op)))

(defn op->key [op]
  (-> op :value first))

(defn op->val [op]
  (-> op :value second))

;;
;; read
;;

(defmethod op->grpc-method :read [_] "/Read")
(defmethod op->request :read [op]
  {:key (op->key op)})

(defmethod op->handle-ok :read [op res]
  (let [k (op->key op)
        v (-> res :value (or 0))]
    (assoc op :type :ok, :value (independent/tuple k v))))

;;
;; write
;;

(defmethod op->grpc-method :write [_] "/Write")
(defmethod op->request :write [op]
  {:key (op->key op) :value (op->val op)})
(defmethod op->handle-ok :write [op _]
  (assoc op :type :ok))

;;
;; cas
;;

(defmethod op->grpc-method :cas [_] "/Cas")
(defmethod op->request :cas [op]
  (let [[compare exchange] (op->val op)]
    {:key (op->key op) :compare compare :exchange exchange}))

(defmethod op->handle-ok :cas [op body]
  (assoc op :type (if (:success body)
                    :ok
                    :fail)))
