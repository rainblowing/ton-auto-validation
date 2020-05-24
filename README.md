# ton-auto-validation

### Description
Automation scripts for TON validation

### Prerequisites
* Scripts require babashka to run them, for installation see https://github.com/borkdude/babashka
* Scripts require installed and fully synced node, for more information see https://github.com/tonlabs/main.ton.dev
* Example of crontab is provided, for it to work properly TONAUTO variable should be exported pointing to these scripts dir
* Scripts quit once they do their job either successfully or not, so they are intended to run every period of time, 5 or 10 minutes or so. So if continuity is needed they are better to be launched through watch or cron jobs.

### Setup
* autosign script is looking for unsigned transactions on the network for given address and signs them with provided paths to keys
* autostake script waits for elections to starts and sends a transaction for a new stake. Stake size is specified inside stake.json, it's done so to let the user redefine next stake size without restarting scripts with new parameters.
* autoreturnstake script waits as well for elections to start and returns the previous stakes.


