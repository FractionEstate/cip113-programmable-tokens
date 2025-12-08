package org.cardanofoundation.cip113;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class AddressTest {


    @Test
    public void genMnemonics() {

        var account = new Account();
        log.info(account.mnemonic());


    }

    @Test
    public void serde() throws CborDeserializationException {

        var version = "1.2.0";

        var plutusBytes = BytesPlutusData.of(HexUtil.encodeHexString(version.getBytes()));

        var bytesPlutusData = (BytesPlutusData)PlutusData.deserialize(plutusBytes.serializeToBytes());

        var actualVersion = bytesPlutusData.getValue();

        log.info(new String(HexUtil.decodeHexString(new String(actualVersion))));


    }

    @Test
    public void serde2() throws CborDeserializationException {

        var version = "1.2.0";

        var plutusBytes = BytesPlutusData.of(HexUtil.encodeHexString(version.getBytes()));

        var inlineDatum = plutusBytes.serializeToBytes();

        var bytesPlutusData = (BytesPlutusData)PlutusData.deserialize(inlineDatum);

        var actualVersion = bytesPlutusData.getValue();

        log.info(new String(HexUtil.decodeHexString(new String(actualVersion))));


    }



}
