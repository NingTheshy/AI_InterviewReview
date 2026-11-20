package com.interview.interview.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExperienceInteractionMapper {
    @Update("UPDATE interview_experience SET view_count=view_count+1 WHERE id=#{experienceId}")
    int incrementView(@Param("experienceId") Long experienceId);

    @Select("SELECT COUNT(*) FROM interview_comment WHERE experience_id=#{experienceId} AND deleted=0")
    int countComments(@Param("experienceId") Long experienceId);
    @Insert("INSERT IGNORE INTO experience_like (experience_id,user_id) VALUES (#{experienceId},#{userId})")
    int addLike(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Delete("DELETE FROM experience_like WHERE experience_id=#{experienceId} AND user_id=#{userId}")
    int removeLike(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO experience_favorite (experience_id,user_id) VALUES (#{experienceId},#{userId})")
    int addFavorite(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Delete("DELETE FROM experience_favorite WHERE experience_id=#{experienceId} AND user_id=#{userId}")
    int removeFavorite(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM experience_like WHERE experience_id=#{experienceId} AND user_id=#{userId}")
    int hasLike(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM experience_favorite WHERE experience_id=#{experienceId} AND user_id=#{userId}")
    int hasFavorite(@Param("experienceId") Long experienceId, @Param("userId") Long userId);

    @Update("UPDATE interview_experience SET like_count=GREATEST(0,like_count+#{delta}) WHERE id=#{experienceId}")
    int changeLikeCount(@Param("experienceId") Long experienceId, @Param("delta") int delta);

    @Update("UPDATE interview_experience SET favorite_count=GREATEST(0,favorite_count+#{delta}) WHERE id=#{experienceId}")
    int changeFavoriteCount(@Param("experienceId") Long experienceId, @Param("delta") int delta);

    @Delete("DELETE FROM experience_like WHERE experience_id=#{experienceId}")
    int clearLikes(@Param("experienceId") Long experienceId);

    @Delete("DELETE FROM experience_favorite WHERE experience_id=#{experienceId}")
    int clearFavorites(@Param("experienceId") Long experienceId);

    @Delete("DELETE FROM interview_comment WHERE experience_id=#{experienceId}")
    int clearComments(@Param("experienceId") Long experienceId);

    @Select("SELECT f.experience_id FROM experience_favorite f JOIN interview_experience e ON e.id=f.experience_id "
            + "WHERE f.user_id=#{userId} AND e.publish_status='PUBLISHED' AND e.moderation_status='NORMAL' "
            + "AND e.deleted=0 ORDER BY f.created_at DESC LIMIT #{offset},#{size}")
    java.util.List<Long> favoriteIds(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM experience_favorite f JOIN interview_experience e ON e.id=f.experience_id "
            + "WHERE f.user_id=#{userId} AND e.publish_status='PUBLISHED' AND e.moderation_status='NORMAL' AND e.deleted=0")
    long favoriteTotal(@Param("userId") Long userId);
}
