(ns jepsen.docker
  (:require [jepsen.control :as c]))

(defn start-container
  [{:keys [label image ports mount args]}]
  (let [port-str (->> ports
                      (mapcat (fn [port] ["-p" (str port ":" port)]))
                      (into []))

        mount-str (->> mount
                       (mapcat (fn [[local remote]] ["-v" (str local ":" remote)]))
                       (into []))

        arg-str (->> args
                     (map c/escape)
                     (into []))
        ]
    (c/su
      (c/exec :docker :run :--label (str "component=" label) :-itd :--rm port-str mount-str image arg-str))))

(defn create-volume [name]
  (c/su
    (c/exec :docker :volume :create name)))

(defn delete-volume [name]
  (c/su
    (c/exec :docker :volume :rm :-f name)))

(defn stop-container
  [label]
  (c/su
    (c/exec :docker :ps :-aq :--filter (c/escape (str "label=component=" label)) :| :xargs :docker :kill :|| :true)))


(defn stop-all
  []
  (c/su
    (c/exec :docker :ps :-aq :| :xargs :docker :kill :|| :true)))
