"""
Moka Agent - 完整语音对话循环（形态 B：完整产品闭环）
========================================
流程：
  1. [可选] 上传小票照片 → Java Pre-call 生成真实 Context
  2. 录音 → 火山ASR → Agent API → 火山TTS → PyAudio播放
========================================
运行：
    conda activate moka-agent
    python voice_main.py [小票照片路径]    # 可选：提供照片走真实 OCR

使用：
    1. 启动后按 Enter 开始说话
    2. 说完再按 Enter 结束录音
    3. Agent 回复会通过扬声器朗读
    4. 说"退出"或"结束"退出

说明：
    启动时会调用 Java 后端 /api/v1/calls/context 获取真实 Pre-call Context；
    Java 不可用时回退到内置示例数据。
    传入小票照片路径时，会把照片一并传给 Java 做识别（需 real 模式）。

依赖：PyAudio / httpx / websockets（已在 moka-agent 环境）
"""

import base64
import json
import os
import sys
import tempfile
import wave

# API Key（必须从环境变量读，代码里不硬编码）
# 请在 .env 里配置 VOLCENGINE_API_KEY，或用 .env 加载
if not os.environ.get("VOLCENGINE_API_KEY"):
    print("⚠️ 未设置 VOLCENGINE_API_KEY，请检查 .env 配置")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import httpx
import pyaudio
from volc_client import asr_transcribe, tts_synthesize_pcm

# ===================== 配置 =====================

AGENT_API_URL = "http://localhost:8000"
JAVA_BACKEND_URL = "http://localhost:8081"   # Java Tool + Pre-call Context 服务

# 录音参数（16kHz 单声道 16bit，火山 ASR 要求）
RECORD_RATE = 16000
RECORD_CHANNELS = 1
RECORD_CHUNK = 1024
RECORD_SECONDS = 5

# TTS 播放参数（火山 TTS PCM 输出）
TTS_RATE = 24000
TTS_CHANNELS = 1

# 退出关键词
EXIT_WORDS = ("退出", "结束", "再见", "拜拜", "bye", "exit")

# 主动结束通话的双护栏
MIN_TURNS_BEFORE_END = 5    # 最少 5 轮才允许 AI 主动结束（防过早告别）
MAX_TURNS = 15              # 最多 15 轮，超过强制收尾（防无限聊）

# 告别语检测兜底：AI 回复包含这些词 → 视为结束通话
FAREWELL_PHRASES = ("再见", "拜拜", "聊到这儿", "不打扰", "先这样", "不耽误", "祝您")

# 兜底 Pre-call Context（Java 不可用时使用）
FALLBACK_CONTEXT = {
    "order": {
        "restaurant": "売泰",
        "time": "周五 19:20",
        "people": 3,
    },
    "dishes": [
        {
            "dishName": "打抛饭（不可免辣）",
            "dishRole": "SIGNATURE",
            "features": ["经典泰式", "酸辣开胃"],
            "experienceTags": ["招牌必点"],
        }
    ],
    "realtime": {
        "weather": "多云 31°C",
        "holiday": "端午节",
    },
    "plan": {
        "directions": ["先聊整体感受"],
        "availableHooks": ["打抛饭的辣度"],
        "avoid": ["不逐菜询问"],
    },
}


# ===================== 功能函数 =====================

def fetch_context_from_java(photo_path: str | None = None) -> dict | None:
    """获取真实 Pre-call Context。

    优先级：
    1. 读取已保存的 real_context.json（跳过慢速 OCR，秒开）
    2. 调用 Java /context（真实模式 OCR+DeepSeek 需约 150-300 秒，超时 300s）
    3. 都失败返回 None（调用方回退到 FALLBACK_CONTEXT）
    """
    # 1. 优先读缓存（跳过慢 OCR）
    cache_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "real_context.json")
    if os.path.exists(cache_path):
        try:
            with open(cache_path, "r", encoding="utf-8") as f:
                ctx = json.load(f)
            if ctx.get("order"):
                order = ctx["order"]
                print(f"  📂 读取已保存的真实 Context")
                print(f"     餐厅: {order.get('restaurant')} | 人数: {order.get('people')}")
                return ctx
        except Exception as e:
            print(f"  ⚠️ 读取 real_context.json 失败: {e}")

    # 2. 调 Java（真实模式可能很慢）
    try:
        payload = {}
        if photo_path and os.path.exists(photo_path):
            with open(photo_path, "rb") as f:
                payload["photoBase64"] = base64.b64encode(f.read()).decode("utf-8")

        resp = httpx.post(
            f"{JAVA_BACKEND_URL}/api/v1/calls/context",
            json=payload,
            timeout=300,   # 真实模式 OCR+DeepSeek 需要约 150-300 秒
        )
        if resp.status_code == 200:
            return resp.json()
        print(f"  ⚠️ Java 返回状态 {resp.status_code}，使用兜底 Context")
        return None
    except Exception as e:
        print(f"  ⚠️ Java 不可用（{e}），使用兜底 Context")
        return None

def _compute_rms(data: bytes) -> float:
    """计算 PCM16 音频块的 RMS 能量（用于静音检测）。"""
    import array
    samples = array.array("h", data)
    if not samples:
        return 0.0
    sum_sq = sum(s * s for s in samples)
    return (sum_sq / len(samples)) ** 0.5


def record_to_wav(path: str, max_seconds: int = 20, silence_ms: int = 700) -> None:
    """从麦克风录音，带静音检测自动停录。

    - 启动时校准环境噪音，动态设定语音阈值
    - 检测到连续 silence_ms 静音 → 自动结束录音
    - 最长 max_seconds 秒兜底（防止一直检测不到静音）
    """
    p = pyaudio.PyAudio()
    stream = p.open(
        format=pyaudio.paInt16,
        channels=RECORD_CHANNELS,
        rate=RECORD_RATE,
        input=True,
        frames_per_buffer=RECORD_CHUNK,
    )

    # 校准环境噪音（0.5 秒），动态设定语音阈值
    print("  校准环境噪音...", end="", flush=True)
    calib_frames = int(RECORD_RATE / RECORD_CHUNK * 0.5)
    noise_vals = []
    for _ in range(calib_frames):
        data = stream.read(RECORD_CHUNK, exception_on_overflow=False)
        noise_vals.append(_compute_rms(data))
    ambient = sum(noise_vals) / len(noise_vals) if noise_vals else 0
    threshold = max(ambient * 2.5 + 100, 300)   # 至少 300，防环境太安静阈值过低
    print(f" 噪音 {ambient:.0f} → 语音阈值 {threshold:.0f}")

    # 开始录音
    print("🎤 请说话...（说完自动停止）", end="", flush=True)
    frames = []
    speech_started = False
    silence_count = 0
    chunk_ms = RECORD_CHUNK / RECORD_RATE * 1000   # 每个 chunk 的毫秒数
    silence_chunks_needed = max(1, int(silence_ms / chunk_ms))
    max_chunks = int(RECORD_RATE / RECORD_CHUNK * max_seconds)

    while len(frames) < max_chunks:
        data = stream.read(RECORD_CHUNK, exception_on_overflow=False)
        rms = _compute_rms(data)
        if rms > threshold:
            speech_started = True
            silence_count = 0
            frames.append(data)
            print(".", end="", flush=True)
        elif speech_started:
            silence_count += 1
            frames.append(data)
            if silence_count >= silence_chunks_needed:
                break
        # 未开始说话时的静音不记录（等用户开口）

    print(" 完成")
    stream.stop_stream()
    stream.close()
    p.terminate()

    with wave.open(path, "wb") as wf:
        wf.setnchannels(RECORD_CHANNELS)
        wf.setsampwidth(pyaudio.PyAudio().get_sample_size(pyaudio.paInt16))
        wf.setframerate(RECORD_RATE)
        wf.writeframes(b"".join(frames))


def play_pcm(pcm_data: bytes) -> None:
    """用 PyAudio 播放 PCM 音频（24000Hz 单声道 16bit）。"""
    p = pyaudio.PyAudio()
    stream = p.open(
        format=pyaudio.paInt16,
        channels=TTS_CHANNELS,
        rate=TTS_RATE,
        output=True,
    )

    # 分块写入，避免一次写太多阻塞
    CHUNK = 4096
    for i in range(0, len(pcm_data), CHUNK):
        stream.write(pcm_data[i : i + CHUNK])

    stream.stop_stream()
    stream.close()
    p.terminate()


# ===================== 主循环 =====================

def main():
    # 可选参数：小票照片路径
    photo_path = sys.argv[1] if len(sys.argv) > 1 else None

    print("=" * 50)
    print("  Moka Agent - 语音对话")
    print("  说\"退出\"或\"结束\"可退出")
    print("=" * 50)

    # 检查 Agent 服务
    client = httpx.Client(base_url=AGENT_API_URL, timeout=30)
    try:
        health = client.get("/api/v1/agent/health")
        if health.status_code != 200:
            print("❌ Agent 服务不可用，请先启动 runtime-agent")
            return
    except Exception:
        print("❌ Agent 服务不可达，请先启动 runtime-agent")
        return

    # 从 Java 获取真实 Pre-call Context（失败则用兜底）
    print("\n正在从 Java 获取真实用餐上下文...")
    pre_call_context = fetch_context_from_java(photo_path)
    if pre_call_context:
        order = pre_call_context.get("order", {})
        print(f"  ✅ 使用 Java 生成的真实 Context")
        print(f"     餐厅: {order.get('restaurant')} | 人数: {order.get('people')}")
        items = order.get("items", [])
        if items:
            names = [i.get("name") for i in items]
            print(f"     菜品: {'、'.join(names)}")
        realtime = pre_call_context.get("realtime", {})
        if realtime.get("weather"):
            print(f"     天气: {realtime['weather']}")
    else:
        pre_call_context = FALLBACK_CONTEXT
        print("  ⚠️ 使用内置兜底 Context")

    # 创建会话
    resp = client.post(
        "/api/v1/agent/session/create",
        json={"pre_call_context": pre_call_context},
    )
    session_id = resp.json()["session_id"]
    print(f"会话已创建: {session_id[:8]}...")

    print("\n准备好了！按 Enter 开始说话（说完再按 Enter）")
    print("-" * 50)

    tmp_wav = os.path.join(tempfile.gettempdir(), "moka_voice_input.wav")

    turn_count = 0

    try:
        while True:
            # 1. 等待用户按回车开始
            input("\n>> 按 Enter 开始说话...")

            # 2. 录音（静音检测自动停录）+ ASR
            record_to_wav(tmp_wav, max_seconds=20, silence_ms=700)
            print("  识别中...")
            try:
                user_text = asr_transcribe(tmp_wav)
            except Exception as e:
                print(f"  ❌ ASR 失败: {e}")
                continue

            if not user_text:
                print("  ⚠️ 未识别到内容，请再说一遍")
                continue

            print(f"\n你: {user_text}")

            # 3. 退出检测（用户主动）
            if any(w in user_text for w in EXIT_WORDS):
                print("\n结束对话 👋")
                break

            # 4. 调 Agent API（达到最大轮数时传 wrap_up 让 AI 收尾）
            try:
                payload = {"user_message": user_text}
                if turn_count >= MAX_TURNS - 1:
                    payload["wrap_up"] = True   # 最大轮数护栏：提示收尾
                resp = client.post(
                    f"/api/v1/agent/session/{session_id}/chat",
                    json=payload,
                )
                data = resp.json()
                agent_response = data["response"]
                end_call = bool(data.get("end_call", False))
            except Exception as e:
                print(f"  ❌ Agent 调用失败: {e}")
                continue

            print(f"Agent: {agent_response}")
            turn_count += 1

            # 5. TTS + 播放
            print("  合成语音...")
            try:
                pcm = tts_synthesize_pcm(agent_response)
                play_pcm(pcm)
            except Exception as e:
                print(f"  ❌ TTS/播放失败: {e}")

            # 6. AI 主动结束判定 + 告别感知护栏
            # 兜底：AI 回复包含明确告别语 → 视为结束（LLM 可能忘了设 end_call）
            has_farewell = any(p in agent_response for p in FAREWELL_PHRASES)
            if has_farewell:
                end_call = True

            # 护栏1（最少轮数）：只挡"软结束"（无明确告别语），
            # 不挡明确告别——AI 已说"不打扰您啦/祝您"，就该结束，否则用户会困惑
            if end_call and not has_farewell and turn_count < MIN_TURNS_BEFORE_END:
                print(f"  (AI 想结束但没明确告别，且轮数还少[{turn_count}]，继续聊)")
                end_call = False
            # 护栏2（最大轮数）：兜底，始终生效
            if turn_count >= MAX_TURNS:
                print(f"  (已达最大轮数 {MAX_TURNS}，结束通话)")
                end_call = True

            if end_call:
                print("\n📞 AI 主动结束了通话 👋")
                break

    except KeyboardInterrupt:
        print("\n\n结束对话 👋")
    finally:
        client.close()
        try:
            os.unlink(tmp_wav)
        except Exception:
            pass


if __name__ == "__main__":
    main()
