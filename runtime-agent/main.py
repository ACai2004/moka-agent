"""Moka Runtime Agent — FastAPI 入口。

提供两个核心端点：
- POST /api/v1/agent/session/create  创建会话（接收 Java Pre-call Context）
- POST /api/v1/agent/session/{id}/chat  每轮对话
"""

import uuid
from typing import Dict, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from graph.graph import agent_graph
from graph.state import AgentState

app = FastAPI(title="Moka Runtime Agent")

# ========== 内存 Session 存储（MVP 阶段，重启即丢失） ==========
sessions: Dict[str, AgentState] = {}


# ========== 请求 / 响应模型 ==========

class CreateSessionRequest(BaseModel):
    pre_call_context: dict


class CreateSessionResponse(BaseModel):
    session_id: str
    status: str


class ChatRequest(BaseModel):
    user_message: str
    conversation_history: Optional[list] = None
    wrap_up: Optional[bool] = False   # True 表示让 Agent 收尾（达到最大轮数）


class ChatResponse(BaseModel):
    response: str
    tool_calls: list
    state_update: dict
    end_call: bool = False            # Agent 判断是否该结束通话


# ========== API 端点 ==========

@app.post("/api/v1/agent/session/create")
async def create_session(req: CreateSessionRequest):
    """创建 Agent 会话，初始化 LangGraph State。"""
    session_id = str(uuid.uuid4())

    sessions[session_id] = {
        "pre_call_context": req.pre_call_context,
        "messages": [],
        "current_topic": "greeting",
        "mentioned_dishes": [],
        "user_engagement": "medium",
        "user_sentiment": None,
        "need_tool": False,
        "force_end": False,
        "tool_calls": [],
        "response": None,
        "end_call": False,
    }

    return CreateSessionResponse(session_id=session_id, status="created")


@app.post("/api/v1/agent/session/{session_id}/chat")
async def chat(session_id: str, req: ChatRequest):
    """每轮对话：传入用户消息，LangGraph 执行决策循环，返回回复。"""
    if session_id not in sessions:
        raise HTTPException(status_code=404, detail="Session not found")

    state = sessions[session_id]

    # 追加用户消息到对话历史
    state["messages"].append({"role": "user", "content": req.user_message})

    # 外层护栏触发收尾：让 Agent 生成告别语
    if req.wrap_up:
        state["force_end"] = True

    # 执行 LangGraph 决策循环
    result = agent_graph.invoke(state)

    # 把 AI 回复写回对话历史（下一轮 LLM 能看到自己说过的话，保持连贯）
    ai_response = result.get("response", "")
    if ai_response:
        result["messages"].append({"role": "assistant", "content": ai_response})

    # 更新 Session 状态
    sessions[session_id] = result

    return ChatResponse(
        response=result.get("response", ""),
        tool_calls=result.get("tool_calls", []),
        state_update={
            "current_topic": result.get("current_topic"),
            "user_engagement": result.get("user_engagement"),
        },
        end_call=result.get("end_call", False),
    )


@app.get("/api/v1/agent/health")
async def health():
    """健康检查。"""
    return {"status": "ok", "sessions": len(sessions)}


# ========== 启动入口 ==========

if __name__ == "__main__":
    import uvicorn
    from config import AGENT_HOST, AGENT_PORT

    uvicorn.run(app, host=AGENT_HOST, port=AGENT_PORT)
