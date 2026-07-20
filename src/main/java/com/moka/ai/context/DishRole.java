package com.moka.ai.context;

/**
 * 菜品在用餐中的角色。
 * <p>
 * 控制 Agent 对话时对这道菜的参与程度：
 * <ul>
 *   <li>SIGNATURE — 招牌/特色菜，值得主动聊</li>
 *   <li>MAIN — 主菜，可以聊</li>
 *   <li>SIDE — 配菜/辅料，不主动提及</li>
 *   <li>STAPLE — 主食，可以聊</li>
 *   <li>DESSERT — 甜品，自然流动时提及</li>
 *   <li>DRINK — 饮品，普通不提，特色可一带而过</li>
 *   <li>CONDIMENT — 调料/小料，不提及</li>
 * </ul>
 */
public enum DishRole {
    SIGNATURE,
    MAIN,
    SIDE,
    STAPLE,
    DESSERT,
    DRINK,
    CONDIMENT
}
