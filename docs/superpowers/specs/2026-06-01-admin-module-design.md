# 管理员模块完整开发设计

## 概述

补全管理员模块的缺失接口（面试/面经/评论管理），添加 JavaDoc 注释，增强测试覆盖。

## 范围

- **包含**: 用户管理（已有）、系统统计（已有）、面试管理（新增）、面经管理（新增）、评论管理（新增）
- **排除**: RBAC 权限表（MVP 阶段跳过）

## 变更清单

### 1. 新增 DTO

**`dto/InterviewManagementResponse.java`**

```java
@Data
@Builder
public class InterviewManagementResponse {
    private Long id;
    private Long userId;
    private String username;      // 关联查询
    private String title;
    private String companyName;
    private String positionTitle;
    private Integer status;
    private BigDecimal overallScore;
    private LocalDateTime createdAt;
}
```

**`dto/ExperienceManagementResponse.java`**

```java
@Data
@Builder
public class ExperienceManagementResponse {
    private Long id;
    private Long interviewId;
    private Long userId;
    private String username;
    private String shareToken;
    private Integer isPublic;
    private Integer viewCount;
    private LocalDateTime createdAt;
}
```

**`dto/CommentManagementResponse.java`**

```java
@Data
@Builder
public class CommentManagementResponse {
    private Long id;
    private Long shareId;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createdAt;
}
```

**`dto/ExperienceStatusRequest.java`**

```java
@Data
public class ExperienceStatusRequest {
    @NotNull(message = "状态不能为空")
    private Integer isPublic;  // 0=下架, 1=上架
}
```

### 2. Service 方法新增

**AdminService.java** 新增：

```java
// 面试管理
PageResult<InterviewManagementResponse> listInterviews(int page, int size, Long userId, Integer status);

// 面经管理
PageResult<ExperienceManagementResponse> listExperiences(int page, int size);
void toggleExperienceStatus(String token);

// 评论管理
PageResult<CommentManagementResponse> listComments(int page, int size, Long userId);
void deleteComment(Long commentId);
```

### 3. Controller 新增

**AdminInterviewController.java** — `/admin/interviews`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/interviews` | 面试列表（分页，支持 userId/status 筛选） |

**AdminExperienceController.java** — `/admin/experiences`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/experiences` | 面经列表（分页） |
| PUT | `/admin/experiences/{token}/status` | 下架/恢复面经 |

**AdminCommentController.java** — `/admin/comments`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/comments` | 评论列表（分页，支持 userId 筛选） |
| DELETE | `/admin/comments/{id}` | 删除评论 |

所有 Controller 添加 `@PreAuthorize("hasRole('ADMIN')")`。

### 4. JavaDoc 注释

为以下类添加类级和字段级 JavaDoc：
- 所有 DTO（字段说明）
- AdminService.java（方法级文档）
- AdminServiceImpl.java（类文档 + 私有方法）
- AdminUserController.java（已有，补充）
- AdminStatsController.java（已有，补充）
- AdminInterviewController.java（新增）
- AdminExperienceController.java（新增）
- AdminCommentController.java（新增）

### 5. 测试

**AdminServiceImplTest.java** — 新增 10 tests

| 方法 | 测试用例 |
|------|----------|
| listInterviews | 成功、按用户筛选、按状态筛选、空列表 |
| listExperiences | 成功、空列表 |
| toggleExperienceStatus | 成功、分享不存在 |
| listComments | 成功、按用户筛选、空列表 |
| deleteComment | 成功、评论不存在 |

**AdminUserControllerTest.java** — 新增 12 tests

| 端点 | 测试用例 |
|------|----------|
| GET /admin/users | 成功、空列表、关键词搜索 |
| GET /admin/users/{id} | 成功、不存在 |
| PUT /admin/users/{id}/status | 成功、不存在 |
| PUT /admin/users/{id}/role | 成功、不存在 |

**AdminInterviewControllerTest.java** — 新增 6 tests

| 端点 | 测试用例 |
|------|----------|
| GET /admin/interviews | 成功、空列表、按用户筛选、按状态筛选 |

**AdminExperienceControllerTest.java** — 新增 6 tests

| 端点 | 测试用例 |
|------|----------|
| GET /admin/experiences | 成功、空列表 |
| PUT /admin/experiences/{token}/status | 成功、不存在 |

**AdminCommentControllerTest.java** — 新增 6 tests

| 端点 | 测试用例 |
|------|----------|
| GET /admin/comments | 成功、空列表 |
| DELETE /admin/comments/{id} | 成功、不存在 |

## 文件清单

| 操作 | 文件路径 |
|------|----------|
| 新增 | `ai-admin/src/main/java/com/interview/admin/dto/InterviewManagementResponse.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/dto/ExperienceManagementResponse.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/dto/CommentManagementResponse.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/dto/ExperienceStatusRequest.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/controller/AdminInterviewController.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/controller/AdminExperienceController.java` |
| 新增 | `ai-admin/src/main/java/com/interview/admin/controller/AdminCommentController.java` |
| 新增 | `ai-admin/src/test/java/com/interview/admin/controller/AdminUserControllerTest.java` |
| 新增 | `ai-admin/src/test/java/com/interview/admin/controller/AdminInterviewControllerTest.java` |
| 新增 | `ai-admin/src/test/java/com/interview/admin/controller/AdminExperienceControllerTest.java` |
| 新增 | `ai-admin/src/test/java/com/interview/admin/controller/AdminCommentControllerTest.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/service/AdminService.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/service/impl/AdminServiceImpl.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/dto/StatsOverviewResponse.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/dto/UserManagementResponse.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/dto/UserStatusRequest.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/dto/UserRoleRequest.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/controller/AdminUserController.java` |
| 修改 | `ai-admin/src/main/java/com/interview/admin/controller/AdminStatsController.java` |
| 修改 | `ai-admin/src/test/java/com/interview/admin/service/impl/AdminServiceImplTest.java` |
