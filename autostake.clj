#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :as shell :refer [sh]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :as pp]
            ))

(def cli-options
  [["-f" "--file PATH" "Path to edn file with staking configs"
    :default "stake.edn"
    ]
   ["-h" "--help"]
   ["-c" "--config PATH" "location of config file"
    :default "default"]
   [nil "--abi file" "link to abi json file"
    :default ""]
   [nil "--sign key" "path to key or seed phrase"
    :default ""]
   ]
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

(defn get-tx-result
  "gets result from txs"
  [trans]
  (pp/pprint trans)
  (cond
    (= (trans :exit) 0)
    (->
     trans
     (get :out)
     (str/split #"Result:" 2)
     last
     str/trim
     json/parse-string
     )
    :else
    trans
    )
  )

(defn take-env
  "reads environment variables"
  []
  (into {} (for [[k v] (map #(str/split % #"=" 2)
                            (-> (str (System/getenv))
                                (subs 1)
                                (str/split #", |}")))] [k v]))
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
      (let [env (take-env)
            tonos-cli (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
            config (options :config)
            abi (cond
                  (empty? (options :abi)) (str (env "KEYS_DIR") "/SafeMultisigWallet.abi.json")
                  :else (options :abi))
            sign (cond
                   (empty? (options :sign)) (str (env "KEYS_DIR") "/msig.keys.json")
                   :else (options :sign))
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
                        tonos-cli
                        "-c" config
                        "run" addr
                        "getTransactions" "{}"
                        "--abi" abi)
                       get-tx-result)
            out (->
                 (sh
                  (str (env "TON_BUILD_DIR") "/lite-client/lite-client")
                  ;"-c" config
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
        (println "config = " config)
        (println "abi = " abi)
        (println "sign = " sign)
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


