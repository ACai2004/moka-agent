package com.moka.ai.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.context.DishKnowledge;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜品知识检索器。
 * <p>
 * MVP 策略：精确匹配（HashMap），无需 Embedding。
 * 1. 启动时从 JSON 文件加载菜品知识到内存 HashMap
 * 2. 根据菜品名精确匹配（O(1) 查找）
 * 3. 匹配不到的菜品返回空，不阻塞下游
 */
@Component
public class DishRetriever {

    private static final Logger log = LoggerFactory.getLogger(DishRetriever.class);
    private static final String DATA_FILE = "data/sample-dish-knowledge.json";

    private final ObjectMapper objectMapper;
    private Map<String, DishKnowledge> knowledgeMap = Collections.emptyMap();
    private Map<String, DishKnowledge> strippedNameMap = Collections.emptyMap();

    public DishRetriever(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource(DATA_FILE);
            if (!resource.exists()) {
                log.warn("菜品知识数据文件不存在: {}, DishRetriever 将返回空结果", DATA_FILE);
                return;
            }
            List<DishKnowledge> list = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<DishKnowledge>>() {}
            );
            knowledgeMap = list.stream()
                    .collect(Collectors.toMap(
                            DishKnowledge::dishName,
                            dk -> dk,
                            (a, b) -> a   // 同名只保留第一个
                    ));
            // 同时构建去括号后的映射，用于模糊匹配
            strippedNameMap = list.stream()
                    .collect(Collectors.toMap(
                            dk -> stripParentheses(dk.dishName()),
                            dk -> dk,
                            (a, b) -> a
                    ));
            log.info("DishRetriever 已加载 {} 道菜品知识", knowledgeMap.size());
        } catch (Exception e) {
            log.error("加载菜品知识数据失败", e);
        }
    }

    /**
     * 根据菜品名列表检索菜品知识。
     * <p>
     * 匹配策略：
     * 1. 先精确匹配（O(1) HashMap 查找）
     * 2. 未命中时，去掉两边的「中文括号及内容」再次匹配
     *
     * @param dishNames 菜品名列表（来自 OrderData.items 的 name）
     * @return 匹配到的菜品知识列表，未匹配的跳过（不报错）
     */
    public List<DishKnowledge> retrieve(List<String> dishNames) {
        return dishNames.stream()
                .map(name -> {
                    // 尝试精确匹配
                    DishKnowledge result = knowledgeMap.get(name.trim());
                    if (result != null) return result;
                    // 精确匹配不到时，去掉括号内容再试（支持全角半角）
                    String stripped = stripParentheses(name);
                    if (!stripped.equals(name.trim())) {
                        result = strippedNameMap.get(stripped);
                        if (result != null) {
                            log.debug("去括号匹配: '{}' → '{}'", name, result.dishName());
                        }
                    }
                    return result;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 去除菜名中的中文括号及内容，如「打抛饭（不可免辣）」→「打抛饭」 */
    /** 去除菜名中的括号及内容（支持全角（）和半角()），如「打抛饭(不可免辣)」→「打抛饭」 */
    private String stripParentheses(String name) {
        return name.replaceAll("[（(][^）)]*[）)]", "").trim();
    }
}
