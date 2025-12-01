package org.cardanofoundation.cip113;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.cip113.model.blueprint.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_PREVIEW_URL;
import static org.cardanofoundation.cip113.PreviewConstants.BLOCKFROST_KEY;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Abstract base class for integration tests that interact with the Preview network.
 * <p>
 * These tests require:
 * <ul>
 *   <li>WALLET_MNEMONIC environment variable with a funded wallet</li>
 *   <li>BLOCKFROST_KEY environment variable (or uses default test key)</li>
 * </ul>
 * Tests will be skipped if the wallet mnemonic is not configured.
 */
@Slf4j
public abstract class AbstractPreviewTest {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final Network network = Networks.preview();

    /**
     * Check if wallet mnemonic is configured. Tests will be skipped if not available.
     */
    protected static boolean isWalletConfigured() {
        return PreviewConstants.ADMIN_MNEMONIC != null && !PreviewConstants.ADMIN_MNEMONIC.isBlank();
    }

    // Lazy-initialized accounts - only created when wallet is configured
    protected Account adminAccount;
    protected Account refInputAccount;
    protected Account aliceAccount;
    protected Account bobAccount;

    protected final BFBackendService bfBackendService = new BFBackendService(BLOCKFROST_PREVIEW_URL, BLOCKFROST_KEY);

    protected QuickTxBuilder quickTxBuilder;

    @BeforeEach
    void setUpAccounts(TestInfo testInfo) {
        assumeTrue(isWalletConfigured(),
            "Skipping integration test - WALLET_MNEMONIC environment variable not set. " +
            "Set WALLET_MNEMONIC to a funded Preview network wallet to run integration tests.");

        log.info("Running integration test: {}", testInfo.getDisplayName());

        // Initialize accounts only when mnemonic is available
        adminAccount = Account.createFromMnemonic(network, PreviewConstants.ADMIN_MNEMONIC);
        refInputAccount = Account.createFromMnemonic(network, PreviewConstants.ADMIN_MNEMONIC, 10, 0);
        aliceAccount = Account.createFromMnemonic(network, PreviewConstants.ADMIN_MNEMONIC, 1, 0);
        bobAccount = Account.createFromMnemonic(network, PreviewConstants.ADMIN_MNEMONIC, 2, 0);
        quickTxBuilder = new QuickTxBuilder(bfBackendService);
    }

    protected String getCompiledCodeFor(String contractTitle, List<Validator> validators) {
        return validators.stream().filter(validator -> validator.title().equals(contractTitle)).findAny().get().compiledCode();
    }

}
