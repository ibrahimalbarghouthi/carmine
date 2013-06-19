(ns taoensso.carmine.utils
  {:author "Peter Taoussanis"}
  (:require [clojure.string      :as str]
            [clojure.tools.macro :as macro]))

(defmacro declare-remote
  "Declares the given ns-qualified names, preserving symbol metadata. Useful for
  circular dependencies."
  [& names]
  (let [original-ns (str *ns*)]
    `(do ~@(map (fn [n]
                  (let [ns (namespace n)
                        v  (name n)
                        m  (meta n)]
                    `(do (in-ns  '~(symbol ns))
                         (declare ~(with-meta (symbol v) m))))) names)
         (in-ns '~(symbol original-ns)))))

(defmacro defonce*
  "Like `clojure.core/defonce` but supports optional docstring and attributes
  map for name symbol."
  {:arglists '([name expr])}
  [name & sigs]
  (let [[name [expr]] (macro/name-with-attributes name sigs)]
    `(clojure.core/defonce ~name ~expr)))

(defmacro defalias
  "Defines an alias for a var, preserving metadata. Adapted from
  clojure.contrib/def.clj, Ref. http://goo.gl/xpjeH"
  [name target & [doc]]
  `(let [^clojure.lang.Var v# (var ~target)]
     (alter-meta! (def ~name (.getRawRoot v#))
                  #(merge % (apply dissoc (meta v#) [:column :line :file :test :name])
                            (when-let [doc# ~doc] {:doc doc#})))
     (var ~name)))

(defmacro time-ns "Returns number of nanoseconds it takes to execute body."
  [& body] `(let [t0# (System/nanoTime)] ~@body (- (System/nanoTime) t0#)))

(defmacro bench
  "Repeatedly executes form and returns time taken to complete execution."
  [num-laps form & {:keys [warmup-laps num-threads as-ns?]}]
  `(try (when ~warmup-laps (dotimes [_# ~warmup-laps] ~form))
        (let [nanosecs#
              (if-not ~num-threads
                (time-ns (dotimes [_# ~num-laps] ~form))
                (let [laps-per-thread# (int (/ ~num-laps ~num-threads))]
                  (time-ns
                   (->> (fn [] (future (dotimes [_# laps-per-thread#] ~form)))
                        (repeatedly ~num-threads)
                        doall
                        (map deref)
                        dorun))))]
          (if ~as-ns? nanosecs# (Math/round (/ nanosecs# 1000000.0))))
        (catch Exception e# (str "DNF: " (.getMessage e#)))))

(defn version-compare "Comparator for version strings like x.y.z, etc."
  [x y] (let [vals (fn [s] (vec (map #(Integer/parseInt %) (str/split s #"\."))))]
          (compare (vals x) (vals y))))

(defn version-sufficient? [version-str min-version-str]
  (try (>= (version-compare version-str min-version-str) 0)
       (catch Exception _ false)))

(defn keyname
  "Like `name` but supports integers and includes namespace in string when
  present."
  [x]
  (cond (string?  x) x
        (keyword? x) (let [n (name x)] (if-let [ns (namespace x)]
                                         (str ns "/" n) n))
        (integer? x) (str x)
        :else (throw (Exception. (str "Invalid keyname type: " (type x))))))

(comment (map keyname [:foo :foo/bar 12 :foo.bar/baz])
         (time (dotimes [_ 10000] (name :foo)))
         (time (dotimes [_ 10000] (keyname :foo))))

(defn keywordize-map [m] (reduce-kv (fn [m k v] (assoc m (keyword k) v)) {} (or m {})))

(defn repeatedly* "Like `repeatedly` but faster and returns a vector."
  [n f]
  (loop [v (transient []) idx 0]
    (if (>= idx n)
      (persistent! v)
      (recur (conj! v (f)) (inc idx)))))

(defn ba= [^bytes x ^bytes y] (java.util.Arrays/equals x y))