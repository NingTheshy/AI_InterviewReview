# AI 配置模块完善设计

## 概述

完善 ai-ai 模块的 AiConfig CRUD 功能，添加 JavaDoc 注释、DTO 验证和测试覆盖。

## 范围

- **包含**: AiConfig 实体的完整 CRUD、默认配置管理
- **排除**: TranscriptionService、ScoringService、AiModelClient（保持接口状态）

## 变更清单

### 1. 新增 DTO

**`dto/AiConfigRequest.java`**

```java
@Data
public class AiConfigRequest {
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 50, message = "配置名称不能超过 50 个字符")
    private String configName;

    @NotBlank(message = "提供商不能为空")
    @Size(max = 50, message = "提供商名称不能超过 50 个字符")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不能超过 100 个字符")
    private String modelName;

    @Size(max = 500, message = "API Key 不能超过 500 个字符")
    private String apiKey;

    @NotBlank(message = "API 端点不能为空")
    @Size(max = 500, message = "API 端点不能超过 500 个字符")
    private String apiEndpoint;

    @NotNull(message = "配置类型不能为空")
    private Integer configType;  // 1=ASR, 2=LLM

    private Integer isDefault;  // 0=否, 1=是
    private Integer sortOrder;
    private Integer status;     // 0=禁用, 1=启用
}
```

### 2. JavaDoc 注释

为以下类添加类级和字段级 JavaDoc：
- `AiConfig.java` - 实体描述，字段说明
- `AiConfigMapper.java` - Mapper 描述
- `AiConfigService.java` - 接口方法文档
- `AiConfigServiceImpl.java` - 类文档（方法已有 @inheritDoc）
- `AiConfigController.java` - 类文档（已有 Swagger 注解）
- `AiClientFactory.java` - 工厂描述

### 3. Controller 更新

- create/update 方法参数改为 `@Valid @RequestBody AiConfigRequest request`
- 内部转换为 AiConfig 实体

### 4. 测试

**`service/impl/AiConfigServiceImplTest.java`** (14 tests)

| 方法 | 测试用例 |
|------|----------|
| list | 成功、空列表 |
| getDetail | 成功、不存在 |
| create | 成功、设为默认 |
| update | 成功、不存在、设为默认 |
| delete | 成功、不存在 |
| setDefault | 成功、不存在、清除旧默认 |

**`controller/AiConfigControllerTest.java`** (10 tests)

| 端点 | 测试用例 |
|------|----------|
| GET /ai-configs | 成功、空列表 |
| GET /ai-configs/{id} | 成功、不存在 |
| POST /ai-configs | 成功、缺少必填字段 |
| PUT /ai-configs/{id} | 成功、不存在 |
| DELETE /ai-configs/{id} | 成功、不存在 |
| PUT /ai-configs/{id}/default | 成功、不存在 |

## 文件清单

| 操作 | 文件路径 |
|------|----------|
| 新增 | `ai-ai/src/main/java/com/interview/ai/dto/AiConfigRequest.java` |
| 新增 | `ai-ai/src/test/java/com/interview/ai/service/impl/AiConfigServiceImplTest.java` |
| 新增 | `ai-ai/src/test/java/com/interview/ai/controller/AiConfigControllerTest.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/entity/AiConfig.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/mapper/AiConfigMapper.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/service/AiConfigService.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/service/impl/AiConfigServiceImpl.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/controller/AiConfigController.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/factory/AiClientFactory.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/service/AiModelClient.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/service/TranscriptionService.java` |
| 修改 | `ai-ai/src/main/java/com/interview/ai/service/ScoringService.java` |
