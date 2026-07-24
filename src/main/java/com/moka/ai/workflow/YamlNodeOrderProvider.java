package com.moka.ai.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从 YAML 配置文件读取节点顺序。
 * <p>
 * 绑定 moka.workflow.nodes 配置项（定义在 moka-workflow.yml 中）。
 * 仅在 moka.llm.mock=false 时生效（Real 模式）。
 * <p>
 * 使用 @ConfigurationProperties 而非 @Value，因为 YAML 列表在 Environment 中
 * 存储为 moka.workflow.nodes[0]、moka.workflow.nodes[1] ... 形式，
 * @ConfigurationProperties 能正确绑定为 List，而 @Value("${...}") 无法直接解析列表。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
@ConfigurationProperties(prefix = "moka.workflow")
public class YamlNodeOrderProvider implements NodeOrderProvider {

    private List<String> nodes = List.of();

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    @Override
    public List<String> getNodeOrder() {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException(
                    "moka.workflow.nodes 配置为空，请在 moka-workflow.yml 中配置节点列表");
        }
        return nodes;
    }
}
