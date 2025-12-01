package org.cardanofoundation.cip113;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.cip113.model.blueprint.Plutus;
import org.cardanofoundation.cip113.model.bootstrap.ProtocolBootstrapParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Integration test for transferring programmable tokens.
 * <p>
 * This test dynamically discovers programmable tokens at alice's script address
 * and transfers half to bob's script address.
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Protocol deployed (ProtocolDeploymentMintTest)</li>
 *   <li>Token issued to alice's programmable address (IssueTokenTest)</li>
 *   <li>Funded sub-accounts - alice and bob (FundSubAccountsTest)</li>
 *   <li>Programmable addresses funded (SetupProgrammableAddressesTest)</li>
 * </ul>
 * <p>
 * To run:
 * <pre>
 * ./gradlew manualIntegrationTest --tests "org.cardanofoundation.cip113.TransferTokenTest"
 * </pre>
 */
@Slf4j
@Tag("manual-integration")
public class TransferTokenTest extends AbstractPreviewTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SUBSTANDARD_ISSUE_CONTRACT = "585701010029800aba2aba1aab9eaab9dab9a4888896600264646644b30013370e900218031baa00289919b87375a6012008906400980418039baa0028a504014600c600e002600c004600c00260066ea801a29344d9590011";

    private static final String SUBSTANDARD_TRANSFER_CONTRACT = "585701010029800aba2aba1aab9eaab9dab9a4888896600264646644b30013370e900218031baa00289919b87375a6012008904801980418039baa0028a504014600c600e002600c004600c00260066ea801a29344d9590011";

    private String DIRECTORY_SPEND_CONTRACT, PROGRAMMABLE_LOGIC_BASE_CONTRACT, PROGRAMMABLE_LOGIC_GLOBAL_CONTRACT;

    private ProtocolBootstrapParams protocolBootstrapParams;

    @BeforeEach
    public void loadContracts() throws Exception {
        protocolBootstrapParams = OBJECT_MAPPER.readValue(this.getClass().getClassLoader().getResourceAsStream("protocolBootstrap.json"), ProtocolBootstrapParams.class);
        var plutus = OBJECT_MAPPER.readValue(this.getClass().getClassLoader().getResourceAsStream("plutus.json"), Plutus.class);
        var validators = plutus.validators();
        DIRECTORY_SPEND_CONTRACT = getCompiledCodeFor("registry_spend.registry_spend.spend", validators);
        PROGRAMMABLE_LOGIC_BASE_CONTRACT = getCompiledCodeFor("programmable_logic_base.programmable_logic_base.spend", validators);
        PROGRAMMABLE_LOGIC_GLOBAL_CONTRACT = getCompiledCodeFor("programmable_logic_global.programmable_logic_global.withdraw", validators);
    }

    /**
     * Data class to hold discovered token information.
     */
    private record TokenInfo(String policyId, String assetNameHex, String assetName, BigInteger quantity, String unit) {}

    /**
     * Discovers the first programmable token at the given address.
     */
    private Optional<TokenInfo> discoverFirstToken(List<Utxo> utxos) {
        for (var utxo : utxos) {
            for (var amount : utxo.getAmount()) {
                if (!"lovelace".equals(amount.getUnit()) && amount.getUnit().length() >= 56) {
                    String policyId = amount.getUnit().substring(0, 56);
                    String assetNameHex = amount.getUnit().substring(56);
                    String assetName = hexToString(assetNameHex);
                    return Optional.of(new TokenInfo(policyId, assetNameHex, assetName, amount.getQuantity(), amount.getUnit()));
                }
            }
        }
        return Optional.empty();
    }

    private String hexToString(String hex) {
        if (hex == null || hex.isEmpty()) return "";
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 2) {
                sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return hex;
        }
    }

    @Test
    public void test() throws Exception {

        var dryRun = false;

        // Protocol Params and Directory UTxOs
        var protocolParamsUtxoOpt = bfBackendService.getUtxoService().getTxOutput(
                protocolBootstrapParams.protocolParamsUtxo().txHash(),
                protocolBootstrapParams.protocolParamsUtxo().outputIndex());
        if (!protocolParamsUtxoOpt.isSuccessful()) {
            Assertions.fail("Could not fetch protocol params UTxO. Has the protocol been deployed?");
        }
        var protocolParamsUtxo = protocolParamsUtxoOpt.getValue();
        log.info("protocolParamsUtxo: {}", protocolParamsUtxo);

        // Build addresses
        var programmableBaseScriptHash = protocolBootstrapParams.programmableLogicBaseParams().scriptHash();

        var aliceAddress = AddressProvider.getBaseAddress(
                Credential.fromScript(programmableBaseScriptHash),
                aliceAccount.getBaseAddress().getDelegationCredential().get(),
                network);
        log.info("aliceAddress (programmable): {}", aliceAddress);

        var bobAddress = AddressProvider.getBaseAddress(
                Credential.fromScript(programmableBaseScriptHash),
                bobAccount.getBaseAddress().getDelegationCredential().get(),
                network);
        log.info("bobAddress (programmable): {}", bobAddress);

        // Get alice's wallet UTxOs for fee payment
        var walletUtxosOpt = bfBackendService.getUtxoService().getUtxos(aliceAccount.baseAddress(), 100, 1);
        Assertions.assertTrue(walletUtxosOpt.isSuccessful() && !walletUtxosOpt.getValue().isEmpty(),
                "Alice's wallet has no UTxOs. Run FundSubAccountsTest first. Address: " + aliceAccount.baseAddress());
        var walletUtxos = walletUtxosOpt.getValue();

        // Discover tokens at alice's programmable address
        var progBaseAddressUtxosOpt = bfBackendService.getUtxoService().getUtxos(aliceAddress.getAddress(), 100, 1);
        Assertions.assertTrue(progBaseAddressUtxosOpt.isSuccessful() && !progBaseAddressUtxosOpt.getValue().isEmpty(),
                "No UTxOs at alice's programmable address: " + aliceAddress.getAddress());
        var progBaseAddressUtxos = progBaseAddressUtxosOpt.getValue();

        // Find a programmable token
        var tokenInfoOpt = discoverFirstToken(progBaseAddressUtxos);
        Assertions.assertTrue(tokenInfoOpt.isPresent(),
                "No programmable tokens found at alice's address. Run IssueTokenTest first.");
        var tokenInfo = tokenInfoOpt.get();
        log.info("Discovered token: {} ({}) with quantity {}", tokenInfo.unit(), tokenInfo.assetName(), tokenInfo.quantity());

        // Find the UTxO containing the token
        var progTokenUtxoOpt = progBaseAddressUtxos.stream()
                .filter(utxo -> utxo.getAmount().stream().anyMatch(a -> tokenInfo.unit().equals(a.getUnit())))
                .findAny();
        Assertions.assertTrue(progTokenUtxoOpt.isPresent(), "Could not find UTxO with token: " + tokenInfo.unit());
        var progTokenUtxo = progTokenUtxoOpt.get();
        log.info("Token UTxO: {}#{}", progTokenUtxo.getTxHash(), progTokenUtxo.getOutputIndex());

        // Build the directory NFT unit from the discovered token
        var directoryNftUnit = protocolBootstrapParams.directoryMintParams().scriptHash() + tokenInfo.policyId();
        log.info("Directory NFT unit: {}", directoryNftUnit);

        var substandardIssueContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(SUBSTANDARD_ISSUE_CONTRACT, PlutusVersion.v3);
        log.info("substandardIssueContract: {}", substandardIssueContract.getPolicyId());
        var substandardIssueAddress = AddressProvider.getRewardAddress(substandardIssueContract, network);
        log.info("substandardIssueAddress: {}", substandardIssueAddress.getAddress());

        var substandardTransferContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(SUBSTANDARD_TRANSFER_CONTRACT, PlutusVersion.v3);
        log.info("substandardTransferContract: {}", substandardTransferContract.getPolicyId());
        var substandardTransferAddress = AddressProvider.getRewardAddress(substandardTransferContract, network);
        log.info("substandardTransferAddress: {}", substandardTransferAddress.getAddress());

        // Programmable Logic Global parameterization
        var programmableLogicGlobalParameters = ListPlutusData.of(BytesPlutusData.of(HexUtil.decodeHexString(protocolBootstrapParams.protocolParams().scriptHash())));
        var programmableLogicGlobalContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(AikenScriptUtil.applyParamToScript(programmableLogicGlobalParameters, PROGRAMMABLE_LOGIC_GLOBAL_CONTRACT), PlutusVersion.v3);
        log.info("programmableLogicGlobalContract policy: {}", programmableLogicGlobalContract.getPolicyId());
        var programmableLogicGlobalAddress = AddressProvider.getRewardAddress(programmableLogicGlobalContract, network);
        log.info("programmableLogicGlobalAddress policy: {}", programmableLogicGlobalAddress.getAddress());

        // Programmable Logic Base parameterization
        var programmableLogicBaseParameters = ListPlutusData.of(ConstrPlutusData.of(1, BytesPlutusData.of(programmableLogicGlobalContract.getScriptHash())));
        var programmableLogicBaseContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(AikenScriptUtil.applyParamToScript(programmableLogicBaseParameters, PROGRAMMABLE_LOGIC_BASE_CONTRACT), PlutusVersion.v3);
        log.info("programmableLogicBaseContract policy: {}", programmableLogicBaseContract.getPolicyId());

        // Directory SPEND parameterization
        var directorySpendParameters = ListPlutusData.of(
                BytesPlutusData.of(HexUtil.decodeHexString(protocolBootstrapParams.protocolParams().scriptHash()))
        );
        var directorySpendContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(AikenScriptUtil.applyParamToScript(directorySpendParameters, DIRECTORY_SPEND_CONTRACT), PlutusVersion.v3);
        var directorySpendContractAddress = AddressProvider.getEntAddress(Credential.fromScript(directorySpendContract.getScriptHash()), network);

        var directoryUtxosOpt = bfBackendService.getUtxoService().getUtxos(directorySpendContractAddress.getAddress(), 100, 1);
        Assertions.assertTrue(directoryUtxosOpt.isSuccessful() && !directoryUtxosOpt.getValue().isEmpty(),
                "No directory UTxOs found. Has the token been registered?");
        var directoryUtxos = directoryUtxosOpt.getValue();
        directoryUtxos.forEach(utxo -> log.info("directory utxo: {}", utxo));

        var directoryUtxoOpt = directoryUtxos.stream()
                .filter(utxo -> utxo.getAmount().stream().anyMatch(amount -> directoryNftUnit.equals(amount.getUnit())))
                .findAny();
        Assertions.assertTrue(directoryUtxoOpt.isPresent(),
                "No directory UTxO for token. Expected NFT: " + directoryNftUnit);
        var directoryUtxo = directoryUtxoOpt.get();
        log.info("directoryUtxo: {}", directoryUtxo);

        // Calculate transfer amounts - split token in half
        var tokenAmount = tokenInfo.quantity();
        var amount1 = tokenAmount.divide(BigInteger.TWO);
        var amount2 = tokenAmount.subtract(amount1);
        log.info("Transferring {} to alice's script addr, {} to bob's script addr", amount1, amount2);

        // Build token assets
        var tokenAsset1 = Asset.builder()
                .name("0x" + tokenInfo.assetNameHex())
                .value(amount1)
                .build();

        var tokenAsset2 = Asset.builder()
                .name("0x" + tokenInfo.assetNameHex())
                .value(amount2)
                .build();

        Value tokenValue1 = Value.builder()
                .coin(Amount.ada(1).getQuantity())
                .multiAssets(List.of(
                        MultiAsset.builder()
                                .policyId(tokenInfo.policyId())
                                .assets(List.of(tokenAsset1))
                                .build()
                ))
                .build();

        Value tokenValue2 = Value.builder()
                .coin(Amount.ada(1).getQuantity())
                .multiAssets(List.of(
                        MultiAsset.builder()
                                .policyId(tokenInfo.policyId())
                                .assets(List.of(tokenAsset2))
                                .build()
                ))
                .build();

        // Build programmable global redeemer: TransferAct { proofs: [TokenProof::ByOwner(idx=1)] }
        var programmableGlobalRedeemer = ConstrPlutusData.of(0,
                ListPlutusData.of(ConstrPlutusData.of(0, BigIntPlutusData.of(1)))
        );

        log.info("programmableGlobalRefInput: {}", protocolBootstrapParams.programmableGlobalRefInput());

        var tx = new ScriptTx()
                .collectFrom(walletUtxos)
                .collectFrom(progTokenUtxo, ConstrPlutusData.of(0))
                // Substandard transfer withdrawal (must be first)
                .withdraw(substandardTransferAddress.getAddress(), BigInteger.ZERO, BigIntPlutusData.of(200))
                // Programmable logic global withdrawal
                .withdraw(programmableLogicGlobalAddress.getAddress(), BigInteger.ZERO, programmableGlobalRedeemer)
                // Output tokens to alice and bob
                .payToContract(aliceAddress.getAddress(), ValueUtil.toAmountList(tokenValue1), ConstrPlutusData.of(0))
                .payToContract(bobAddress.getAddress(), ValueUtil.toAmountList(tokenValue2), ConstrPlutusData.of(0))
                // Reference inputs
                .readFrom(
                        TransactionInput.builder()
                                .transactionId(protocolParamsUtxo.getTxHash())
                                .index(protocolParamsUtxo.getOutputIndex())
                                .build(),
                        TransactionInput.builder()
                                .transactionId(directoryUtxo.getTxHash())
                                .index(directoryUtxo.getOutputIndex())
                                .build()
                )
                // Attach validators
                .attachRewardValidator(programmableLogicGlobalContract)
                .attachRewardValidator(substandardTransferContract)
                .attachSpendingValidator(programmableLogicBaseContract)
                .withChangeAddress(aliceAccount.baseAddress());

        var transaction = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(aliceAccount))
                .withSigner(SignerProviders.stakeKeySignerFrom(aliceAccount))
                .withTxEvaluator(new AikenTransactionEvaluator(bfBackendService))
                .withRequiredSigners(aliceAccount.getBaseAddress().getDelegationCredentialHash().get())
                .feePayer(aliceAccount.baseAddress())
                .mergeOutputs(false)
                .buildAndSign();

        log.info("tx hex: {}", transaction.serializeToHex());

        if (!dryRun) {
            var result = bfBackendService.getTransactionService().submitTransaction(transaction.serialize());
            if (result.isSuccessful()) {
                log.info("Transfer submitted successfully! TxHash: {}", result.getValue());
            } else {
                log.error("Transfer failed: {}", result.getResponse());
                Assertions.fail("Transaction submission failed: " + result.getResponse());
            }
        } else {
            log.info("Dry run - transaction not submitted");
        }
    }
}
