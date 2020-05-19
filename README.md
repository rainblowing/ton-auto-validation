# ton-auto-validation

### Description
Automation scripts for TON validation

### Prerequisites
1. Scripts require babashka to run them, for installation see https://github.com/borkdude/babashka
2. Scripts quit once they do their job either successfully or not, so they are intended to run every period of time, 5 or 10 minutes. So if continuity is needed they are better to be launched through watch or cron jobs.

### Setup
1. autosign script is looking for unsigned transactions on the network for given address and signs them with provided paths to keys
2. stake script waits for elections to start, returns the previous stakes and signs a transaction for a new stake
3. stake size is specified inside json, it's done so to let the user redefine next stake size without restarting scripts with new parameters


