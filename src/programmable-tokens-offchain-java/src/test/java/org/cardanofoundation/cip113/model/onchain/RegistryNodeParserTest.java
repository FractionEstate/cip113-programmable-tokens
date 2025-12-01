package org.cardanofoundation.cip113.model.onchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegistryNodeParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RegistryNodeParser registryNodeParser = new RegistryNodeParser(OBJECT_MAPPER);

    @Test
    public void testParseRegistryNode() {
        // Valid CBOR for RegistryNode with:
        // - key: 0befd1269cf3b5b41cce136c92c64b45dde93e4bfe11875839b713d1
        // - next: ffffffffffffffffffffffffffffffffffffffffffffffffffffff
        // - transferLogicScript: Script(aaa513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126102)
        // - thirdPartyLogicScript: Script(def513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126103)
        // - globalStatePolicyId: 1234567890abcdef1234567890abcdef1234567890abcdef12345678
        var inlineDatum = "d8799f581c0befd1269cf3b5b41cce136c92c64b45dde93e4bfe11875839b713d1581bffffffffffffffffffffffffffffffffffffffffffffffffffffffd87a9f581caaa513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126102ffd87a9f581cdef513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126103ff581c1234567890abcdef1234567890abcdef1234567890abcdef12345678ff";

        var registryNodeOpt = registryNodeParser.parse(inlineDatum);

        if (registryNodeOpt.isEmpty()) {
            Assertions.fail("Failed to parse registry node");
        }

        var registryNode = registryNodeOpt.get();

        // Verify parsed values
        Assertions.assertEquals("0befd1269cf3b5b41cce136c92c64b45dde93e4bfe11875839b713d1", registryNode.key());
        Assertions.assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffff", registryNode.next());
        Assertions.assertEquals("aaa513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126102", registryNode.transferLogicScript());
        Assertions.assertEquals("def513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126103", registryNode.thirdPartyTransferLogicScript());
        Assertions.assertEquals("1234567890abcdef1234567890abcdef1234567890abcdef12345678", registryNode.globalStatePolicyId());
    }

    @Test
    public void testParseSentinelNode() {
        // Sentinel node has empty key and empty global state
        // CBOR structure: constr 0 [ empty_bytes, 27-byte-next, Script(28-byte-hash), Script(28-byte-hash), empty_bytes ]
        var inlineDatum = "d8799f40581bffffffffffffffffffffffffffffffffffffffffffffffffffffffd87a9f581caaa513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126102ffd87a9f581cdef513b0fcc01d635f8535d49f38acc33d4d6b62ee8732ca6e126103ff40ff";

        var registryNodeOpt = registryNodeParser.parse(inlineDatum);

        if (registryNodeOpt.isEmpty()) {
            Assertions.fail("Failed to parse sentinel node");
        }

        var registryNode = registryNodeOpt.get();

        // Verify sentinel has empty key
        Assertions.assertEquals("", registryNode.key());
        Assertions.assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffff", registryNode.next());
        // Verify global state is empty
        Assertions.assertEquals("", registryNode.globalStatePolicyId());
    }

    @Test
    public void testParseInvalidDatum() {
        var invalidDatum = "invalid_hex_data";

        var registryNodeOpt = registryNodeParser.parse(invalidDatum);

        // Should return empty on parse failure
        Assertions.assertTrue(registryNodeOpt.isEmpty());
    }
}
