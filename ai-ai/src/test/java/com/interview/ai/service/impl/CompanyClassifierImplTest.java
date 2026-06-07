package com.interview.ai.service.impl;

import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.CompanyTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyClassifierImpl Test")
class CompanyClassifierImplTest {

    @Mock
    private AiClientFactory aiClientFactory;

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private CompanyClassifierImpl companyClassifier;

    @BeforeEach
    void setUp() throws Exception {
        // Clear static caches to ensure test isolation
        Field cacheField = CompanyClassifierImpl.class.getDeclaredField("AI_CLASSIFICATION_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CompanyTier> cache = (Map<String, CompanyTier>) cacheField.get(null);
        cache.clear();
    }

    @Test
    @DisplayName("classify - known company returns correct tier")
    void classify_knownCompany_returnsCorrectTier() {
        assertEquals(CompanyTier.TIER_1, companyClassifier.classify("阿里巴巴", "互联网", null));
        assertEquals(CompanyTier.TIER_1, companyClassifier.classify("腾讯", "互联网", null));
        assertEquals(CompanyTier.TIER_1, companyClassifier.classify("谷歌", "互联网", null));
    }

    @Test
    @DisplayName("classify - empty company name returns default tier 3")
    void classify_emptyCompanyName_returnsTier3() {
        assertEquals(CompanyTier.TIER_3, companyClassifier.classify("", "互联网", null));
        assertEquals(CompanyTier.TIER_3, companyClassifier.classify(null, "互联网", null));
    }

    @Test
    @DisplayName("classify - unknown company calls AI")
    void classify_unknownCompany_callsAi() throws Exception {
        String aiResponse = "{\"tier\": 2, \"reason\": \"big company\"}";
        when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        when(llmClient.call(anyString(), anyString(), isNull())).thenReturn(aiResponse);

        CompanyTier result = companyClassifier.classify("UnknownCompany", "互联网", "JD text");

        assertEquals(CompanyTier.TIER_2, result);
        verify(llmClient).call(anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("classify - AI failure returns default tier 3")
    void classify_aiFailure_returnsTier3() {
        when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        when(llmClient.call(anyString(), anyString(), isNull()))
                .thenThrow(new RuntimeException("AI call failed"));

        CompanyTier result = companyClassifier.classify("UnknownCompany", "互联网", null);

        assertEquals(CompanyTier.TIER_3, result);
    }

    @Test
    @DisplayName("classify - AI invalid tier returns default tier 3")
    void classify_aiInvalidTier_returnsTier3() throws Exception {
        String aiResponse = "{\"tier\": 99, \"reason\": \"invalid\"}";
        when(aiClientFactory.getDefaultLlmClient()).thenReturn(llmClient);
        when(llmClient.call(anyString(), anyString(), isNull())).thenReturn(aiResponse);

        CompanyTier result = companyClassifier.classify("UnknownCompany", "互联网", null);

        assertEquals(CompanyTier.TIER_3, result);
    }
}
