package com.interview.interview.service;

import com.interview.interview.entity.UserResume;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 用户简历库服务。 */
public interface ResumeService {
    UserResume upload(Long userId, MultipartFile file);
    List<UserResume> list(Long userId);
    UserResume requireReady(Long resumeId, Long userId);
    void delete(Long resumeId, Long userId);
}
