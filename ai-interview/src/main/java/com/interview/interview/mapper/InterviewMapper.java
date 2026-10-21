package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.Interview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {

    @Select("SELECT * FROM interview WHERE user_id=#{userId} AND status=0 AND deleted=0 LIMIT 1 FOR UPDATE")
    Interview selectActiveForUpdate(@Param("userId") Long userId);

    @Update("UPDATE interview SET processing_token=#{token}, lease_expires_at=#{leaseExpiresAt}, " +
            "heartbeat_at=NOW(), status=0 WHERE id=#{id} AND deleted=0 AND " +
            "(processing_token IS NULL OR lease_expires_at IS NULL OR lease_expires_at < NOW())")
    int claimTask(@Param("id") Long id, @Param("token") String token,
                  @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE interview SET heartbeat_at=NOW(), lease_expires_at=#{leaseExpiresAt} " +
            "WHERE id=#{id} AND processing_token=#{token} AND deleted=0")
    int heartbeat(@Param("id") Long id, @Param("token") String token,
                  @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE interview SET status=1, processing_step=4, processing_token=NULL, lease_expires_at=NULL, " +
            "heartbeat_at=NOW(), last_error_code=NULL, error_message=NULL WHERE id=#{id} AND processing_token=#{token} " +
            "AND status=0 AND deleted=0")
    int completeTask(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE interview SET status=1, processing_step=4, reanalysis_status='COMPLETED', reanalysis_error=NULL, " +
            "processing_token=NULL, lease_expires_at=NULL, heartbeat_at=NOW(), last_error_code=NULL, error_message=NULL " +
            "WHERE id=#{id} AND processing_token=#{token} AND status=0 AND deleted=0")
    int completeReanalysis(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE interview SET status=#{fallbackStatus}, reanalysis_status='FAILED', reanalysis_error=#{message}, " +
            "processing_token=NULL, lease_expires_at=NULL, heartbeat_at=NOW() WHERE id=#{id} " +
            "AND processing_token=#{token} AND deleted=0")
    int failReanalysis(@Param("id") Long id, @Param("token") String token,
                       @Param("fallbackStatus") int fallbackStatus, @Param("message") String message);

    @Update("UPDATE interview SET status=#{status}, processing_token=NULL, lease_expires_at=NULL, heartbeat_at=NOW(), " +
            "last_error_code=#{errorCode}, error_message=#{message} WHERE id=#{id} AND processing_token=#{token} AND deleted=0")
    int finishTaskWithError(@Param("id") Long id, @Param("token") String token, @Param("status") int status,
                            @Param("errorCode") String errorCode, @Param("message") String message);

    @Select("SELECT * FROM interview WHERE status=0 AND deleted=0 AND " +
            "(lease_expires_at IS NULL OR lease_expires_at < NOW())")
    List<Interview> selectRecoverable();

    @Select("SELECT COUNT(*) FROM interview WHERE id=#{id} AND processing_token=#{token} AND deleted=0")
    int ownsTask(@Param("id") Long id, @Param("token") String token);

    @Select("SELECT * FROM interview WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Interview selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM interview WHERE id=#{id} AND deleted=1")
    Interview selectDeletedById(@Param("id") Long id);

    @Select("SELECT * FROM interview WHERE user_id=#{userId} AND deleted=1 AND purged_at IS NULL " +
            "ORDER BY deleted_at DESC")
    List<Interview> selectRecycleBin(@Param("userId") Long userId);

    @Update("UPDATE interview SET deleted=1, deleted_at=NOW(), purge_after=DATE_ADD(NOW(), INTERVAL 7 DAY), " +
            "processing_token=NULL, lease_expires_at=NULL WHERE id=#{id} AND user_id=#{userId} AND deleted=0")
    int moveToRecycleBin(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE interview SET deleted=0, deleted_at=NULL, purge_after=NULL WHERE id=#{id} AND user_id=#{userId} " +
            "AND deleted=1 AND purged_at IS NULL AND purge_after>NOW()")
    int restore(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM interview WHERE deleted=1 AND purged_at IS NULL AND purge_after<=NOW()")
    List<Interview> selectExpiredDeleted();

    @Delete("DELETE FROM interview_question WHERE interview_id=#{interviewId}")
    int physicallyDeleteQuestions(@Param("interviewId") Long interviewId);

    @Delete("DELETE FROM interview_note WHERE interview_id=#{interviewId}")
    int physicallyDeleteNotes(@Param("interviewId") Long interviewId);

    @Delete("DELETE FROM interview_favorite WHERE interview_id=#{interviewId}")
    int physicallyDeleteFavorites(@Param("interviewId") Long interviewId);

    @Delete("DELETE c FROM interview_comment c JOIN interview_experience e ON c.experience_id=e.id WHERE e.interview_id=#{interviewId}")
    int physicallyDeleteComments(@Param("interviewId") Long interviewId);

    @Delete("DELETE FROM interview_experience WHERE interview_id=#{interviewId}")
    int physicallyDeleteShares(@Param("interviewId") Long interviewId);

    @Update("UPDATE interview SET audio_file_path=NULL, audio_file_size=NULL, resume_file_path=NULL, resume_text=NULL, " +
            "jd_text=NULL, transcript_text=NULL, transcript_segments=NULL, overall_score=NULL, dimension_content=NULL, " +
            "dimension_logic=NULL, dimension_expression=NULL, dimension_professional=NULL, dimension_communication=NULL, " +
            "improvement_summary=NULL, strengths=NULL, weaknesses=NULL, asr_config_snapshot=NULL, llm_config_snapshot=NULL, " +
            "resume_id=NULL, purged_at=NOW() WHERE id=#{id} AND deleted=1")
    int markPurged(@Param("id") Long id);
}
