"""LangGraph 图组装。

将节点和边组装为可执行的编译图。
"""

from langgraph.graph import StateGraph, END

from .state import AgentState
from .nodes import intent_detection, tool_execution, response_generation
from .edges import route_after_intent


def build_graph() -> StateGraph:
    """构建并编译 Moka Runtime Agent 的 LangGraph。"""
    workflow = StateGraph(AgentState)

    # ── 注册节点 ──
    workflow.add_node("intent_detection", intent_detection)
    workflow.add_node("tool_execution", tool_execution)
    workflow.add_node("response_generation", response_generation)

    # ── 入口 ──
    workflow.set_entry_point("intent_detection")

    # ── 条件路由：意图识别 → 需要工具？→ 工具执行 / 直接回复 ──
    workflow.add_conditional_edges(
        "intent_detection",
        route_after_intent,
        {
            "tool_execution": "tool_execution",
            "response_generation": "response_generation",
        },
    )

    # ── 工具执行完成后 → 生成回复 ──
    workflow.add_edge("tool_execution", "response_generation")

    # ── 回复生成 → 结束 ──
    workflow.add_edge("response_generation", END)

    return workflow.compile()


# 全局编译好的图实例（模块加载时一次性构建）
agent_graph = build_graph()
