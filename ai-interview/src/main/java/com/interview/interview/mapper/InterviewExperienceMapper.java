package com.interview.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.interview.entity.InterviewExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface InterviewExperienceMapper extends BaseMapper<InterviewExperience> {
    @Select("<script>SELECT e.* FROM interview_experience e " +
            "LEFT JOIN user u ON u.id=e.user_id LEFT JOIN interview i ON i.id=e.interview_id " +
            "WHERE e.deleted=0 AND e.publish_status='PUBLISHED' " +
            "<if test='title != null and title != \"\"'>AND e.title LIKE CONCAT('%',#{title},'%') </if>" +
            "<if test='author != null and author != \"\"'>AND (u.username LIKE CONCAT('%',#{author},'%') OR u.nickname LIKE CONCAT('%',#{author},'%') OR CAST(e.user_id AS CHAR)=#{author}) </if>" +
            "<if test='company != null and company != \"\"'>AND i.company_name LIKE CONCAT('%',#{company},'%') </if>" +
            "<if test='position != null and position != \"\"'>AND e.position_title LIKE CONCAT('%',#{position},'%') </if>" +
            "<if test='moderation != null and moderation != \"\"'>AND e.moderation_status=#{moderation} </if>" +
            "<if test='from != null'>AND e.published_at &gt;= #{from} </if>" +
            "<if test='to != null'>AND e.published_at &lt;= #{to} </if>" +
            "ORDER BY e.published_at DESC, e.id DESC</script>")
    Page<InterviewExperience> selectAdminPage(Page<InterviewExperience> page,
            @Param("title") String title, @Param("author") String author,
            @Param("company") String company, @Param("position") String position,
            @Param("moderation") String moderation, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
