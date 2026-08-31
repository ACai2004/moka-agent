"""Java Tool API 客户端。

负责调 Java 后端暴露的工具接口（天气 / 菜品搜索 / 餐厅信息）。
通过 HTTP 调用 Java 端的 ToolController。
"""

import httpx
from typing import Optional

from config import JAVA_BACKEND_URL


class JavaToolClient:
    """Java 后端 Tool API 客户端。"""

    def __init__(self, base_url: str = JAVA_BACKEND_URL):
        self.base_url = base_url
        self.client = httpx.Client(timeout=8.0)

    def get_weather(self, district: str, city: str = "北京") -> dict:
        """查询天气。

        GET /api/v1/tools/weather?district={district}&city={city}
        """
        try:
            response = self.client.get(
                f"{self.base_url}/api/v1/tools/weather",
                params={"district": district, "city": city},
            )
            return response.json()
        except Exception as e:
            return {
                "status": "degraded",
                "data": "未知",
                "errorMessage": f"天气查询失败: {e}",
            }

    def search_dish(self, name: str) -> dict:
        """搜索菜品知识。

        GET /api/v1/tools/dish-search?name={name}
        """
        try:
            response = self.client.get(
                f"{self.base_url}/api/v1/tools/dish-search",
                params={"name": name},
            )
            return response.json()
        except Exception as e:
            return {
                "status": "degraded",
                "data": [],
                "errorMessage": f"菜品搜索失败: {e}",
            }

    def get_restaurant(self, name: str) -> dict:
        """查询餐厅信息。

        GET /api/v1/tools/restaurant?name={name}
        """
        try:
            response = self.client.get(
                f"{self.base_url}/api/v1/tools/restaurant",
                params={"name": name},
            )
            return response.json()
        except Exception as e:
            return {
                "status": "degraded",
                "data": None,
                "errorMessage": f"餐厅查询失败: {e}",
            }


# 全局工具客户端实例（指向 Java 后端地址）
tool_client = JavaToolClient()
