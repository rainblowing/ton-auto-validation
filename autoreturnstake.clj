#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            ))

(def cli-options
  [["-f" "--file PATH" "Path to edn file with staking configs"
    :default "stake.edn"
    ]
   ["-h" "--help"]]
  )

(defn usage [options-summary]
  (->>
   ["Autoreturn of Stake for FreeTON"
    ""
    "Usage: autoreturnstake.clj [options] -- <ton address>"
    ""
    "Options:"
    options-summary
    ""
    "<ton address> - full address on TON blockchain"
    ]
   (str/join \newline)
   )
  )

(defn error-message [errors]
  (str "Errors:\n" (str/join \newline errors))
  )

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary) :ok? :true}
      errors {:exit-message (error-message errors)}
      (> (count arguments) 0) {:addr (first arguments) :options options}
      :else {:exit-message (usage summary)}
      )
    )
  )

(defn exit [status msg]
  (println msg)
  (System/exit status)
  )

(let [{:keys [addr options exit-message ok?]} (validate-args *command-line-args*)]
  (cond
    exit-message (exit (if ok? 0 1) exit-message)  
    :else
    (do
      (println "addr = " addr)
      (println "params-file = " (options :file))
      (let [env (into {} (for [[k v] (map #(str/split % #"=" 2)
                                          (-> (str (System/getenv))
                                              (subs 1)
                                              (str/split #", |}")
                                              ))] [k v]))
            dest (env "elector_addr")
            out_trans (->   
                       (sh
                        (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
                        "run" addr
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
                    (options :file)
                    slurp
                    str
                    edn/read-string
                    )
            return-tries (params :autoreturn-tries)
            return-res-file (str (options :file) ".return")
            return-tried (->
                          (try
                            (->
                             return-res-file
                             slurp
                             str
                             edn/read-string
                             )
                            (catch Exception e (println e)
                                   (spit return-res-file {:autoreturn-tried 0})
                                   {:autoreturn-tried 0}
                                   )
                            )
                          (get :autoreturn-tried)
                          )
            ]
        
        (println (java.util.Date.))
        (println "Returning stake...")
        (println "Tried " return-tried " of " return-tries)
        
        (cond
          (= res 0) (do
                      (println "Elections hasn't started")
                      (if (not (= return-tried 0)) (spit return-res-file {:autoreturn-tried 0}))
                      )
          (and (not (= res 0)) (< return-tried return-tries)) (do
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
                                                              (spit return-res-file {:autoreturn-tried (+ return-tried 1)})
                                                              )
          :else (println "Too many staking tries this election round")
          )
        )
      )
    )
  )


