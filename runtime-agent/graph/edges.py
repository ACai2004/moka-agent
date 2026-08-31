"""条件路由函数。

定义 LangGraph 图中各节点的连接逻辑。
"""

from typing import Literal

from .state import AgentState


def route_after_intent(state: AgentState) -> Literal["tool_execution", "response_generation"]:
    """意图识别后的路由决策。

    根据 intent_detection 节点产出的 need_tool 标记决定下一步：
    - need_tool=True  → 走 tool_execution 节点
    - need_tool=False → 直接走 response_generation 节点
    """
    if state.get("need_tool", False):
        return "tool_execution"
    return "response_generation"
