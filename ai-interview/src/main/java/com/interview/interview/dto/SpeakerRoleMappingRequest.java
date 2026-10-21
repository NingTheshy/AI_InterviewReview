package com.interview.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 人工校正一场面试中的匿名说话人角色。 */
@Data
public class SpeakerRoleMappingRequest {
    @Valid
    @NotEmpty(message = "说话人映射不能为空")
    private List<Mapping> mappings;

    @Data
    public static class Mapping {
        @NotBlank(message = "speakerId不能为空")
        private String speakerId;
        @NotBlank(message = "role不能为空")
        private String role;
    }
}
