#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.edn :as edn]
            ))

(def cli-options
  [["-m" "--minimum keys_num" "Minimum keys signed before"
    :default 1
    :parse-fn #(Integer/parseInt %)    
    ]
   ["-M" "--maximum keys_num" "Maximum keys to sign after"
    :default 65536
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]]
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

(let [{:keys [addr keys options exit-message ok?]} (validate-args *command-line-args*)]
  (cond
    exit-message (exit (if ok? 0 1) exit-message)  
    :else
    (do
      (println (java.util.Date.))
      (println "Signing...")
      (println "addr = " addr)
      (println "keys = " keys)
      (println "opts = " options)
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
            trans (filter #(and (= dest (% "dest")) (>= (edn/read-string (% "signsReceived")) (options :minimum)) (< (edn/read-string (% "signsReceived")) (options :maximum))) (-> (get res "output") (get "transactions")))]
        ;(println "trans = " trans)
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
