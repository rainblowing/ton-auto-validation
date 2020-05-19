#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            ))

(let [addr *command-line-args*]
  (cond
    (nil? addr) (println "USAGE: stake.clj <ton address>")  
    :else
    (do
      (println "addr = " addr)
      (let [env (into {} (for [[k v] (map #(str/split % #"=" 2)
                                          (-> (str (System/getenv))
                                              (subs 1)
                                              (str/split #", |}")
                                              ))] [k v]))
            dest (env "elector_addr")
            out_trans (->   
                       (sh
                        (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli") "run" addr
                        "getTransactions" "{}"
                        "--abi" (str (env "CONFIGS_DIR") "/" "SafeMultisigWallet.abi.json"))
                       (get :out)
                       (str/split #"Result:" 2)
                       last
                       str/trim
                       json/parse-string)
            trans (filter #(= dest (% "dest")) (-> (get out_trans "output") (get "transactions")))
            out (->
                 (sh
                  (str (env "TON_BUILD_DIR") "/lite-client/lite-client")
                  "-p" (str (env "KEYS_DIR") "/liteserver.pub")
                  "-a" "127.0.0.1:3031"
                  "-rc" (str "runmethod " dest " active_election_id")
                  "-rc" "quit")
                 (get :out)
                 )
            res (->           
                 (str/split out #"result:" 2)
                 last
                 (str/split #"\n" 2)
                 first
                 str/trim
                 json/parse-string
                 first
                 )
            params (->
                    (slurp "stake.json")
                    str
                    json/parse-string
                    )
            stake-size (params "stake")
            ]
        
        (println (java.util.Date.))
        
        (cond
          (not (empty? trans)) (do
                                 (println "There are unsigned transactions for voting")
                                 (println "trans = " trans)
                                 )
          (not (= res 0)) (do
                            (println "Elections started" res)
                            (println "rqb" (env "recover_query_boc"))

                            (->
                             (sh
                              (str (env "UTILS_DIR") "/tonos-cli")
                              "call" (env "MSIG_ADDR")
                              "submitTransaction" (str "{\"dest\":\"" dest "\",\"value\":1000000000,\"bounce\":true,\"allBalance\":false,\"payload\":\"" (env "recover_query_boc") "\"}")
                              "--abi" (str (env "CONFIGS_DIR") "/SafeMultisigWallet.abi.json")
                              "--sign" (str (env "KEYS_DIR") "/msig.keys.json")
                              )
                             (println)
                             )

                            (println "Staking " stake-size)
                            (sh
                             (str (env "NET_TON_DEV_SRC_TOP_DIR") "/scripts/validator_msig.sh")
                             (str stake-size)
                             )
                            )
          :else (println "Elections hasn't started")
          )
        )
      )
    )
  )

