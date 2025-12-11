package org.cardanofoundation.cip113.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.cip113.config.AppConfig;
import org.cardanofoundation.cip113.model.blueprint.Plutus;
import org.cardanofoundation.cip113.model.blueprint.Validator;
import org.cardanofoundation.cip113.model.bootstrap.ProtocolBootstrapParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading and managing CIP-0113 protocol bootstrap configuration.
 *
 * <p>This service is responsible for loading two critical configuration files at startup:</p>
 * <ul>
 *   <li><strong>protocolBootstrap.json</strong>: Contains bootstrap transaction UTxO references,
 *       policy IDs, and other protocol initialization parameters.</li>
 *   <li><strong>plutus.json</strong>: The Aiken-generated blueprint containing compiled Plutus
 *       validator scripts and their metadata.</li>
 * </ul>
 *
 * <h2>Initialization</h2>
 * <p>Both files are loaded from the classpath during application startup via the {@code @PostConstruct}
 * lifecycle hook. If either file is missing or malformed, the service throws a RuntimeException,
 * preventing the application from starting in an invalid state.</p>
 *
 * <h2>Protocol Bootstrap Parameters</h2>
 * <p>The bootstrap parameters define:</p>
 * <ul>
 *   <li>Reference UTxO transaction IDs and output indices</li>
 *   <li>Protocol policy IDs for various validator scripts</li>
 *   <li>Initial stake key credentials</li>
 * </ul>
 *
 * <h2>Plutus Blueprint</h2>
 * <p>The Plutus blueprint provides:</p>
 * <ul>
 *   <li>Compiled validator bytecode (CBOR-encoded)</li>
 *   <li>Validator titles for lookup operations</li>
 *   <li>Script hashes and policy IDs</li>
 *   <li>Parameter specifications for script instantiation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Autowired
 * private ProtocolBootstrapService bootstrapService;
 *
 * public void buildTransaction() {
 *     // Get bootstrap UTxO references
 *     var params = bootstrapService.getProtocolBootstrapParams();
 *     var refTxId = params.getReferenceTxId();
 *
 *     // Get compiled validator code
 *     Optional<String> code = bootstrapService.getProtocolContract("registry_mint.mint");
 *     code.ifPresent(compiledCode -> {
 *         // Use compiled code for transaction building
 *     });
 * }
 * }</pre>
 *
 * @see Plutus
 * @see ProtocolBootstrapParams
 * @see Validator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProtocolBootstrapService {

    /** Jackson ObjectMapper for JSON deserialization */
    private final ObjectMapper objectMapper;

    private final AppConfig.Network network;

    @Value("${programmable.token.default.txHash:}")
    private String defaultTxHash;

    @Getter
    private Plutus plutus;

    /**
     * Protocol bootstrap parameters loaded from protocolBootstrap.json.
     * <p>
     * Contains the UTxO references and policy IDs needed to interact with
     * the deployed protocol instance on-chain.
     */
    @Getter
    private ProtocolBootstrapParams protocolBootstrapParams;

    // Map of txHash -> ProtocolBootstrapParams for all available versions
    private final Map<String, ProtocolBootstrapParams> bootstrapsByTxHash = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("defaultTxHash: {}", defaultTxHash);
        log.info("network: {}", network.getNetwork());

        try {

            var protocolBootstrapFilename = String.format("protocol-bootstraps-%s.json", network.getNetwork());
            log.info("protocolBootstrapFilename: {}", protocolBootstrapFilename);

            // Load array of protocol bootstrap configurations
            var bootstrapsList = objectMapper.readValue(
                    this.getClass().getClassLoader().getResourceAsStream(protocolBootstrapFilename),
                    new TypeReference<List<ProtocolBootstrapParams>>() {}
            );

            // Store all bootstraps in map
            for (ProtocolBootstrapParams params : bootstrapsList) {
                bootstrapsByTxHash.put(params.txHash(), params);
                log.info("Loaded protocol bootstrap for txHash: {}", params.txHash());
            }

            // Set default protocol bootstrap params
            if (defaultTxHash != null && !defaultTxHash.isEmpty()) {
                protocolBootstrapParams = bootstrapsByTxHash.get(defaultTxHash);
                if (protocolBootstrapParams == null) {
                    log.warn("Default txHash {} not found in bootstraps, using first available", defaultTxHash);
                    protocolBootstrapParams = bootstrapsList.getFirst();
                } else {
                    log.info("Using default protocol bootstrap with txHash: {}", defaultTxHash);
                }
            } else {
                // No default specified, use first one
                protocolBootstrapParams = bootstrapsList.getFirst();
                log.info("No default txHash configured, using first bootstrap: {}", protocolBootstrapParams.txHash());
            }

            // Load plutus contracts
            plutus = objectMapper.readValue(
                    this.getClass().getClassLoader().getResourceAsStream("plutus.json"),
                    Plutus.class
            );

            log.info("Successfully initialized ProtocolBootstrapService with {} bootstrap versions", bootstrapsByTxHash.size());
        } catch (IOException e) {
            log.error("could not load bootstrap or protocol blueprint", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Get protocol bootstrap params by transaction hash
     *
     * @param txHash the transaction hash
     * @return the protocol bootstrap params or empty if not found
     */
    public Optional<ProtocolBootstrapParams> getProtocolBootstrapParamsByTxHash(String txHash) {
        return Optional.ofNullable(bootstrapsByTxHash.get(txHash));
    }

    /**
     * Get all available protocol bootstrap configurations
     *
     * @return map of txHash to ProtocolBootstrapParams
     */
    public Map<String, ProtocolBootstrapParams> getAllBootstraps() {
        return Map.copyOf(bootstrapsByTxHash);
    }

    public Optional<String> getProtocolContract(String contractTitle) {
        return plutus.validators().stream()
                .filter(validator -> validator.title().equals(contractTitle))
                .findAny()
                .map(Validator::compiledCode);
    }

}
