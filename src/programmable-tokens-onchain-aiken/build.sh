AIKEN=$1

echo "${AIKEN}"

aiken() {
  ${AIKEN} $*
}


## PREVIEW ##

aiken build -t verbose &> /dev/null
# aiken build &> /dev/null

DIRECTORY_MINT_BOOT_UTXO="D8799F58202AD9A8A937482BD7243AA5B806335ADD3FC4731CED9A6CB12D2509667AB1160E00FF"

PREVIEW_PROTOCOL_BOOT_UTXO="D8799F58202AD9A8A937482BD7243AA5B806335ADD3FC4731CED9A6CB12D2509667AB1160E00FF"

## Directory Mint
aiken blueprint apply -m directory_mint -v directory_mint $DIRECTORY_MINT_BOOT_UTXO -o plutus-tmp.json
mv plutus-tmp.json plutus.json

## Protocol Params Mint
aiken blueprint apply -m protocol_params_mint -v protocol_params_mint $PREVIEW_PROTOCOL_BOOT_UTXO -o plutus-tmp.json
mv plutus-tmp.json plutus.json

### Saving preprod plutus.json
mv plutus.json preview-plutus.json
