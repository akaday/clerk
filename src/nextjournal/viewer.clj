(ns nextjournal.viewer
  (:refer-clojure :exclude [meta with-meta vary-meta])
  (:require [clojure.core :as core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom metadata handling - supporting any cljs value - not compatible with core meta

(defn meta? [x] (and (map? x) (contains? x :nextjournal/value)))

(defn meta [data]
  (if (meta? data)
    data
    (assoc (core/meta data)
           :nextjournal/value (cond-> data
                                (instance? clojure.lang.IMeta data) (core/with-meta {})))))

(defn with-meta [data m]
  (cond (meta? data) (assoc m :nextjournal/value (:nextjournal/value data))
        (instance? clojure.lang.IMeta data) (core/with-meta data m)
        :else
        (assoc m :nextjournal/value data)))

#_(with-meta {:hello :world} {:foo :bar})
#_(with-meta "foo" {:foo :bar})

(defn vary-meta [data f & args]
  (with-meta data (apply f (meta data) args)))

(defn type-key [value]
  (cond
    (var? value) :var
    (instance? clojure.lang.IDeref value) :derefable
    (map? value) :map
    (set? value) :set
    (vector? value) :vector
    (list? value) :list
    (seq? value) :list
    (fn? value) :fn
    (uuid? value) :uuid
    (string? value) :string
    (keyword? value) :keyword
    (symbol? value) :symbol
    (nil? value) :nil
    (boolean? value) :boolean
    (inst? value) :inst
    :else :untyped))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Viewers (built on metadata)

(defn with-viewer
  "The given viewer will be used to display data"
  [data viewer]
  (vary-meta data assoc :nextjournal/viewer viewer))

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [data viewers]
  (vary-meta data assoc :nextjournal/viewers viewers))

(defn view-as
  "Like `with-viewer` but takes viewer as 1st argument"
  [viewer data]
  (with-viewer data viewer))

#_(view-as :latex "a^2+b^2=c^2")

(defn html [x]
  (with-viewer x (if (string? x) :html :hiccup)))

(defn vl [x]
  (with-viewer x :vega-lite))

(defn plotly [x]
  (with-viewer x :plotly))

(defn md [x]
  (with-viewer x :markdown))

(defn tex [x]
  (with-viewer x :latex))

;; TODO: hack for talk to make sql result display as table, propery support SQL results as tables and remove
(defn ->table
  "converts a sequence of maps into a table with the first row containing the column names."
  [xs]
  (let [cols (sort (keys (first xs)))]
    (into [cols]
          (map (fn [row] (map #(get row %) cols)))
          xs)))

#_(->table [{:a 1 :b 2 :c 3} {:a 3 :b 0 :c 2}])

(defn table [xs]
  (view-as :table (->table xs)))

(defmacro register-viewers! [v]
  `(nextjournal.viewer/with-viewer
     ::register!
     (quote (let [viewers# ~v]
              (nextjournal.viewer/register-viewers! viewers#)
              (constantly viewers#)))))

#_
(macroexpand-1 (register-viewers! {:vector (fn [x options]
                                             (html (into [:div.flex.inline-flex] (map (partial inspect options)) x)))}))


(defn registration? [x]
  (-> x meta :nextjournal/value (partial = ::register!)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pagination + Lazy Loading
(defn describe [result]
  (cond-> {:nextjournal/type-key (type-key result) :blob/id (-> result core/meta :blob/id)}
    (counted? result)
    (assoc :count (count result))))

#_(describe (vec (range 100)))

(defn paginate [result {:as opts :keys [start n] :or {start 0}}]
  (if (and (number? n)
           (pos? n)
           (not (or (map? result)
                    (set? result)))
           (counted? result))
    (core/with-meta (->> result (drop start) (take n) doall) (merge opts (describe result)))
    result))

#_(meta (paginate (vec (range 10)) {:n 20}))
#_(meta (paginate (vec (range 100)) {:n 20}))
#_(meta (paginate (zipmap (range 100) (range 100)) {:n 20}))
#_(paginate #{1 2 3} {:n 20})
