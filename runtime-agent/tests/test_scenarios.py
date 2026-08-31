"""Agent 测试场景定义。

每个场景定义用户输入和期望 Agent 行为，
用于手动（或未来自动）验证 Agent 决策逻辑的正确性。

使用方式：
    python tests/test_scenarios.py
"""

TEST_SCENARIOS = [
    {
        "id": 1,
        "name": "天气触发",
        "user_input": "今天感觉特别闷",
        "expected_topic": "weather_chitchat",
        "expects_tool_call": True,
        "description": "用户表达体感不适 → Agent 应调用天气工具",
    },
    {
        "id": 2,
        "name": "菜品追问",
        "user_input": "那个牛肉粉为什么汤这么浓",
        "expected_topic": "dish",
        "expects_tool_call": True,
        "description": "用户对菜品提问 → Agent 应调用菜品搜索工具",
    },
    {
        "id": 3,
        "name": "纯聊天",
        "user_input": "今天挺开心的",
        "expected_topic": "dining_scene",
        "expects_tool_call": False,
        "description": "用户分享感受 → Agent 应自然延伸，不调工具",
    },
    {
        "id": 4,
        "name": "餐厅问询",
        "user_input": "他们还有别的店吗",
        "expected_topic": "restaurant_info",
        "expects_tool_call": True,
        "description": "用户询问其他门店 → Agent 应调用餐厅工具",
    },
    {
        "id": 5,
        "name": "其他话题兜底",
        "user_input": "对了，我今天在路上遇到一件有意思的事",
        "expected_topic": "other",
        "expects_tool_call": False,
        "description": "话题不在枚举中 → Agent 走兜底策略，不调用工具",
    },
]


def print_scenarios():
    """打印所有测试场景供人工验证。"""
    print("=" * 60)
    print("  Moka Runtime Agent — 测试场景清单")
    print("=" * 60)
    for scene in TEST_SCENARIOS:
        print(f"\n[{scene['id']}] {scene['name']}")
        print(f"  用户输入：\"{scene['user_input']}\"")
        print(f"  预期 topic：{scene['expected_topic']}")
        print(f"  预期工具调用：{'是' if scene['expects_tool_call'] else '否'}")
        print(f"  说明：{scene['description']}")
    print("\n" + "=" * 60)
    print("共 {} 个测试场景".format(len(TEST_SCENARIOS)))


if __name__ == "__main__":
    print_scenarios()
