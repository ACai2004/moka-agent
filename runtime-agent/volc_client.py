"""
火山引擎豆包语音客户端（自实现，使用新版 X-Api-Key 认证）。

封装豆包流式语音识别模型 2.0 的 WebSocket 二进制协议。
区别于 doubao-speech 库（其内部用旧版 APP ID + Access Token 认证），
本客户端使用新版控制台的 API Key 认证，已在官方端点验证通过。

用法：
    from volc_client import asr_transcribe, tts_synthesize

    text = asr_transcribe("录音.wav")          # 语音 → 文字
    tts_synthesize("你好", "输出.mp3")         # 文字 → 语音
"""

import asyncio
import gzip
import io
import json
import os
import struct
import uuid
import wave
from enum import IntEnum
from pathlib import Path

import websockets

# ============================================================
# 配置（从环境变量读取，代码里不硬编码）
# ============================================================

VOLC_API_KEY = os.environ.get(
    "VOLCENGINE_API_KEY",
    os.environ.get("DOUBAO_API_KEY", ""),
)
ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"
TTS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"
ASR_RESOURCE_ID = "volc.seedasr.sauc.duration"
TTS_RESOURCE_ID = "seed-tts-2.0"
TTS_SPEAKER = "zh_female_vv_uranus_bigtts"  # seed-tts-2.0 兼容音色
TTS_SAMPLE_RATE = 24000


# ============================================================
# 协议常量
# ============================================================

class MsgType(IntEnum):
    """帧类型。"""
    FullClientRequest = 0b0001      # 完整客户端请求（metadata/JSON）
    AudioOnlyClient = 0b0010        # 纯音频帧
    FullServerResponse = 0b1001     # 完整服务端响应（结果 JSON）
    AudioOnlyServer = 0b1011        # 服务端音频帧
    FrontEndResultServer = 0b1100   # 前端结果
    Error = 0b1111                  # 错误


class Flags(IntEnum):
    """帧标志。"""
    NoSeq = 0b0000
    PositiveSeq = 0b0001            # 正序号（普通帧）
    LastNoSeq = 0b0010              # 最后一帧（无序号）
    NegativeSeq = 0b0011            # 负序号（ASR 末包）
    WithEvent = 0b0100              # 带事件（TTS 用）


class Serialization(IntEnum):
    """载荷序列化。"""
    Raw = 0
    JSON = 0b0001


class Compression(IntEnum):
    """载荷压缩。"""
    None_ = 0
    Gzip = 0b0001


class Event(IntEnum):
    """TTS 事件类型。"""
    None_ = 0
    # 上游连接
    StartConnection = 1
    FinishConnection = 2
    # 下游连接
    ConnectionStarted = 50
    ConnectionFailed = 51
    ConnectionFinished = 52
    # 上游会话
    StartSession = 100
    CancelSession = 101
    FinishSession = 102
    # 下游会话
    SessionStarted = 150
    SessionCanceled = 151
    SessionFinished = 152
    SessionFailed = 153
    # 上游通用
    TaskRequest = 200
    UpdateConfig = 201
    # 下游 TTS
    TTSSentenceStart = 350
    TTSSentenceEnd = 351
    TTSResponse = 352
    TTSEnded = 359


# 连接事件不携带 session_id（服务端返回时也没有）
_CONNECTION_EVENTS = frozenset({
    Event.StartConnection,
    Event.FinishConnection,
    Event.ConnectionStarted,
    Event.ConnectionFailed,
    Event.ConnectionFinished,
})


# ============================================================
# 二进制帧编解码
# ============================================================

def _marshal_frame(
    msg_type: MsgType,
    flag: Flags,
    payload: bytes,
    *,
    sequence: int | None = None,
    serialization: Serialization = Serialization.JSON,
    compression: Compression = Compression.None_,
    event: Event | None = None,
    session_id: str = "",
) -> bytes:
    """将消息编码为二进制帧。

    - ASR 用 PositiveSeq/NegativeSeq + Gzip 压缩载荷
    - TTS 用 WithEvent 标志 + 事件号 + session_id + 明文 JSON 载荷
    """
    buf = io.BytesIO()
    buf.write(
        bytes([
            (1 << 4) | 1,                       # version=1, header_size=1 → 4字节头
            (int(msg_type) << 4) | int(flag),   # 类型 + 标志
            (int(serialization) << 4) | int(compression),
            0x00,
        ])
    )

    # WithEvent 标志：先写事件号，再写 session_id（连接事件除外）
    if flag == Flags.WithEvent:
        buf.write(struct.pack(">i", int(event or Event.None_)))
        if event not in _CONNECTION_EVENTS:
            sid = session_id.encode("utf-8")
            buf.write(struct.pack(">I", len(sid)))
            if sid:
                buf.write(sid)

    # 带序号的帧追加 4 字节大端序号（WithEvent 帧不写序号）
    if sequence is not None and flag in (Flags.PositiveSeq, Flags.NegativeSeq):
        buf.write(struct.pack(">i", sequence))

    # 载荷长度 + 载荷
    buf.write(struct.pack(">I", len(payload)))
    if payload:
        buf.write(payload)
    return buf.getvalue()


def _unmarshal_frame(data: bytes) -> dict:
    """解析服务端返回的二进制帧，返回关键信息。"""
    if len(data) < 4:
        raise ValueError(f"帧太短: {len(data)} bytes")

    header_size = (data[0] & 0x0F) * 4
    if header_size < 4:
        raise ValueError(f"非法 header_size: {header_size}")

    msg_type = MsgType(data[1] >> 4)
    flag = Flags(data[1] & 0x0F)
    serialization = Serialization(data[2] >> 4)
    compression = Compression(data[2] & 0x0F)

    buf = io.BytesIO(data[header_size:])

    sequence = None
    event = None
    if flag == Flags.WithEvent:
        ev_bytes = buf.read(4)
        if ev_bytes:
            event = Event(struct.unpack(">i", ev_bytes)[0])
        if event not in _CONNECTION_EVENTS:
            # 非连接事件：读 session_id（len + bytes）
            sid_size_bytes = buf.read(4)
            if sid_size_bytes:
                sid_size = struct.unpack(">I", sid_size_bytes)[0]
                if sid_size:
                    buf.read(sid_size)  # session_id 跳过，TTS 不用
    elif flag in (Flags.PositiveSeq, Flags.NegativeSeq):
        seq_bytes = buf.read(4)
        if seq_bytes:
            sequence = struct.unpack(">i", seq_bytes)[0]

    size_bytes = buf.read(4)
    payload = b""
    if size_bytes:
        size = struct.unpack(">I", size_bytes)[0]
        if size:
            payload = buf.read(size)

    # 解压 / 解码载荷
    raw = payload
    if compression == Compression.Gzip and raw:
        raw = gzip.decompress(raw)
    body = None
    if serialization == Serialization.JSON and raw:
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            body = raw.decode("utf-8", errors="replace")

    return {
        "type": msg_type,
        "flag": flag,
        "sequence": sequence,
        "event": event,
        "payload": raw,
        "body": body,
    }


# ============================================================
# ASR：语音 → 文字
# ============================================================

def _read_wav_as_pcm16(wav_path: str, target_rate: int = 16000) -> bytes:
    """读取 WAV 文件，返回 PCM16 单声道数据。"""
    with wave.open(wav_path, "rb") as w:
        channels = w.getnchannels()
        sampwidth = w.getsampwidth()
        rate = w.getframerate()
        frames = w.readframes(w.getnframes())

    # 火山要求：16kHz / 单声道 / 16-bit PCM
    # 如果录音时已按要求保存（16kHz/单声道/16bit），直接返回
    if channels == 1 and sampwidth == 2 and rate == target_rate:
        return frames

    # 不满足时通过 wave 转换（本项目录音脚本已按要求保存，此处兜底）
    raise ValueError(
        f"WAV 格式不符，需 {target_rate}Hz/单声道/16bit，"
        f"当前 {rate}Hz/{channels}声道/{sampwidth*8}bit"
    )


async def _asr_transcribe_async(wav_path: str, timeout: float = 60.0) -> str:
    """核心 ASR 实现：连接 → 发送 metadata → 发送音频 → 接收结果。"""
    if not VOLC_API_KEY:
        raise RuntimeError(
            "缺少 API Key，请设置环境变量 VOLCENGINE_API_KEY 或 DOUBAO_API_KEY"
        )

    request_id = str(uuid.uuid4())
    headers = {
        "X-Api-Key": VOLC_API_KEY,
        "X-Api-Resource-Id": ASR_RESOURCE_ID,
        "X-Api-Request-Id": request_id,
        "X-Api-Sequence": "-1",
    }

    # 读取音频为 PCM16
    audio_bytes = _read_wav_as_pcm16(wav_path)

    # metadata 载荷
    init_payload = {
        "user": {"uid": request_id},
        "audio": {
            "format": "pcm",
            "codec": "raw",
            "rate": 16000,
            "bits": 16,
            "channel": 1,
        },
        "request": {
            "model_name": "bigmodel",
            "enable_itn": True,
            "enable_punc": True,
            "enable_ddc": True,
            "show_utterances": True,
            "enable_nonstream": False,
        },
    }
    init_bytes = gzip.compress(json.dumps(init_payload, ensure_ascii=False).encode("utf-8"))

    # 音频分包：每包 200ms → 16000 * 2 * 0.2 = 6400 bytes
    segment_bytes = 6400
    chunks = [
        audio_bytes[i : i + segment_bytes]
        for i in range(0, len(audio_bytes), segment_bytes)
    ]
    if not chunks:
        chunks = [b""]

    try:
        ws = await websockets.connect(
            ASR_URL,
            additional_headers=headers,
            max_size=16 * 1024 * 1024,
            open_timeout=15,
            ping_interval=20,
            ping_timeout=20,
        )
    except Exception as e:
        raise RuntimeError(f"ASR WebSocket 连接失败: {e}") from e

    transcript = ""

    try:
        # 1. 发送 metadata（seq=1，Gzip 压缩）
        seq = 1
        await ws.send(_marshal_frame(
            MsgType.FullClientRequest, Flags.PositiveSeq,
            init_bytes, sequence=seq,
            compression=Compression.Gzip,
        ))
        seq += 1

        # 2. 发送音频帧（普通帧正序，最后一帧负序，Gzip 压缩）
        for i, chunk in enumerate(chunks):
            is_last = (i == len(chunks) - 1)
            payload = gzip.compress(chunk) if chunk else b""
            if is_last:
                await ws.send(_marshal_frame(
                    MsgType.AudioOnlyClient, Flags.NegativeSeq,
                    payload, sequence=-seq,
                    compression=Compression.Gzip,
                ))
            else:
                await ws.send(_marshal_frame(
                    MsgType.AudioOnlyClient, Flags.PositiveSeq,
                    payload, sequence=seq,
                    compression=Compression.Gzip,
                ))
                seq += 1

        # 3. 接收识别结果
        from websockets.exceptions import ConnectionClosedOK, ConnectionClosedError

        while True:
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
            except ConnectionClosedOK:
                # 服务端正常关闭（可能已发送结果或检测到无声）
                break
            except ConnectionClosedError as e:
                raise RuntimeError(f"ASR 连接异常关闭: {e}") from e

            frame = _unmarshal_frame(raw)

            if frame["type"] == MsgType.Error:
                raise RuntimeError(f"ASR 服务端错误: {frame['body']}")

            if frame["type"] == MsgType.FullServerResponse and isinstance(frame["body"], dict):
                result = frame["body"].get("result") or {}
                text = result.get("text", "")
                if text:
                    transcript = text
                is_final = bool(result.get("is_final", False)) or (
                    frame["flag"] == Flags.LastNoSeq
                )
                if is_final:
                    break

    except asyncio.TimeoutError as e:
        raise RuntimeError(f"ASR 等待超时: {e}") from e
    finally:
        try:
            await ws.close()
        except Exception:
            pass

    return transcript.strip()


def asr_transcribe(wav_path: str, timeout: float = 60.0) -> str:
    """语音识别：录音 WAV 文件 → 文字（同步封装）。"""
    return asyncio.run(_asr_transcribe_async(wav_path, timeout))


# ============================================================
# TTS：文字 → 语音
# ============================================================

async def _tts_synthesize_async(
    text: str,
    audio_format: str = "mp3",
    timeout: float = 60.0,
) -> bytes:
    """核心 TTS 实现：连接 → StartConnection → StartSession → TaskRequest → 收音频。

    返回原始音频字节（audio_format 决定是 mp3 还是 pcm）。
    """
    if not VOLC_API_KEY:
        raise RuntimeError(
            "缺少 API Key，请设置环境变量 VOLCENGINE_API_KEY 或 DOUBAO_API_KEY"
        )

    request_id = str(uuid.uuid4())
    session_id = str(uuid.uuid4())
    headers = {
        "X-Api-Key": VOLC_API_KEY,
        "X-Api-Resource-Id": TTS_RESOURCE_ID,
        "X-Api-Request-Id": request_id,
        "X-Api-Sequence": "-1",
    }

    # TTS 基础参数（speech_rate 为整数百分比增量）
    req_params = {
        "speaker": TTS_SPEAKER,
        "audio_params": {
            "format": audio_format,
            "sample_rate": TTS_SAMPLE_RATE,
            "speech_rate": 0,
        },
    }
    base_payload = {
        "user": {"uid": request_id},
        "namespace": "BidirectionalTTS",
        "req_params": req_params,
    }

    try:
        ws = await websockets.connect(
            TTS_URL,
            additional_headers=headers,
            max_size=16 * 1024 * 1024,
            open_timeout=15,
            ping_interval=20,
            ping_timeout=20,
        )
    except Exception as e:
        raise RuntimeError(f"TTS WebSocket 连接失败: {e}") from e

    audio_chunks = []

    try:
        # 1. StartConnection
        await ws.send(_marshal_frame(
            MsgType.FullClientRequest, Flags.WithEvent,
            b"{}", event=Event.StartConnection,
        ))
        msg = await _recv_frame(ws, timeout)
        if msg.get("event") != Event.ConnectionStarted:
            raise RuntimeError(f"期望 ConnectionStarted，实际: {msg.get('event')}")

        # 2. StartSession（携带 speaker 等参数）
        start_payload = dict(base_payload, event=int(Event.StartSession))
        await ws.send(_marshal_frame(
            MsgType.FullClientRequest, Flags.WithEvent,
            json.dumps(start_payload, ensure_ascii=False).encode("utf-8"),
            event=Event.StartSession, session_id=session_id,
        ))
        msg = await _recv_frame(ws, timeout)
        if msg.get("event") != Event.SessionStarted:
            raise RuntimeError(f"期望 SessionStarted，实际: {msg.get('event')}")

        # 3. TaskRequest（携带文本）
        task_payload = dict(base_payload, event=int(Event.TaskRequest))
        task_payload["req_params"] = dict(req_params, text=text)
        await ws.send(_marshal_frame(
            MsgType.FullClientRequest, Flags.WithEvent,
            json.dumps(task_payload, ensure_ascii=False).encode("utf-8"),
            event=Event.TaskRequest, session_id=session_id,
        ))

        # 4. FinishSession（通知服务端文本发送完毕）
        await ws.send(_marshal_frame(
            MsgType.FullClientRequest, Flags.WithEvent,
            b"{}", event=Event.FinishSession, session_id=session_id,
        ))

        # 5. 接收音频直到结束
        session_done = False
        while True:
            msg = await _recv_frame(ws, timeout)
            if msg.get("type") == MsgType.AudioOnlyServer and msg.get("payload"):
                audio_chunks.append(msg["payload"])
            elif msg.get("type") == MsgType.FullServerResponse:
                ev = msg.get("event")
                if ev in (Event.TTSEnded, Event.SessionFinished):
                    if ev == Event.SessionFinished:
                        session_done = True
                    break
                # TTSResponse / SentenceStart / SentenceEnd 是进度帧，忽略

        # 6. FinishConnection
        if not session_done:
            await ws.send(_marshal_frame(
                MsgType.FullClientRequest, Flags.WithEvent,
                b"{}", event=Event.FinishConnection,
            ))
            try:
                await _recv_frame(ws, timeout)
            except Exception:
                pass

    except asyncio.TimeoutError as e:
        raise RuntimeError(f"TTS 等待超时: {e}") from e
    finally:
        try:
            await ws.close()
        except Exception:
            pass

    if not audio_chunks:
        raise RuntimeError("TTS 未返回任何音频数据")

    # 拼接所有音频块返回原始字节
    return b"".join(audio_chunks)


async def _recv_frame(ws, timeout: float) -> dict:
    """接收一帧并解析。"""
    from websockets.exceptions import ConnectionClosed

    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
    except ConnectionClosed as e:
        raise RuntimeError(f"连接关闭: {e}") from e
    frame = _unmarshal_frame(raw)
    if frame["type"] == MsgType.Error:
        raise RuntimeError(f"TTS 服务端错误: {frame['body']}")
    return frame


def tts_synthesize(
    text: str,
    output_path: str,
    audio_format: str = "mp3",
    timeout: float = 60.0,
) -> str:
    """文字转语音：写入音频文件，返回文件路径。"""
    data = asyncio.run(_tts_synthesize_async(text, audio_format, timeout))
    with open(output_path, "wb") as f:
        f.write(data)
    return output_path


def tts_synthesize_pcm(text: str, timeout: float = 60.0) -> bytes:
    """文字转语音：返回 PCM16 裸音频字节（24000Hz 单声道，可直接用 PyAudio 播放）。"""
    return asyncio.run(_tts_synthesize_async(text, "pcm", timeout))


if __name__ == "__main__":
    print(f"API Key: {VOLC_API_KEY[:8] if VOLC_API_KEY else '(未设置)'}...")
    print(f"ASR URL: {ASR_URL}")
    print(f"TTS URL: {TTS_URL}")
