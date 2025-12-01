package org.cardanofoundation.cip113.controller;

import org.cardanofoundation.cip113.model.blueprint.Plutus;
import org.cardanofoundation.cip113.model.blueprint.Validator;
import org.cardanofoundation.cip113.model.bootstrap.ProtocolBootstrapParams;
import org.cardanofoundation.cip113.model.bootstrap.TxInput;
import org.cardanofoundation.cip113.service.ProtocolBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ProtocolController endpoints.
 * Uses standalone MockMvc setup for fast, isolated unit testing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Protocol Controller Tests")
class ProtocolControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProtocolBootstrapService protocolBootstrapService;

    @InjectMocks
    private ProtocolController protocolController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(protocolController)
                .addPlaceholderValue("apiPrefix", "/api/v1")
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/protocol/blueprint")
    class GetBlueprint {

        @Test
        @DisplayName("should return Plutus blueprint with validators")
        void shouldReturnPlutusBlueprint() throws Exception {
            // Given
            Plutus blueprint = createTestPlutus();
            when(protocolBootstrapService.getPlutus()).thenReturn(blueprint);

            // When/Then
            mockMvc.perform(get("/api/v1/protocol/blueprint")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.validators").isArray())
                    .andExpect(jsonPath("$.validators[0].title").value("programmable_logic_base"))
                    .andExpect(jsonPath("$.validators[0].compiledCode").value("compiled_code_1"));
        }

        @Test
        @DisplayName("should return empty validators when none configured")
        void shouldReturnEmptyValidatorsWhenNoneConfigured() throws Exception {
            // Given
            Plutus emptyBlueprint = new Plutus(List.of());
            when(protocolBootstrapService.getPlutus()).thenReturn(emptyBlueprint);

            // When/Then
            mockMvc.perform(get("/api/v1/protocol/blueprint")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.validators").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/protocol/bootstrap")
    class GetBootstrap {

        @Test
        @DisplayName("should return protocol bootstrap params")
        void shouldReturnProtocolBootstrapParams() throws Exception {
            // Given
            ProtocolBootstrapParams params = createTestBootstrapParams();
            when(protocolBootstrapService.getProtocolBootstrapParams()).thenReturn(params);

            // When/Then
            mockMvc.perform(get("/api/v1/protocol/bootstrap")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.protocolParamsUtxo.txHash").value("txhash1"))
                    .andExpect(jsonPath("$.protocolParamsUtxo.outputIndex").value(0))
                    .andExpect(jsonPath("$.issuanceUtxo.txHash").value("txhash2"))
                    .andExpect(jsonPath("$.directoryUtxo.txHash").value("txhash3"));
        }

        @Test
        @DisplayName("should handle null UTxO references")
        void shouldHandleNullUtxoReferences() throws Exception {
            // Given
            ProtocolBootstrapParams params = new ProtocolBootstrapParams(
                    null, null, null, null, null, null, null, null, null, null, null, null
            );
            when(protocolBootstrapService.getProtocolBootstrapParams()).thenReturn(params);

            // When/Then
            mockMvc.perform(get("/api/v1/protocol/bootstrap")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.protocolParamsUtxo").doesNotExist())
                    .andExpect(jsonPath("$.issuanceUtxo").doesNotExist());
        }
    }

    private Plutus createTestPlutus() {
        return new Plutus(
                List.of(
                        new Validator("programmable_logic_base", "compiled_code_1"),
                        new Validator("issuance_mint", "compiled_code_2")
                )
        );
    }

    private ProtocolBootstrapParams createTestBootstrapParams() {
        return new ProtocolBootstrapParams(
                null, // protocolParams
                null, // programmableLogicGlobalPrams
                null, // programmableLogicBaseParams
                null, // issuanceParams
                null, // directoryMintParams
                null, // directorySpendParams
                null, // programmableBaseRefInput
                null, // programmableGlobalRefInput
                new TxInput("txhash1", 0), // protocolParamsUtxo
                new TxInput("txhash3", 2), // directoryUtxo
                new TxInput("txhash2", 1), // issuanceUtxo
                "bootstrap_txhash" // txHash
        );
    }
}
