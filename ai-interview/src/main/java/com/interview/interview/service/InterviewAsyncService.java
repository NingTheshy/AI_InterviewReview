package com.interview.interview.service;

/**
 * 面试异步处理服务接口
 * <p>
 * 负责面试记录的异步 AI 处理流程，包括 FunASR 句子级转写、
 * 问答识别、标准量表比对、服务端计分和整体总结。
 * </p>
 */
public interface InterviewAsyncService {

    /**
     * 异步处理面试记录
     * <p>
     * 处理步骤：
     * 1. FunASR 转写、句子时间戳和说话人标识
     * 2. 问答识别、标准答案生成和逐项比对
     * 3. 服务端计分及结果保存
     * 4. 整体评分和总结
     * </p>
     *
     * @param interviewId 面试记录 ID
     */
    void processInterview(Long interviewId);

    /** 使用任务令牌处理，令牌不匹配时必须停止写回。 */
    void processInterview(Long interviewId, String processingToken);

    /** 保留转写，仅重新执行问答识别与评分。 */
    void reanalyzeInterview(Long interviewId, String processingToken);
}
