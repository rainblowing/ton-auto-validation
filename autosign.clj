#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            ))

(def cli-options
  [["-m" "--minimum keys_num" "Minimum keys signed before"
    :default 1
    :parse-fn #(Integer/parseInt %)    
    ]
   ["-M" "--maximum keys_num" "Maximum keys to sign after"
    :default 65536
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]
   ["-c" "--config PATH" "location of config file"
    :default "default"]
   [nil "--abi file" "link to abi json file"
    :default ""]
   ]
  )

(defn usage [options-summary]
  (->>
   ["Autosigning for FreeTON"
    ""
    "Usage: autosign.clj [options] -- <ton address> <path to key 1> <path to key 2> ..."
    ""
    "Options:"
    options-summary
    ""
    "<ton address> - full address on TON blockchain"
    "<path to key x> - full path to key x"
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
      (> (count arguments) 0) {:addr (first arguments) :keys (rest arguments) :options options}
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


(let [{:keys [addr keys options exit-message ok?]} (validate-args *command-line-args*)]
  (cond
    exit-message (exit (if ok? 0 1) exit-message)  
    :else
    (do
      (println (java.util.Date.))
      (println "Signing...")
      (println "addr = " addr)
      (print "keys = ")
      (pp/pprint keys)
      ;(print "opts = ")
      ;(pp/pprint options)
      (let [env (take-env)
            tonos-cli (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
            config (options :config)
            abi (cond
                  (empty? (options :abi)) (str (env "KEYS_DIR") "/SafeMultisigWallet.abi.json")
                  :else (options :abi))
            dest (->
                  (slurp (str (env "KEYS_DIR") "/elections/elector-addr-base64"))
                  str/trim
                  )
            out (->   
                 (sh
                  tonos-cli
                  "-c" config
                  "run" addr
                  "getTransactions" "{}"
                  "--abi" abi) 
                 get-tx-result)
            trans (filter #(and (= dest (% "dest")) (>= (edn/read-string (% "signsReceived")) (options :minimum)) (< (edn/read-string (% "signsReceived")) (options :maximum))) (get out "transactions"))]
        (println "config = " config)
        (println "abi = " abi)
        (->>
         (for [x trans
               y keys]
           (list x y)
           )
         (map #(list tonos-cli
                     "-c" config
                     "call" addr 
                     "confirmTransaction" (str "{\"transactionId\":\"" ((first %) "id") "\"}")
                     "--abi" abi
                     "--sign" (second %)))
         (map #(do (pp/pprint %) (apply sh %)))
         )
        )
      )
    )
  )
