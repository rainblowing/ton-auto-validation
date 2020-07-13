#!/usr/bin/env bb

(as-> (first *command-line-args*) $
  (println (new java.util.Date (* 1000 (Long/parseLong $)))))
