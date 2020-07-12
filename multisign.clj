#!/usr/bin/env bb

(ns ton-validation
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :as pp]
            [clojure.data.csv :as csv]
            ))

(def cli-options
  [["-s" "--submit" "submit only transactions, else will only confirm transactions"]
   ["-h" "--help"]
   ["-c" "--config PATH" "location of config file"
    :default "default"]
   ["-f" "--file PATH" "path to csv file with addresses and amounts"
    :default "addresses.csv"
    ]
   [nil "--abi file" "link to abi json file"
    :default "SafeMultisigWallet.abi.json"]
   [nil "--sign key" "path to key or seed phrase"
    :default ""]
   ])

(defn usage [options-summary]
  (->>
   ["Multisigning for FreeTON"
    ""
    "Usage: multisign.clj [options] -- <ton address>"
    ""
    "Options:"
    options-summary
    ""
    "<ton address> - blockchain address-originator of transactions"
    ]
   (str/join \newline)
   )
  )

(defn error-message [errors]
  (str "Errors:\n" (str/join \newline errors))
  )

(defn validate-args [args]
  (let [{:keys [addr options arguments errors summary]} (parse-opts args cli-options)]
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

(defn take-env
  "reads environment variables"
  []
  (into {} (for [[k v] (map #(str/split % #"=" 2)
                            (-> (str (System/getenv))
                                (subs 1)
                                (str/split #", |}")))] [k v]))
  )

(defn take-csv
  "Takes file name and reads csv data"
  [fname]
  (vec (with-open [file (io/reader fname)]
         (csv/read-csv (slurp file)))))

(defn hex-to-num-str
  "converts hex number to normal number string"
  [hex-num]
  (.toString (read-string hex-num))
  )

(defn slurp-file
  "gets edn file"
  [fname]
  (try
    (->
     fname
     slurp
     str
     edn/read-string
     )
    (catch Exception e ;(println e)
      (spit fname [])
      []
      )
    )  
  )

(defn get-tx-result
  "gets result from txs"
  [trans]
                                        ;(pp/pprint trans)
  (->
   trans
   (get :out)
   (str/split #"Result:" 2)
   last
   str/trim
   json/parse-string
   (get "transactions")
   )
  )

(defn make-elem
  "creates vector of vectors"
  [first second third]
  (vector (vector first second) third)
  )

(defn get-elem
  "gets element from vector where first element is equal to second argument"
  [coll arg]
  (first (filter #(= (first %) arg) coll))
  )

(defn contains-elem
  "check whether vectors of vector first element is equal to second argument"
  [coll arg]
  (seq (filter #(= (first %) arg) coll))
  )


(let [{:keys [addr options exit-message ok?]} (validate-args *command-line-args*)]
  (cond
    exit-message (exit (if ok? 0 1) exit-message)
    :else
    (do
      (println (java.util.Date.))
      (println "Multi Signing...")
      (println "addr = " addr)
      (print "opts = ")
      (pp/pprint options)
      (let [env (take-env)
            tonos-cli (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
            file (take-csv (options :file))
            addr-amount (map #(make-elem (first %) (second %) "") file)
            file-tx-name (str (options :file) ".submission")
            file-tx (slurp-file file-tx-name)
            abi (options :abi)
            config (options :config)
            sign (options :sign)
            ]

        (println "parsed csv:")
        (pp/pprint addr-amount)
        (println "parsed submissions:")
        (pp/pprint file-tx)

        (cond
          (options :submit)
          (do
            (println "Submitting transactions...")
            (let
                [trans (->
                                        ;get all transactions
                        (sh tonos-cli
                            "-c" config
                            "run" addr
                            "getTransactions" "{}"
                            "--abi" abi)
                        get-tx-result)
                 trans_values (map #(make-elem (% "dest") (hex-to-num-str (% "value")) (% "id")) trans)
                 ]

              (println "trans_values = ")
              (pp/pprint trans_values)

              (->>
               (map
                #(cond
                   (contains-elem trans_values (first %)) (get-elem trans_values (first %))
                   :else (let
                             [res (->
                                   (sh tonos-cli
                                       "-c" config
                                       "call" addr
                                       "submitTransaction" (str "{\"dest\":\"" (first (first %)) "\",\"value\":" (second (first %)) ",\"bounce\":true,\"allBalance\":false,\"payload\":\"\"}")
                                       "--abi" abi
                                       "--sign" sign)
                                   get-tx-result
                                   )]
                           (make-elem (res "dest") (res "value") (res "id")))
                   
                   )
                addr-amount)
               vec
               (spit file-tx-name) 
               )

              ))
          :else
          (do
            (println "Confirming transactions...")

            (map #(let [comm (list tonos-cli
                                   "-c" config
                                   "call" addr
                                   "confirmTransaction" (str "{\"transactionId\":\"" (second %) "\"}")
                                   "--abi" abi
                                   "--sign" sign)]
                    (println "Transaction")
                    (pp/pprint %)
                    (->
                     (apply sh comm)
                     pp/pprint
                     )
                    )
                 file-tx)
            )
          )          
        )
      )
    )
  )



