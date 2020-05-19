#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            ))

(let [[addr & keys] *command-line-args*]
  (cond
    (nil? addr) (println "USAGE: autosign.clj <ton address> [path_to_key1] [path_to_key2] ...")  
    :else
    (do
      (println "addr = " addr)
      (println "keys = " keys)
      (let [env (into {} (for [[k v] (map #(str/split % #"=" 2)
                                          (-> (str (System/getenv))
                                              (subs 1)
                                              (str/split #", |}")))] [k v]))
            dest (env "elector_addr")
            out (->   
                 (sh
                  (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli") "run" addr
                  "getTransactions" "{}"
                  "--abi" (str (env "CONFIGS_DIR") "/" "SafeMultisigWallet.abi.json")) 
                 (get :out))
            res (->
                 (str/split out #"Result:" 2)
                 last
                 str/trim
                 json/parse-string)
            trans (filter #(= dest (% "dest")) (-> (get res "output") (get "transactions")))]
        (println "trans = " trans)
        (->>
         (for [x trans
               y keys]
           (list x y)
           )
         (map #(list (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli") "call" addr 
                     "confirmTransaction" (str "{\"transactionId\":\"" ((first %) "id") "\"}")
                     "--abi" (str (env "CONFIGS_DIR") "/" "SafeMultisigWallet.abi.json")
                     "--sign" (second %)))
         (map #(do (println %) (apply sh %)))
         )
        )
      )
    )
  )
