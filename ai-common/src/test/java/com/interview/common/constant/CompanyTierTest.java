package com.interview.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompanyTier 枚举测试")
class CompanyTierTest {

    @Test
    @DisplayName("fromCode - 有效值返回对应枚举")
    void fromCode_validCode_returnsCorrectTier() {
        assertEquals(CompanyTier.TIER_1, CompanyTier.fromCode(1));
        assertEquals(CompanyTier.TIER_2, CompanyTier.fromCode(2));
        assertEquals(CompanyTier.TIER_3, CompanyTier.fromCode(3));
        assertEquals(CompanyTier.TIER_4, CompanyTier.fromCode(4));
        assertEquals(CompanyTier.TIER_5, CompanyTier.fromCode(5));
    }

    @Test
    @DisplayName("fromCode - 无效值返回默认 TIER_3")
    void fromCode_invalidCode_returnsDefaultTier3() {
        assertEquals(CompanyTier.TIER_3, CompanyTier.fromCode(0));
        assertEquals(CompanyTier.TIER_3, CompanyTier.fromCode(99));
        assertEquals(CompanyTier.TIER_3, CompanyTier.fromCode(-1));
    }

    @Test
    @DisplayName("枚举属性验证")
    void enumProperties_areCorrect() {
        assertEquals(1, CompanyTier.TIER_1.getCode());
        assertEquals("超大厂", CompanyTier.TIER_1.getName());
        assertNotNull(CompanyTier.TIER_1.getDescription());
    }
}
