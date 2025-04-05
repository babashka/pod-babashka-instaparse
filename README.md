# pod-babashka-instaparse

A pod exposing [Instaparse](https://github.com/Engelberg/instaparse) to babashka.

It's recommended to use this pod via the [instaparse.bb](https://github.com/babashka/instaparse.bb) library.

## API

Only a subset of instaparse is exposed. If you are missing functionality, please create an issue.

### pod.babashka.instaparse

- `parser`
- `parse`
- `parses`
- `failure?`

## Differences with instaparse

- Parser only works on a string grammar input
- The result of `parser` must be used with `parse` or `parses`, it cannot be called as a function directly

## Example

``` clojure
(require '[babashka.pods :as pods])

(pods/load-pod 'org.babashka/instaparse "0.0.5")

;; loading the pod creates the instaparse.core namespace

(require '[pod.babashka.instaparse :as insta])

(def as-and-bs
  (insta/parser
   "S = AB*
    AB = A B
    A = 'a'+
    B = 'b'+"))

(prn (insta/parse as-and-bs "aaaaabbbaaaabb"))

(def failure (insta/parse as-and-bs "xaaaaabbbaaaabb"))

(prn failure)

(prn :failure? (insta/failure? failure))
```

## Build

Run `script/compile`. This requires `GRAALVM_HOME` to be set.

## Test

To test the pod code with JVM clojure, run `clojure -M test.clj`.

To test the native image with bb, run `bb test.clj`.

## License

Copyright © Michiel Borkent

Distributed under the EPL 1.0 license, same as Instaparse and Clojure. See LICENSE.
