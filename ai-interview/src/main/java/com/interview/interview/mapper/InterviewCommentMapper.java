package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.interview.entity.InterviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface InterviewCommentMapper extends BaseMapper<InterviewComment> {
    /** 串行化同一篇面经的评论发表与级联删除。 */
    @Select("SELECT id FROM interview_experience WHERE id = #{experienceId} FOR UPDATE")
    Long lockExperience(@Param("experienceId") Long experienceId);
    /** 根评论和直接回复分别分页，仅返回仍可见的评论。 */
    @Select("""
            <script>
            SELECT c.*, (SELECT COUNT(*) FROM interview_comment child WHERE child.parent_id = c.id AND child.deleted = 0) AS reply_count
            FROM interview_comment c
            WHERE c.experience_id = #{experienceId} AND c.deleted = 0
            <choose>
              <when test="parentId == null">AND c.parent_id IS NULL</when>
              <otherwise>AND c.parent_id = #{parentId}</otherwise>
            </choose>
            ORDER BY c.created_at DESC, c.id DESC
            </script>
            """)
    Page<InterviewComment> selectThreadPage(Page<InterviewComment> page,
                                            @Param("experienceId") Long experienceId,
                                            @Param("parentId") Long parentId);

    @Select("SELECT * FROM interview_comment WHERE id = #{id}")
    InterviewComment selectAnyById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id FROM interview_comment WHERE deleted = 0 AND parent_id IN
            <foreach collection="parentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> selectChildIds(@Param("parentIds") List<Long> parentIds);

    @Update("""
            <script>
            UPDATE interview_comment SET deleted = 1, deleted_at = NOW(),
                   purge_after = DATE_ADD(NOW(), INTERVAL 7 DAY), updated_at = NOW()
            WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int softDeleteIds(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM interview_comment WHERE deleted = 1 AND purge_after <= NOW() LIMIT 500")
    int purgeExpiredDeleted();

    @Select("""
            <script>
            SELECT c.*, u.username AS author_username, u.nickname AS author_nickname,
                   e.title AS experience_title, p.content AS parent_content
            FROM interview_comment c
            LEFT JOIN user u ON u.id = c.user_id
            LEFT JOIN interview_experience e ON e.id = c.experience_id
            LEFT JOIN interview_comment p ON p.id = c.parent_id
            WHERE c.deleted = #{status}
            <if test="keyword != null and keyword != ''">AND c.content LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="author != null and author != ''">AND (u.username LIKE CONCAT('%', #{author}, '%') OR u.nickname LIKE CONCAT('%', #{author}, '%'))</if>
            <if test="experience != null and experience != ''">AND e.title LIKE CONCAT('%', #{experience}, '%')</if>
            <if test="userId != null">AND c.user_id = #{userId}</if>
            <if test="startTime != null">AND c.created_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND c.created_at &lt;= #{endTime}</if>
            ORDER BY c.created_at DESC, c.id DESC
            </script>
            """)
    Page<InterviewComment> selectAdminPage(Page<InterviewComment> page,
                                           @Param("keyword") String keyword,
                                           @Param("author") String author,
                                           @Param("experience") String experience,
                                           @Param("userId") Long userId,
                                           @Param("status") Integer status,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT c.*, u.username AS author_username, u.nickname AS author_nickname,
                   e.title AS experience_title, p.content AS parent_content
            FROM interview_comment c
            LEFT JOIN user u ON u.id = c.user_id
            LEFT JOIN interview_experience e ON e.id = c.experience_id
            LEFT JOIN interview_comment p ON p.id = c.parent_id
            WHERE c.id = #{id}
            """)
    InterviewComment selectAdminById(@Param("id") Long id);
}
