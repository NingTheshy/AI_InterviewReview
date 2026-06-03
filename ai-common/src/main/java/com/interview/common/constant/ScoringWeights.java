package com.interview.common.constant;

/**
 * 评分维度权重常量
 * <p>
 * 用于计算加权 overallScore，各维度权重之和 = 1.0
 * </p>
 */
public final class ScoringWeights {

    private ScoringWeights() {
    }

    /** 内容专业度：技术深度、准确性、完整性 */
    public static final double CONTENT = 0.25;

    /** 逻辑思维：结构化、因果链、边界意识 */
    public static final double LOGIC = 0.20;

    /** 表达能力：清晰度、简洁性、举例能力 */
    public static final double EXPRESSION = 0.15;

    /** 专业知识：技术栈深度、行业认知、工程实践 */
    public static final double PROFESSIONAL = 0.25;

    /** 沟通协作：倾听理解、互动能力、团队意识 */
    public static final double COMMUNICATION = 0.15;

    /**
     * 计算加权总分
     */
    public static int calculateOverallScore(
            Integer dimensionContent,
            Integer dimensionLogic,
            Integer dimensionExpression,
            Integer dimensionProfessional,
            Integer dimensionCommunication) {

        int content = dimensionContent != null ? dimensionContent : 0;
        int logic = dimensionLogic != null ? dimensionLogic : 0;
        int expression = dimensionExpression != null ? dimensionExpression : 0;
        int professional = dimensionProfessional != null ? dimensionProfessional : 0;
        int communication = dimensionCommunication != null ? dimensionCommunication : 0;

        return (int) Math.round(
                content * CONTENT +
                logic * LOGIC +
                expression * EXPRESSION +
                professional * PROFESSIONAL +
                communication * COMMUNICATION
        );
    }
}
