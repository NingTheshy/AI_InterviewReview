package com.interview.ai.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionDifficultyPolicyTest {
    @Test
    void mapsEntireMatrixAndRejectsInvalidInputs() {
        int[][] expected = {{1, 1, 2, 2, 3}, {1, 2, 2, 3, 3}, {2, 2, 3, 4, 4},
                {2, 3, 4, 4, 5}, {3, 3, 4, 5, 5}};
        for (int k = 1; k <= 5; k++) {
            for (int c = 1; c <= 5; c++) {
                assertEquals("L" + expected[k - 1][c - 1], QuestionDifficultyPolicy.level(k, c));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> QuestionDifficultyPolicy.level(0, 3));
        assertThrows(IllegalArgumentException.class, () -> QuestionDifficultyPolicy.level(3, 6));
    }
}
