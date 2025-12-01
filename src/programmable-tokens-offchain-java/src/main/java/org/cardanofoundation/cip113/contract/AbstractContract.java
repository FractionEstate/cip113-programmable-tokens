package org.cardanofoundation.cip113.contract;


import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Abstract base class for Cardano smart contract wrappers.
 * Provides common functionality for loading Plutus scripts and computing addresses.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbstractContract {

    @Getter
    private byte[] scriptHashBytes;

    @Getter
    private String scriptHash;

    @Getter
    private PlutusScript plutusScript;

    /**
     * Create a contract from compiled Plutus code.
     *
     * @param script  the compiled script code (CBOR hex)
     * @param version the Plutus version (V1, V2, or V3)
     */
    public AbstractContract(String script, PlutusVersion version) {
        try {
            plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(script, version);
            scriptHashBytes = plutusScript.getScriptHash();
            scriptHash = HexUtil.encodeHexString(plutusScript.getScriptHash());
            log.info("INIT - Contract: {}, hash: {}", this.getClass(), scriptHash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the enterprise address for this contract on mainnet.
     *
     * @return the bech32 enterprise address
     */
    public String getAddress() {
        return getAddress(Networks.mainnet());
    }

    /**
     * Get the enterprise address for this contract on the specified network.
     *
     * @param network the Cardano network (mainnet, preview, preprod)
     * @return the bech32 enterprise address
     */
    public String getAddress(Network network) {
        return AddressProvider.getEntAddress(plutusScript, network).getAddress();
    }

}
