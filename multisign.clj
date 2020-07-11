#!/usr/bin/env bb

(ns ton-validation
    (:require [clojure.java.shell :refer [sh]]
      [clojure.string :as str]
      [clojure.java.io :as io]
      [cheshire.core :as json]
      [clojure.tools.cli :refer [parse-opts]]
      [clojure.edn :as edn]
      [clojure.pprint :as pp]
      [clojure-csv.core :as csv]
      ))

(def cli-options
  [["-s" "--submit" "submit only transactions, else will only confirm transactions"]
   ["-h" "--help"]
   ["-c" "--config" "location of config file"
    :default "default"]
   ["-f" "--file PATH" "path to csv file with addresses and amounts"
    :default "addresses.csv"
    ]
   [nil "--abi" "link to abi json file"
    :default "SafeMultisigWallet.abi.json"]
   [nil "--sign" "path to key or seed phrase"
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

(defn take-csv
      "Takes file name and reads csv data"
      [fname]
      (with-open [file (reader fname)]
                 (csv/parse-csv (slurp file))))

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
         (let [env (into {} (for [[k v] (map #(str/split % #"=" 2)
                                             (-> (str (System/getenv))
                                                 (subs 1)
                                                 (str/split #", |}")))] [k v]))
               tonos-cli (str (env "TONOS_CLI_SRC_DIR") "/target/release/tonos-cli")
               file (take-csv (options :file))
               abi (options :abi)
               config (options :config)
               sign (options :sign)
               ]

              (cond
               (not (nil? (options :c)))
               (->>
                (map #(list tonos-cli
                            "call" addr
                            "submitTransaction" (str "{\"dest\":\"" (first %) "\",\"value\"" (second %) ",\"bounce\":true,\"allBalance\":false,\"payload\":\"\"}")
                            "-c" config
                            "--abi" abi
                            "--sign" sign) file)
                (map #(do (pp/pprint %) (apply sh %)))
                )
               :else
               (pp/print "Here will go confirmations")
               )
              )
         )
       )
     )
