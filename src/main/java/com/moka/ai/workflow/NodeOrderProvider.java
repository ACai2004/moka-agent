package com.moka.ai.workflow;

import java.util.List;

/**
 * 节点顺序提供者接口。
 * <p>
 * 从不同数据源读取 Workflow 节点执行顺序。
 * 当前实现从 YAML 读取，未来可从数据库读取（运营可视化页面）。
 */
public interface NodeOrderProvider {

    /**
     * 返回节点执行顺序列表（节点名列表）。
     * 例：["OrderNode", "DishNode", "RealtimeNode", ...]
     */
    List<String> getNodeOrder();
}
