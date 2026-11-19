package com.interview.ai.service.impl;

/** 统一题目难度规则；难度只由题目要求的知识深度和解题复杂度决定。 */
public final class QuestionDifficultyPolicy {

    public static final String VERSION = "difficulty-v1";
    private static final int[][] LEVELS = {
            {1, 1, 2, 2, 3},
            {1, 2, 2, 3, 3},
            {2, 2, 3, 4, 4},
            {2, 3, 4, 4, 5},
            {3, 3, 4, 5, 5}
    };

    private QuestionDifficultyPolicy() {
    }

    public static String level(int knowledgeDepth, int problemComplexity) {
        if (knowledgeDepth < 1 || knowledgeDepth > 5 || problemComplexity < 1 || problemComplexity > 5) {
            throw new IllegalArgumentException("知识深度和解题复杂度必须在 1 至 5 之间");
        }
        return "L" + LEVELS[knowledgeDepth - 1][problemComplexity - 1];
    }
}
