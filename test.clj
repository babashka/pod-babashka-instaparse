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

(assert (= [:S [:AB [:A "a" "a" "a" "a" "a"] [:B "b" "b" "b"]] [:AB [:A "a" "a" "a" "a"] [:B "b" "b"]]] (insta/parse as-and-bs "aaaaabbbaaaabb")))

(def failure (insta/parse as-and-bs "xaaaaabbbaaaabb"))

(assert (insta/failure? failure) "should be true")

(def commit-msg-grammar
  "A PEG grammar to validate and parse conventional commit messages."
  (str
    "<S>            =       (HEADER <EMPTY-LINE> FOOTER GIT-REPORT? <NEWLINE>*)
                            / ( HEADER <EMPTY-LINE> BODY (<EMPTY-LINE> FOOTER)? GIT-REPORT? <NEWLINE>*)

                            / (HEADER <EMPTY-LINE> BODY GIT-REPORT? <NEWLINE>*)
                            / (HEADER GIT-REPORT? <NEWLINE>*);"
    "<HEADER>       =       TYPE (<'('>SCOPE<')'>)? <':'> <SPACE> SUBJECT;"
    "TYPE           =       'feat' | 'fix' | 'refactor' | 'perf' | 'style' | 'test' | 'docs' | 'build' | 'ops' | 'chore';"
    "SCOPE          =       #'[a-zA-Z0-9]+';"
    "SUBJECT        =       TEXT ISSUE-REF? TEXT? !'.';"
    "BODY           =       (!PRE-FOOTER PARAGRAPH) / (!PRE-FOOTER PARAGRAPH (<EMPTY-LINE> PARAGRAPH)*);"
    "PARAGRAPH      =       (ISSUE-REF / TEXT / (NEWLINE !NEWLINE))+;"
    "PRE-FOOTER     =       NEWLINE+ FOOTER;"
    "FOOTER         =       FOOTER-ELEMENT (<NEWLINE> FOOTER-ELEMENT)*;"
    "FOOTER-ELEMENT =       FOOTER-TOKEN <':'> <WHITESPACE> FOOTER-VALUE;"
    "FOOTER-TOKEN   =       ('BREAKING CHANGE' (<'('>SCOPE<')'>)?) / #'[a-zA-Z\\-^\\#]+';"
    "FOOTER-VALUE   =       (ISSUE-REF / TEXT)+;"
    "GIT-REPORT     =       (<EMPTY-LINE> / <NEWLINE>) COMMENT*;"
    "COMMENT        =       <'#'> #'[^\\n]*' <NEWLINE?> ;"
    "ISSUE-REF      =       <'#'> ISSUE-ID;"
    "ISSUE-ID       =       #'([A-Z]+\\-)?[0-9]+';"
    "TEXT           =       #'[^\\n\\#]+';"
    "SPACE          =       ' ';"
    "WHITESPACE     =       #'\\s';"
    "NEWLINE        =       <'\n'>;"
    "EMPTY-LINE     =       <'\n\n'>;"))

(def commit-msg-parser (insta/parser commit-msg-grammar))

(assert (= '([:TYPE "feat"] [:SUBJECT [:TEXT "adding a new awesome feature"]])
           (insta/parse commit-msg-parser "feat: adding a new awesome feature")))

(def commit-msg-parser-enlive (insta/parser commit-msg-grammar :output-format :enlive))

;; test nested AutoFlattenSeqs are handled by serialize
(assert (= '({:tag :TYPE, :content ("feat")}
             {:tag :SUBJECT,
              :content ({:tag :TEXT, :content ("adding a new awesome feature")})})
           (insta/parse commit-msg-parser-enlive "feat: adding a new awesome feature")))

(assert (seq? (insta/parse commit-msg-parser-enlive "feat: adding a new awesome feature")))

(assert (= '(({:tag :TYPE, :content ("feat")}
              {:tag :SUBJECT,
               :content
               ({:tag :TEXT, :content ("adding a new awesome feature")})}))
           (insta/parses commit-msg-parser-enlive "feat: adding a new awesome feature") ))

#_(when-not (= "executable" (System/getProperty "org.graalvm.nativeimage.kind"))
    (shutdown-agents)
    (System/exit 0))
