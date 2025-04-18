(ns pod.babashka.instaparse
  (:refer-clojure :exclude [read-string read])
  (:require [bencode.core :as bencode]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [instaparse.core :as insta])
  (:import [java.io PushbackInputStream]
           [instaparse.gll Failure])
  (:gen-class))

(set! *warn-on-reflection* true)

(def stdin (PushbackInputStream. System/in))
(def stdout System/out)

(def debug? true)

(defn debug [& strs]
  (when debug?
    (binding [*out* (io/writer System/err)]
      (apply prn strs))))

(defn write
  ([v] (write stdout v))
  ([stream v]
   ;; (debug :writing v)
   (bencode/write-bencode stream v)
   (flush)))

(defn read-string [^"[B" v]
  (String. v))

(defn read [stream]
  (bencode/read-bencode stream))

(def regex-key (str ::regex))

(defn reg-transit-handlers
  []
  (format
   "
(babashka.pods/add-transit-read-handler!
  \"%s\"
  re-pattern)

(babashka.pods/add-transit-write-handler!
  #{java.util.regex.Pattern}
  \"%s\"
  str)
"
   regex-key regex-key))

(def parsers
  (atom {}))

(defn -parser [grammar & opts]
  (let [parser (apply insta/parser grammar opts)
        id (gensym)]
    (swap! parsers assoc id parser)
    {::id id}))

(defn -call-parser [ref s]
  (let [id (::id ref)
        p (get @parsers id)]
    (p s)))

#_(def parser-wrapper (str '(do (defn parser [grammar]
                                  (let [p (-parser grammar)]
                                    p #_(fn [s]
                                          (-call-parser p s)))))))

(defn- wrap-failure
  "The instaparse.gll.Failure class is lost in the (de)serialization between
  pod server and client (even if we use Transit read & write handlers because
  on the client side we don't have access to that class and create it in an SCI
  runtime environment; so we can't implement a working write handler there).
  So we wrap failures in a map with a qualified ::failure keyword key and then
  look for that in our wrapper of `instaparse.core/failure?`."
  [result]
  (if (insta/failure? result)
    {::failure result}
    result))

(defn parse [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (-> insta/parse
        (apply p opts)
        wrap-failure)))

(defn parses [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (apply insta/parses p opts)))

(defn span [tree]
  (insta/span tree))

(defn failure? [result]
  (contains? result ::failure))

(def lookup*
  {'pod.babashka.instaparse
   {#_#_'-parser -parser
    'parse parse
    'parses parses
    'parser -parser
    'span span
    'failure? failure?
    #_#_'-call-parser -call-parser}})

(defn lookup [var]
  (let [var-ns (symbol (namespace var))
        var-name (symbol (name var))]
    (get-in lookup* [var-ns var-name])))

(def describe-map
  (walk/postwalk
   (fn [v]
     (if (ident? v) (name v)
         v))
   {:format :transit+json
    :namespaces [{:name "pod.babashka.instaparse"
                  :vars [#_{"name" "-parser"}
                         #_{"name" "-call-parser"}
                         {"name" "parser" #_#_"code" parser-wrapper}
                         {"name" "parse"}
                         {"name" "parses"}
                         {"name" "span" "arg-meta" "true"}
                         {"name" "failure?"}
                         ;; register client side transit handlers when pod is loaded. Implementation detail.
                         {"name" "-reg-transit-handlers"
                          "code"  (reg-transit-handlers)}]}]}))

(def regex-read-handler (transit/read-handler re-pattern))

(def regex-write-handler (transit/write-handler regex-key str))

(defn read-transit [^String v]
  (transit/read
   (transit/reader
    (java.io.ByteArrayInputStream. (.getBytes v "utf-8"))
    :json
    {:handlers {regex-key regex-read-handler}})))

(defn auto-seq? [x]
  (instance? instaparse.auto_flatten_seq.AutoFlattenSeq x))

(defn serialize- [x]
  (if (instance? instaparse.auto_flatten_seq.AutoFlattenSeq x)
    (seq x)
    x))

(defn serialize [x]
  (clojure.walk/prewalk serialize- x))

(defn write-transit [v]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer baos
                                   :json
                                   {:handlers {java.util.regex.Pattern regex-write-handler}
                                    :transform transit/write-meta}) v)
    (.toString baos "utf-8")))

(defn -main [& _args]
  (loop []
    (let [message (try (read stdin)
                       (catch java.io.EOFException _
                         ::EOF))]
      (when-not (identical? ::EOF message)
        (let [op (get message "op")
              op (read-string op)
              op (keyword op)
              id (some-> (get message "id")
                         read-string)
              id (or id "unknown")]
          (case op
            :describe (do (write stdout describe-map)
                          (recur))
            :invoke (do (try
                          (let [var (-> (get message "var")
                                        read-string
                                        symbol)
                                args (get message "args")
                                args (read-string args)
                                args (read-transit args)]
                            (if-let [f (lookup var)]
                              (let [v (apply f args)
                                    ;; _ (debug :value v :type (type v))
                                    value (write-transit (serialize v))
                                    reply {"value" value
                                           "id" id
                                           "status" ["done"]}]
                                (write stdout reply))
                              (throw (ex-info (str "Var not found: " var) {}))))
                          (catch Throwable e
                            (debug e)
                            (let [reply {"ex-message" (ex-message e)
                                         "ex-data" (write-transit
                                                    (assoc (ex-data e)
                                                           :type (str (class e))))
                                         "id" id
                                         "status" ["done" "error"]}]
                              (write stdout reply))))
                        (recur))
            :shutdown (System/exit 0)
            (do
              (let [reply {"ex-message" "Unknown op"
                           "ex-data" (pr-str {:op op})
                           "id" id
                           "status" ["done" "error"]}]
                (write stdout reply))
              (recur))))))))
