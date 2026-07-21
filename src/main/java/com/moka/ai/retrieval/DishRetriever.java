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
            log.info("DishRetriever 已加载 {} 道菜品知识", knowledgeMap.size());
        } catch (Exception e) {
            log.error("加载菜品知识数据失败", e);
        }
    }

    /**
     * 根据菜品名列表检索菜品知识。
     *
     * @param dishNames 菜品名列表（来自 OrderData.items 的 name）
     * @return 匹配到的菜品知识列表，未匹配的跳过（不报错）
     */
    public List<DishKnowledge> retrieve(List<String> dishNames) {
        return dishNames.stream()
                .map(name -> knowledgeMap.get(name.trim()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
