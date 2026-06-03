package com.interview.interview.controller;

import com.interview.common.result.Result;
import com.interview.common.utils.SecurityUtils;
import com.interview.interview.dto.NoteRequest;
import com.interview.interview.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 笔记控制器
 * <p>
 * 提供面试笔记和问题笔记的 CRUD 接口。
 * </p>
 */
@Tag(name = "用户笔记模块")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * 获取面试笔记
     *
     * @param id 面试 ID
     * @return 笔记内容
     */
    @Operation(summary = "获取面试笔记")
    @GetMapping("/{id}/note")
    public Result<Map<String, String>> getInterviewNote(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String content = noteService.getInterviewNote(id, userId);
        return Result.success(Map.of("content", content != null ? content : ""));
    }

    /**
     * 保存面试笔记（新建或更新）
     *
     * @param id      面试 ID
     * @param request 笔记内容
     * @return 操作结果
     */
    @Operation(summary = "保存面试笔记")
    @PutMapping("/{id}/note")
    public Result<Void> saveInterviewNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        noteService.saveInterviewNote(id, userId, request.getContent());
        return Result.success();
    }

    /**
     * 获取问题笔记
     *
     * @param id  面试 ID
     * @param qid 问题 ID
     * @return 笔记内容
     */
    @Operation(summary = "获取问题笔记")
    @GetMapping("/{id}/questions/{qid}/note")
    public Result<Map<String, String>> getQuestionNote(@PathVariable Long id, @PathVariable Long qid) {
        Long userId = SecurityUtils.getCurrentUserId();
        String content = noteService.getQuestionNote(id, qid, userId);
        return Result.success(Map.of("content", content != null ? content : ""));
    }

    /**
     * 保存问题笔记（新建或更新）
     *
     * @param id      面试 ID
     * @param qid     问题 ID
     * @param request 笔记内容
     * @return 操作结果
     */
    @Operation(summary = "保存问题笔记")
    @PutMapping("/{id}/questions/{qid}/note")
    public Result<Void> saveQuestionNote(@PathVariable Long id, @PathVariable Long qid,
                                         @Valid @RequestBody NoteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        noteService.saveQuestionNote(id, qid, userId, request.getContent());
        return Result.success();
    }
}
