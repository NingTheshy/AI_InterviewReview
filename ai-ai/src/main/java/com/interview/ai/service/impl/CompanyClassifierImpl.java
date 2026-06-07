package com.interview.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.ai.factory.AiClientFactory;
import com.interview.ai.service.CompanyClassifier;
import com.interview.ai.service.LlmClient;
import com.interview.common.constant.CompanyTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公司分级服务实现类
 * <p>
 * 采用三级策略判断企业档次：
 * <ol>
 *   <li>硬编码已知公司名单（阿里、腾讯、字节等）</li>
 *   <li>AI 分级结果缓存</li>
 *   <li>LLM 调用（6个评估维度）</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyClassifierImpl implements CompanyClassifier {

    private final AiClientFactory aiClientFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 已知公司分级缓存（硬编码） */
    private static final Map<String, CompanyTier> KNOWN_COMPANIES = new ConcurrentHashMap<>();

    /** AI 分级结果缓存 */
    private static final Map<String, CompanyTier> AI_CLASSIFICATION_CACHE = new ConcurrentHashMap<>();

    static {
        // TIER_1: 超大厂 — 全球/全国龙头，员工>10万
        Set<String> tier1 = Set.of(
                // 国内
                "阿里巴巴", "阿里", "腾讯", "字节跳动", "字节", "百度", "美团", "华为",
                "小米", "京东", "网易", "拼多多", "蚂蚁集团", "蚂蚁金服",
                // 国际
                "苹果", "apple", "谷歌", "google", "微软", "microsoft",
                "亚马逊", "amazon", "meta", "facebook", "特斯拉", "tesla",
                "英伟达", "nvidia", "英特尔", "intel", "三星", "samsung",
                "oracle", "甲骨文", "salesforce", "adobe", "netflix",
                "ibm", "思科", "cisco", "高通", "qualcomm"
        );
        tier1.forEach(name -> KNOWN_COMPANIES.put(name.toLowerCase(), CompanyTier.TIER_1));

        // TIER_2: 大厂 — 国内知名，员工>1万
        Set<String> tier2 = Set.of(
                "快手", "滴滴", "携程", "bilibili", "b站", "知乎",
                "微博", "新浪", "搜狐", "网易", "盛大", "巨人网络",
                "中兴", "联想", "tcl", "比亚迪", "宁德时代",
                "oppo", "vivo", "荣耀", "大疆", "商汤", "旷视",
                "小米科技", "网易游戏", "腾讯游戏", "完美世界",
                "中国平安", "招商银行", "工商银行", "建设银行",
                "中国移动", "中国电信", "中国联通",
                "丰田", "toyota", "大众", "volkswagen", "宝马", "bmw",
                "奔驰", "mercedes", "索尼", "sony", "松下", "panasonic"
        );
        tier2.forEach(name -> KNOWN_COMPANIES.put(name.toLowerCase(), CompanyTier.TIER_2));

        // TIER_3: 中厂 — 区域知名，员工1000-10000
        Set<String> tier3 = Set.of(
                "货拉拉", "得物", "boss直聘", "boss",
                "番茄小说", "飞猪", "饿了么", "闲鱼", "盒马",
                "酷狗音乐", "酷我音乐", "喜马拉雅", "小红书",
                "keep", "soul", "探探", "陌陌",
                "猿辅导", "好未来", "作业帮", "学而思",
                "蔚来", "小鹏", "理想", "零跑",
                "大华", "海康威视", "科大讯飞", "寒武纪",
                "汇顶科技", "韦尔股份", "卓胜微"
        );
        tier3.forEach(name -> KNOWN_COMPANIES.put(name.toLowerCase(), CompanyTier.TIER_3));
    }

    @Override
    public CompanyTier classify(String companyName, String industry, String jdText) {
        if (companyName == null || companyName.isBlank()) {
            log.info("公司名称为空，默认为中厂");
            return CompanyTier.TIER_3;
        }

        // 1. 查已知公司名单
        String key = companyName.trim().toLowerCase();
        CompanyTier knownTier = KNOWN_COMPANIES.get(key);
        if (knownTier != null) {
            log.info("公司分级(已知): {} -> {}", companyName, knownTier.getName());
            return knownTier;
        }

        // 2. 查 AI 分级缓存
        CompanyTier cached = AI_CLASSIFICATION_CACHE.get(key);
        if (cached != null) {
            log.info("公司分级(缓存): {} -> {}", companyName, cached.getName());
            return cached;
        }

        // 3. 调用 AI 分级
        CompanyTier aiTier = classifyByAi(companyName, industry, jdText);
        AI_CLASSIFICATION_CACHE.put(key, aiTier);
        log.info("公司分级(AI): {} -> {}", companyName, aiTier.getName());
        return aiTier;
    }

    private CompanyTier classifyByAi(String companyName, String industry, String jdText) {
        try {
            LlmClient client = aiClientFactory.getDefaultLlmClient();

            String jdSnippet = jdText != null && jdText.length() > 200
                    ? jdText.substring(0, 200) + "..."
                    : jdText;

            String systemPrompt = """
                    你是一位企业分析专家。请从以下多个维度综合判断公司等级：

                    【评估维度】
                    1. 市场地位：市值/营收排名、行业排名、市场份额
                    2. 品牌影响力：全球/全国/区域知名度、媒体曝光度
                    3. 技术实力：开源贡献、专利数量、技术博客活跃度、技术峰会参与
                    4. 人才规模：员工总数、工程师占比、招聘规模
                    5. 融资/上市状态：上市、估值、融资轮次
                    6. 行业影响力：是否参与标准制定、是否为行业标杆

                    【等级标准】
                    1 - 超大厂：上述维度中≥5项达到行业顶级（如苹果、谷歌、华为、阿里、腾讯）
                    2 - 大厂：上述维度中≥4项达到行业前列（如京东、美团、小米、快手）
                    3 - 中厂：上述维度中≥3项表现良好，在细分领域有影响力（如货拉拉、得物、小红书）
                    4 - 小厂：仅1-2项表现良好，业务模式验证中
                    5 - 初创：处于早期阶段，多维度尚不成熟

                    请综合分析，如果信息不足，保守地选择 3（中厂）。
                    只返回 JSON，格式：{"tier": 数字, "reason": "基于多维度的判断理由"}
                    """;

            String userPrompt = String.format(
                    "公司名称：%s\n行业：%s\n岗位JD摘要：%s",
                    companyName,
                    industry != null ? industry : "未知",
                    jdSnippet != null ? jdSnippet : "无"
            );

            String result = client.call(userPrompt, systemPrompt, null);
            JsonNode root = objectMapper.readTree(result);

            int tierCode = root.path("tier").asInt(3);
            if (tierCode < 1 || tierCode > 5) {
                tierCode = 3;
            }
            return CompanyTier.fromCode(tierCode);
        } catch (Exception e) {
            log.warn("AI 公司分级失败，默认为中厂: {}", e.getMessage());
            return CompanyTier.TIER_3;
        }
    }
}
