export MSIG_ADDR=$(cat "${KEYS_DIR}/${VALIDATOR_NAME}.addr")
export ELECTIONS_WORK_DIR="${KEYS_DIR}/elections"

"${TON_BUILD_DIR}/crypto/fift" -I "${TON_SRC_DIR}/crypto/fift/lib:${TON_SRC_DIR}/crypto/smartcont" -s recover-stake.fif "${ELECTIONS_WORK_DIR}/recover-query.boc"

export recover_query_boc=$(base64 --wrap=0 "${ELECTIONS_WORK_DIR}/recover-query.boc")
export elector_addr=$(cat "${ELECTIONS_WORK_DIR}/elector-addr-base64")

