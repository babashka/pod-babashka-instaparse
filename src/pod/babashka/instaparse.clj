(ns pod.babashka.instaparse
  (:refer-clojure :exclude [read-string read])
  (:require [bencode.core :as bencode]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [instaparse.core :as insta])
  (:import [java.io PushbackInputStream])
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

(defn mark-failure [x]
  (if (insta/failure? x)
    (assoc x ::failure true)
    x))

(defn parse [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (-> (apply insta/parse p opts)
        mark-failure)))

(defn parses [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (-> (apply insta/parses p opts)
        mark-failure)))

(def lookup*
  {'pod.babashka.instaparse
   {#_#_'-parser -parser
    'parse parse
    'parses parses
    'parser -parser
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
                         {"name" "failure?" "code" "(defn failure? [x] (boolean (:pod.babashka.instaparse/failure x)))"}]}]}))

(defn read-transit [^String v]
  (transit/read
   (transit/reader
    (java.io.ByteArrayInputStream. (.getBytes v "utf-8"))
    :json)))

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
    (transit/write (transit/writer baos :json) v)
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
