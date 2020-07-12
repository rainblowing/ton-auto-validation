#!/usr/bin/env bb

(as-> (first *command-line-args*) $
(.toString (read-string $)))
