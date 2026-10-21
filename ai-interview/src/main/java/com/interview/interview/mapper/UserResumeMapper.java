package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.UserResume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserResumeMapper extends BaseMapper<UserResume> {
    @Select("SELECT * FROM user_resume WHERE user_id=#{userId} AND content_hash=#{contentHash} " +
            "AND deleted=0 ORDER BY id DESC LIMIT 1")
    UserResume findReusable(@Param("userId") Long userId, @Param("contentHash") String contentHash);

    @Update("UPDATE user_resume SET processing_token=#{token}, lease_expires_at=#{leaseExpiresAt}, " +
            "heartbeat_at=NOW(), parse_status=0, parse_error=NULL WHERE id=#{id} AND deleted=0 AND " +
            "(processing_token IS NULL OR lease_expires_at IS NULL OR lease_expires_at<NOW())")
    int claimParse(@Param("id") Long id, @Param("token") String token,
                   @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Select("SELECT COUNT(*) FROM user_resume WHERE id=#{id} AND processing_token=#{token} AND deleted=0")
    int ownsParse(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE user_resume SET parse_status=2, parse_error=#{message}, processing_token=NULL, " +
            "lease_expires_at=NULL, heartbeat_at=NOW() WHERE id=#{id} AND processing_token=#{token} AND deleted=0")
    int failParse(@Param("id") Long id, @Param("token") String token, @Param("message") String message);

    @Select("SELECT * FROM user_resume WHERE parse_status=0 AND deleted=0 AND " +
            "(lease_expires_at IS NULL OR lease_expires_at<NOW())")
    List<UserResume> selectRecoverable();

    @Select("SELECT COUNT(*) FROM interview WHERE resume_id=#{resumeId}")
    int countInterviewReferences(@Param("resumeId") Long resumeId);

    @Select("SELECT r.* FROM user_resume r WHERE r.deleted=1 AND NOT EXISTS " +
            "(SELECT 1 FROM interview i WHERE i.resume_id=r.id) LIMIT 100")
    List<UserResume> selectHiddenUnreferenced();

    @Delete("DELETE FROM user_resume WHERE id=#{id} AND deleted=1 AND NOT EXISTS " +
            "(SELECT 1 FROM interview i WHERE i.resume_id=#{id})")
    int physicallyDelete(@Param("id") Long id);
}
