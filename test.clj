#!/usr/bin/env bb

(require '[babashka.pods :as pods])

(if (= "executable" (System/getProperty "org.graalvm.nativeimage.kind"))
  (pods/load-pod "./pod-babashka-instaparse")
  (pods/load-pod ["clojure" "-M" "-m" "pod.babashka.instaparse"]))

(require '[pod.babashka.instaparse :as insta])

(def as-and-bs
  (insta/parser
   "S = AB*
    AB = A B
    A = 'a'+
    B = 'b'+"))

#_(prn (as-and-bs "aaaaabbbaaaabb"))

(prn (insta/parse as-and-bs "aaaaabbbaaaabb"))

(def failure (insta/parse as-and-bs "xaaaaabbbaaaabb"))

(prn failure)
(prn :failure (insta/failure? failure))

(when-not (= "executable" (System/getProperty "org.graalvm.nativeimage.kind"))
  (shutdown-agents)
  (System/exit 0))
