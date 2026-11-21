package com.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.common.constant.ErrorCode;
import com.interview.common.constant.InterviewStatus;
import com.interview.common.exception.BusinessException;
import com.interview.common.result.PageResult;
import com.interview.common.utils.AvatarPaths;
import com.interview.interview.dto.*;
import com.interview.interview.entity.*;
import com.interview.interview.mapper.*;
import com.interview.interview.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 公开面经使用独立题目快照，原面试的重评和清理不改变公开内容。 */
@Service
@RequiredArgsConstructor
public class ExperiencePublicationServiceImpl implements ExperienceService {
    private static final Set<String> SENSITIVE_TYPES = Set.of("PERSONAL", "PROJECT", "BEHAVIORAL");
    private static final Pattern CONTACT = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|(?<!\\d)\\d{17}[0-9Xx](?!\\d)|(?<!\\d)\\d{15}(?!\\d)");
    private final InterviewExperienceMapper experienceMapper;
    private final ExperienceQuestionSnapshotMapper snapshotMapper;
    private final ExperienceInteractionMapper interactionMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<ExperienceListResponse> listPublic(int page, int size, String keyword, String positionTitle, String sortBy) {
        validatePage(page, size);
        if (sortBy != null && !Set.of("publishedAt", "viewCount", "likeCount").contains(sortBy)) throw bad("排序参数不合法");
        LambdaQueryWrapper<InterviewExperience> query = visible();
        if (filled(keyword)) query.like(InterviewExperience::getTitle, keyword.trim());
        if (filled(positionTitle)) query.like(InterviewExperience::getPositionTitle, positionTitle.trim());
        if ("viewCount".equals(sortBy)) query.orderByDesc(InterviewExperience::getViewCount);
        else if ("likeCount".equals(sortBy)) query.orderByDesc(InterviewExperience::getLikeCount);
        query.orderByDesc(InterviewExperience::getPublishedAt, InterviewExperience::getId);
        Page<InterviewExperience> result = experienceMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(cards(result.getRecords()), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public ExperienceDetailResponse getDetail(Long id, Long viewerId) {
        InterviewExperience experience = requireVisible(id);
        interactionMapper.incrementView(id);
        User author = userMapper.selectById(experience.getUserId());
        List<ExperienceDetailResponse.QuestionItem> questions = selected(id).stream()
                .map(q -> ExperienceDetailResponse.QuestionItem.builder().id(q.getId())
                        .questionIndex(q.getQuestionIndex()).questionText(q.getQuestionText())
                        .referenceAnswer(Integer.valueOf(1).equals(experience.getShowAnswers()) ? q.getReferenceAnswer() : null)
                        .build()).toList();
        return ExperienceDetailResponse.builder().id(id)
                .authorNickname(author == null ? null : author.getNickname())
                .authorAvatar(author == null ? null : AvatarPaths.visibleUrl(author.getAvatar()))
                .title(experience.getTitle()).positionTitle(experience.getPositionTitle())
                .questions(questions).viewCount((experience.getViewCount() == null ? 0 : experience.getViewCount()) + 1)
                .likeCount(experience.getLikeCount()).favoriteCount(experience.getFavoriteCount())
                .commentCount(interactionMapper.countComments(id))
                .liked(viewerId != null && interactionMapper.hasLike(id, viewerId) > 0)
                .favorited(viewerId != null && interactionMapper.hasFavorite(id, viewerId) > 0)
                .publishedAt(experience.getPublishedAt()).build();
    }

    @Override
    @Transactional
    public ExperiencePublicationResponse publish(Long interviewId, Long userId, ExperiencePublicationRequest request) {
        Interview source = interviewMapper.selectById(interviewId);
        InterviewExperience previous = request != null && request.getSourceExperienceId() != null
                ? owned(request.getSourceExperienceId(), userId) : latest(interviewId, userId);
        if (previous != null && !interviewId.equals(previous.getInterviewId())) throw forbidden();
        if (source != null && !userId.equals(source.getUserId())) throw forbidden();
        if (source == null && previous == null) throw notFound();
        if (source != null && !Integer.valueOf(InterviewStatus.COMPLETED.getCode()).equals(source.getStatus()) && previous == null)
            throw bad("只有已完成面试可以发布面经");
        if (blocked(interviewId)) throw forbidden();
        if (experienceMapper.selectCount(new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getInterviewId, interviewId)
                .eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED)) > 0) throw bad("本场面试已有公开面经");
        String position = source != null ? source.getPositionTitle() : previous.getPositionTitle();
        InterviewExperience value = new InterviewExperience();
        value.setInterviewId(interviewId); value.setUserId(userId);
        value.setPositionTitle(position); value.setPublishStatus(InterviewExperience.PUBLISHED);
        value.setModerationStatus(InterviewExperience.NORMAL); value.setPublishedAt(LocalDateTime.now());
        value.setViewCount(0); value.setLikeCount(0); value.setFavoriteCount(0);
        value.setShowAnswers(Boolean.TRUE.equals(request.getShowReferenceAnswers()) ? 1 : 0);
        value.setTitle(title(request.getTitle(), position));
        validatePublicHeader(value.getTitle(), position);
        Map<Long, ExperienceQuestionSnapshot> candidates = candidates(interviewId, userId, previous);
        List<ExperienceQuestionSnapshot> chosen = validateSelection(request, candidates);
        experienceMapper.insert(value);
        for (ExperienceQuestionSnapshot candidate : chosen) {
            ExperienceQuestionSnapshot copy = copy(candidate, value.getId());
            copy.setSelected(1); snapshotMapper.insert(copy);
        }
        return publication(value);
    }

    @Override
    public ExperiencePublicationResponse getPublication(Long interviewId, Long userId) {
        InterviewExperience value = current(interviewId, userId);
        if (value == null) throw notFound();
        return publication(value);
    }

    @Override
    public ExperienceEditorResponse editor(Long interviewId, Long userId) {
        InterviewExperience value = latest(interviewId, userId);
        Interview source = interviewMapper.selectById(interviewId);
        if (source != null && !userId.equals(source.getUserId())) throw forbidden();
        if (source == null && value == null) throw notFound();
        return editorResponse(interviewId, userId, value);
    }

    @Override
    public ExperienceEditorResponse editPublished(Long experienceId, Long userId) {
        InterviewExperience value = owned(experienceId, userId);
        return editorResponse(value.getInterviewId(), userId, value);
    }

    @Override
    @Transactional
    public ExperiencePublicationResponse updatePublication(Long experienceId, Long userId, ExperiencePublicationRequest request) {
        InterviewExperience value = owned(experienceId, userId);
        if (!InterviewExperience.PUBLISHED.equals(value.getPublishStatus())) throw bad("已删除面经不能直接编辑，请重新发布");
        if (InterviewExperience.BLOCKED.equals(value.getModerationStatus())) throw forbidden();
        Map<Long, ExperienceQuestionSnapshot> available = candidates(value.getInterviewId(), userId, value);
        List<ExperienceQuestionSnapshot> chosen = validateSelection(request, available);
        value.setTitle(title(request.getTitle(), value.getPositionTitle()));
        validatePublicHeader(value.getTitle(), value.getPositionTitle());
        value.setShowAnswers(Boolean.TRUE.equals(request.getShowReferenceAnswers()) ? 1 : 0);
        experienceMapper.updateById(value);
        Map<Long, ExperienceQuestionSnapshot> stored = snapshots(value.getId()).stream()
                .collect(Collectors.toMap(ExperienceQuestionSnapshot::getSourceQuestionId, Function.identity()));
        Set<Long> selectedIds = chosen.stream().map(ExperienceQuestionSnapshot::getSourceQuestionId).collect(Collectors.toSet());
        for (ExperienceQuestionSnapshot old : stored.values()) {
            old.setSelected(selectedIds.contains(old.getSourceQuestionId()) ? 1 : 0);
            snapshotMapper.updateById(old);
        }
        for (ExperienceQuestionSnapshot item : chosen) {
            if (!stored.containsKey(item.getSourceQuestionId())) snapshotMapper.insert(copy(item, value.getId()));
        }
        return publication(value);
    }

    @Override
    @Transactional
    public void deletePublication(Long experienceId, Long userId) {
        InterviewExperience value = owned(experienceId, userId);
        if (!InterviewExperience.PUBLISHED.equals(value.getPublishStatus())) return;
        if (InterviewExperience.BLOCKED.equals(value.getModerationStatus())) throw forbidden();
        value.setPublishStatus(InterviewExperience.DELETED);
        value.setLikeCount(0); value.setFavoriteCount(0);
        experienceMapper.updateById(value);
        interactionMapper.clearComments(experienceId);
        interactionMapper.clearLikes(experienceId);
        interactionMapper.clearFavorites(experienceId);
    }

    @Override
    public PageResult<ExperiencePublicationResponse> mine(Long userId, int page, int size) {
        validatePage(page, size);
        Page<InterviewExperience> result = experienceMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<InterviewExperience>().eq(InterviewExperience::getUserId, userId)
                        .orderByDesc(InterviewExperience::getId));
        return new PageResult<>(result.getRecords().stream().map(this::publication).toList(), result.getTotal(), page, size);
    }

    @Override
    public PageResult<ExperienceListResponse> favorites(Long userId, int page, int size) {
        validatePage(page, size);
        List<Long> ids = interactionMapper.favoriteIds(userId, (page - 1) * size, size);
        List<InterviewExperience> values = ids.stream().map(experienceMapper::selectById)
                .filter(Objects::nonNull).filter(InterviewExperience::isVisible).toList();
        return new PageResult<>(cards(values), interactionMapper.favoriteTotal(userId), page, size);
    }

    @Override
    @Transactional
    public void like(Long experienceId, Long userId, boolean enabled) {
        requireVisible(experienceId);
        int changed = enabled ? interactionMapper.addLike(experienceId, userId) : interactionMapper.removeLike(experienceId, userId);
        if (changed > 0) interactionMapper.changeLikeCount(experienceId, enabled ? 1 : -1);
    }

    @Override
    @Transactional
    public void favorite(Long experienceId, Long userId, boolean enabled) {
        requireVisible(experienceId);
        int changed = enabled ? interactionMapper.addFavorite(experienceId, userId) : interactionMapper.removeFavorite(experienceId, userId);
        if (changed > 0) interactionMapper.changeFavoriteCount(experienceId, enabled ? 1 : -1);
    }

    private ExperienceEditorResponse editorResponse(Long interviewId, Long userId, InterviewExperience value) {
        Map<Long, ExperienceQuestionSnapshot> available = candidates(interviewId, userId, value);
        List<Long> selectedIds = value == null ? List.of() : selected(value.getId()).stream()
                .map(ExperienceQuestionSnapshot::getSourceQuestionId).toList();
        return ExperienceEditorResponse.builder().id(value == null ? null : value.getId()).interviewId(interviewId)
                .title(value == null ? null : value.getTitle())
                .positionTitle(value == null ? null : value.getPositionTitle())
                .showReferenceAnswers(value != null && Integer.valueOf(1).equals(value.getShowAnswers()))
                .publishStatus(value == null ? null : value.getPublishStatus())
                .moderationStatus(value == null ? null : value.getModerationStatus())
                .selectedQuestionIds(selectedIds)
                .questions(available.values().stream().sorted(Comparator.comparing(ExperienceQuestionSnapshot::getQuestionIndex))
                        .map(q -> ExperienceEditorResponse.QuestionItem.builder().id(q.getSourceQuestionId())
                                .questionIndex(q.getQuestionIndex()).questionText(q.getQuestionText())
                                .referenceAnswer(q.getReferenceAnswer()).questionType(q.getQuestionType())
                                .sensitive(isSensitive(q.getQuestionType())).build()).toList()).build();
    }

    private Map<Long, ExperienceQuestionSnapshot> candidates(Long interviewId, Long userId, InterviewExperience previous) {
        Map<Long, ExperienceQuestionSnapshot> result = new LinkedHashMap<>();
        if (previous != null) for (ExperienceQuestionSnapshot q : snapshots(previous.getId())) result.put(q.getSourceQuestionId(), q);
        Interview source = interviewMapper.selectById(interviewId);
        if (source != null && userId.equals(source.getUserId())
                && Integer.valueOf(InterviewStatus.COMPLETED.getCode()).equals(source.getStatus())) {
            for (InterviewQuestion q : questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getInterviewId, interviewId).eq(InterviewQuestion::getIsActive, 1)
                    .orderByAsc(InterviewQuestion::getQuestionIndex))) {
                if (!result.containsKey(q.getId())) {
                    ExperienceQuestionSnapshot item = new ExperienceQuestionSnapshot();
                    item.setSourceQuestionId(q.getId()); item.setQuestionIndex(q.getQuestionIndex());
                    item.setQuestionText(q.getQuestionText()); item.setReferenceAnswer(q.getReferenceAnswer());
                    item.setQuestionType(q.getQuestionType()); item.setSelected(0);
                    result.put(q.getId(), item);
                }
            }
        }
        return result;
    }

    private List<ExperienceQuestionSnapshot> validateSelection(ExperiencePublicationRequest request,
            Map<Long, ExperienceQuestionSnapshot> available) {
        if (request == null || request.getSelectedQuestionIds() == null || request.getSelectedQuestionIds().isEmpty())
            throw bad("至少选择一道题目");
        Set<Long> ids = new LinkedHashSet<>(request.getSelectedQuestionIds());
        if (ids.size() != request.getSelectedQuestionIds().size()) throw bad("题目不能重复选择");
        Set<Long> confirmed = request.getConfirmedSensitiveQuestionIds() == null ? Set.of()
                : new HashSet<>(request.getConfirmedSensitiveQuestionIds());
        List<ExperienceQuestionSnapshot> chosen = new ArrayList<>();
        for (Long id : ids) {
            ExperienceQuestionSnapshot item = available.get(id);
            if (item == null || !filled(item.getQuestionText())) throw bad("所选题目不存在或为空");
            if (isSensitive(item.getQuestionType()) && !confirmed.contains(id))
                throw bad("第" + item.getQuestionIndex() + "题涉及个人经历，需逐题确认");
            String publicText = item.getQuestionText();
            if (Boolean.TRUE.equals(request.getShowReferenceAnswers())) {
                if (!filled(item.getReferenceAnswer())) throw bad("第" + item.getQuestionIndex() + "题没有 AI 标准答案");
                publicText += item.getReferenceAnswer();
            }
            if (CONTACT.matcher(publicText).find()) throw bad("第" + item.getQuestionIndex() + "题包含明显个人标识，请取消勾选");
            chosen.add(item);
        }
        return chosen;
    }

    private ExperienceQuestionSnapshot copy(ExperienceQuestionSnapshot q, Long experienceId) {
        ExperienceQuestionSnapshot value = new ExperienceQuestionSnapshot();
        value.setExperienceId(experienceId); value.setSourceQuestionId(q.getSourceQuestionId());
        value.setQuestionIndex(q.getQuestionIndex()); value.setQuestionText(q.getQuestionText());
        value.setReferenceAnswer(q.getReferenceAnswer()); value.setQuestionType(q.getQuestionType());
        value.setSelected(1); return value;
    }

    private List<ExperienceQuestionSnapshot> snapshots(Long id) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<ExperienceQuestionSnapshot>()
                .eq(ExperienceQuestionSnapshot::getExperienceId, id).orderByAsc(ExperienceQuestionSnapshot::getQuestionIndex));
    }
    private List<ExperienceQuestionSnapshot> selected(Long id) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<ExperienceQuestionSnapshot>()
                .eq(ExperienceQuestionSnapshot::getExperienceId, id).eq(ExperienceQuestionSnapshot::getSelected, 1)
                .orderByAsc(ExperienceQuestionSnapshot::getQuestionIndex));
    }
    private LambdaQueryWrapper<InterviewExperience> visible() {
        return new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED)
                .eq(InterviewExperience::getModerationStatus, InterviewExperience.NORMAL);
    }
    private InterviewExperience requireVisible(Long id) {
        InterviewExperience value = experienceMapper.selectById(id);
        if (value == null || !value.isVisible()) throw notFound();
        return value;
    }
    private InterviewExperience owned(Long id, Long userId) {
        InterviewExperience value = experienceMapper.selectById(id);
        if (value == null || !userId.equals(value.getUserId())) throw forbidden();
        return value;
    }
    private InterviewExperience latest(Long interviewId, Long userId) {
        return experienceMapper.selectOne(new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getInterviewId, interviewId).eq(InterviewExperience::getUserId, userId)
                .orderByDesc(InterviewExperience::getId).last("LIMIT 1"));
    }
    private InterviewExperience current(Long interviewId, Long userId) {
        return experienceMapper.selectOne(new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getInterviewId, interviewId).eq(InterviewExperience::getUserId, userId)
                .eq(InterviewExperience::getPublishStatus, InterviewExperience.PUBLISHED));
    }
    private boolean blocked(Long interviewId) {
        return experienceMapper.selectCount(new LambdaQueryWrapper<InterviewExperience>()
                .eq(InterviewExperience::getInterviewId, interviewId)
                .eq(InterviewExperience::getModerationStatus, InterviewExperience.BLOCKED)) > 0;
    }
    private List<ExperienceListResponse> cards(List<InterviewExperience> values) {
        return values.stream().map(value -> {
            User author = userMapper.selectById(value.getUserId());
            return ExperienceListResponse.builder().id(value.getId()).title(value.getTitle())
                    .positionTitle(value.getPositionTitle())
                    .authorNickname(author == null ? null : author.getNickname())
                    .authorAvatar(author == null ? null : AvatarPaths.visibleUrl(author.getAvatar()))
                    .questionCount(selected(value.getId()).size()).viewCount(value.getViewCount())
                    .likeCount(value.getLikeCount()).favoriteCount(value.getFavoriteCount())
                    .publishedAt(value.getPublishedAt()).build();
        }).toList();
    }
    private ExperiencePublicationResponse publication(InterviewExperience value) {
        ExperiencePublicationResponse response = ExperiencePublicationResponse.from(value);
        response.setSelectedQuestionIds(selected(value.getId()).stream()
                .map(ExperienceQuestionSnapshot::getSourceQuestionId).toList());
        return response;
    }
    private String title(String raw, String position) {
        String value = filled(raw) ? raw.trim() : position + "面经-" + LocalDate.now();
        if (value.length() > 200) throw bad("标题不能超过200字");
        return value;
    }
    private void validatePublicHeader(String title, String position) {
        if (CONTACT.matcher(title + " " + position).find()) throw bad("标题或岗位包含明显个人标识，请修改后发布");
    }
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数不合法");
    }
    private boolean filled(String value) { return value != null && !value.isBlank(); }
    private boolean isSensitive(String questionType) { return questionType != null && SENSITIVE_TYPES.contains(questionType); }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message); }
    private BusinessException notFound() { return new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND.getCode(), ErrorCode.EXPERIENCE_NOT_FOUND.getMessage()); }
    private BusinessException forbidden() { return new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该面经或面经已被管理员下架"); }
}
