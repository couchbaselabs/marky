(ns marky.generate
  (:require [cbdrawer.view :as cbv]
            [cbdrawer.client :as cb]))

(defn pick [wset]
  (when-not (empty? wset)
    (let [tot (reduce + (map :value wset))]
      (loop [i (rand tot)
             [{weight :value :as item} & more] (seq wset)]
        (if (>= weight i)
          item
          (recur (- i weight) more))))))

(defn next-word [viewuri word]
  (let [start [word]
        end [word {}]
        candidates (cbv/view-seq viewuri
                                 {:startkey start
                                  :endkey end
                                  :reduce true
                                  :group_level 2
                                  :stale false})
        selected (pick candidates)]
    (or (last (:key selected))
        (recur viewuri nil))))

(defn generate-text [cfg length]
  (let [fact (cb/factory (:bucket cfg) (:pass cfg) (:cburl cfg))
        capis (cb/capi-bases fact)
        viewuri (cbv/view-url capis "marky" "marky")]
    (with-out-str (loop [i length
                         w nil]
                    (let [word (next-word viewuri w)]
                      (print (str word " "))
                      (when (<= 0 i) (recur (- i (count w)) word)))))))

