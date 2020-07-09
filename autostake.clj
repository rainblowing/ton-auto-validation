#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :as pp]
            ))

(def cli-options
  [["-f" "--file PATH" "Path to edn file with staking configs"
    :default "stake.edn"
    ]
   ["-h" "--help"]]
  )

(defn usage [options-summary]
  (->>
   ["Autostaking for FreeTON"
    ""
    "Usage: autostake.clj [options] -- <ton address>"
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
      (println (java.util.Date.))
      (println "Staking... ")
      (println "addr = " addr)
      (println "params-file = " (options :file))
      (let [env (into {} (for [[k v] (map #(str/split % #"=" 2)
                                          (-> (str (System/getenv))
                                              (subs 1)
                                              (str/split #", |}")
                                              ))] [k v]))
            dest (->
                  (slurp (str (env "KEYS_DIR") "/elections/elector-addr-base64"))
                  str/trim
                  )
            msig (->
                  (slurp (str (env "KEYS_DIR") "/" (env "VALIDATOR_NAME") ".addr"))
                  str/trim
                  )            
            out_trans (->   
                       (sh
                        (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
                        "run" addr
                        "getTransactions" "{}"
                        "--abi" (str (env "KEYS_DIR") "/" "SafeMultisigWallet.abi.json"))
                       (get :out)
                       (str/split #"Result:" 2)
                       last
                       str/trim
                       json/parse-string)
            trans (filter #(= dest (% "dest")) (get out_trans "transactions"))
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
            stake-size (params :stake)
            stake-tries (params :autostake-tries)
            stake-res-file (str (options :file) ".stake")
            stake-tried (->
                         (try
                           (->
                            stake-res-file
                            slurp
                            str
                            edn/read-string
                            )
                           (catch Exception e (println e)
                                  (spit stake-res-file {:autostake-tried 0})
                                  {:autostake-tried 0}
                                  )
                           )
                         (get :autostake-tried)
                         )
            ]
        (print "out = ")
        (pp/pprint out_trans)
        (println "dest = " dest)
        (println "msig = " msig)
        (println "stake-size = " stake-size)
        (println "Tried " stake-tried " of " stake-tries)
        (cond
          (= res 0) (do
                      (println "Elections hasn't started")
                      (if (not (= stake-tried 0)) (spit stake-res-file {:autostake-tried 0}))
                      )                      
          (and (not (= res 0)) (< stake-tried stake-tries)) (do
                                                              (println "Elections started" res)
                                                              (println (sh
                                                                        (str (env "NET_TON_DEV_SRC_TOP_DIR") "/scripts/validator_msig.sh")
                                                                        (str stake-size)
                                                                        ))
                                                              (spit stake-res-file {:autostake-tried (+ stake-tried 1)})
                                                              )
          :else (println "Too many staking tries this election round")
          )
        )
      )
    )
  )


