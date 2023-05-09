(ns jepsen.docker
  (:require [jepsen.control :as c]))

(defn start-container
  [{:keys [label image ports mount args]}]
  (let [port-str (->> ports
                      (mapcat (fn [port] ["-p" (str port ":" port)]))
                      (into []))

        mount-str (->> mount
                       (mapcat (fn [[local remote]] ["-v" (str local ":" remote ":ro")]))
                       (into []))

        arg-str (->> args
                     (map c/escape)
                     (into []))
        ]
    (c/su
      (c/exec :docker :run :--label (str "component=" label) :-itd :--rm port-str mount-str image arg-str))))

(defn stop-container
  [label]
  (c/su
    (c/exec :docker :ps :-aq :--filter (str "label=component=" label) :| :xargs :docker :kill :|| :true)))


(defn stop-all
  []
  (c/su
    (c/exec :docker :ps :-aq :| :xargs :docker :kill :|| :true)))
