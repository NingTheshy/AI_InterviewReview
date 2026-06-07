## 一、目标与约束

- **输入**：30 分钟面试录音，格式不限（建议预处理为 16kHz 单声道 WAV）。
- **ASR API**：小米 MIMO-v2.5-ASR，单次请求最大 **10 MB**（约 12 分钟音频）。
- **核心要求**：
  1. 音频自动分片，保证单片段 ≤ 10 MB。
  2. 切分点不破坏话语完整性，不造成词级断裂。
  3. 控制碎片数量，避免过多调用。
  4. 网络/服务异常时自动重试，且不导致文本重复。
  5. 无需部署大型 VAD 模型，依赖尽量轻量。

------

## 二、整体架构

```tex
原始音频文件
    │
    ▼
┌─────────────────┐
│  音频预处理      │ → 统一采样率、声道，必要时降噪
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  自适应切片器    │ ← 静音检测 + 最小/最大时长 + 重叠窗口
│  (SmartChunker) │
└────────┬────────┘
         │ 生成带 seq 的片段
         ▼
┌─────────────────┐
│  可靠传输与调度  │ ← 并发窗口、超时重传、seq 去重
│  (ReliableSender)│
└────────┬────────┘
         │ 获取每段文本
         ▼
┌─────────────────┐
│  文本拼接与去重  │ ← 利用重叠区去除重复内容
│  (TextMerger)   │
└────────┬────────┘
         │
         ▼
     完整转写文本
```

**外部依赖**（Python）：

- `webrtcvad`（轻量语音活动检测，约 1MB，纯 C 绑定）
- `pydub`（音频读写、裁剪、重叠处理）
- `requests` 或 `aiohttp`（调用 API）
- 标准库：`wave`, `collections`, `time`, `logging`

------

## 三、详细设计

### 3.1 音频预处理

统一转换为小米 ASR 推荐格式：

- 采样率：16000 Hz
- 位深：16 bit
- 声道：单声道
- 编码：PCM WAV（或 API 支持的格式）

```python
from pydub import AudioSegment
audio = AudioSegment.from_file("interview.mp3")
audio = audio.set_frame_rate(16000).set_channels(1).set_sample_width(2)
audio.export("interview_16k.wav", format="wav")
```

### 3.2 自适应切片器（SmartChunker）

这是方案的核心，融合了**静音切分**、**最小/最大时长约束**、**重叠窗口**，避免固定大小切分和纯间隙切分的问题。

#### 3.2.1 检测原理

使用 `webrtcvad`，设置模式为 2（适中敏感度）。它会在 10/20/30 ms 帧上判断是否有人声。

#### 3.2.2 切分策略

设定三个关键参数（可调）：

- **最小静音间隙** `min_silence_duration`：2 秒
  连续静音达到这个长度，才被认为是“可切点”。
- **最小片段时长** `min_segment_duration`：30 秒
  防止因思考停顿产生过多小碎片。
- **最大片段时长** `max_segment_duration`：10 分钟（600 秒）
  防止一直无人声间隙导致超限（对应 10 MB 的安全时长，根据你的实际码率可微调）。

**逻辑流程**：

1. 逐帧读取音频，维护一个缓冲区 `buffer` 和静音连续计数器。
2. 当缓冲区的总时长 ≥ `min_segment_duration` **且** 检测到一段连续静音时长 ≥ `min_silence_duration` 时，触发切分。
3. 如果缓冲时长已经达到 `max_segment_duration`，即使没有静音，也**强制切分**。
4. 切分时，不仅输出当前 buffer 的音频，还要附加上**上一片段的尾部音频**作为上下文重叠。重叠长度取 `overlap_duration`（建议 2 秒）。
5. 输出片段时，分配全局递增的 `seq`，并记录该片段在原始时间轴上的起止时间（用于后续文本合并）。
6. 清空 buffer，保留当前片段最后 `overlap_duration` 的音频作为下一次的“上一片段尾部”。

#### 3.2.3 代码骨架

```python
import webrtcvad
from pydub import AudioSegment
import struct

class SmartChunker:
    def __init__(self, min_seg=30, max_seg=600, silence_thresh=0.5, overlap=1.5):
        self.min_seg = min_seg
        self.max_seg = max_seg
        self.silence_thresh = silence_thresh  # 秒
        self.overlap = overlap  # 秒
        self.vad = webrtcvad.Vad(2)  # 0-3 敏感度

    def chunk(self, audio_path):
        audio = AudioSegment.from_wav(audio_path)
        sample_rate = audio.frame_rate
        frame_ms = 20  # VAD 处理帧长 20ms
        frame_size = int(sample_rate * frame_ms / 1000) * 2  # 16bit 单声道字节数

        # 将音频拆成 20ms 帧
        frames = [audio[i:i+frame_ms].raw_data for i in range(0, len(audio), frame_ms)]
        # 预处理每个帧的语音标志
        is_speech = []
        for f in frames:
            if len(f) < frame_size:  # 最后不足一帧补零
                f += b'\x00' * (frame_size - len(f))
            is_speech.append(self.vad.is_speech(f, sample_rate))

        segments = []
        buffer_frames = []
        silence_len = 0.0
        last_tail_audio = AudioSegment.empty()
        seq = 0

        for i, speech in enumerate(is_speech):
            buffer_frames.append(frames[i])
            # 更新静音累计
            if not speech:
                silence_len += frame_ms / 1000.0
            else:
                silence_len = 0.0

            buffer_duration = len(buffer_frames) * frame_ms / 1000.0

            # 判断是否切分
            cut = False
            if buffer_duration >= self.min_seg and silence_len >= self.silence_thresh:
                cut = True
            elif buffer_duration >= self.max_seg:
                cut = True

            if cut:
                # 拼接 buffer 音频
                seg_audio = AudioSegment.empty()
                for frm in buffer_frames:
                    seg_audio += AudioSegment(frm, sample_width=2, frame_rate=sample_rate, channels=1)
                # 添加上一片段的尾部重叠
                if len(last_tail_audio) > 0:
                    seg_audio = last_tail_audio + seg_audio
                # 保存当前片段尾部作为下一次的重叠
                overlap_bytes = int(self.overlap * sample_rate * 2)  # 16bit单声道
                last_tail_audio = seg_audio[-overlap_bytes:] if len(seg_audio) > overlap_bytes else seg_audio

                segments.append({
                    "seq": seq,
                    "audio": seg_audio,
                    "start_time": i - len(buffer_frames) * frame_ms / 1000.0  # 粗略估计
                })
                seq += 1
                buffer_frames = []
                silence_len = 0.0

        # 处理最后剩余 buffer
        if buffer_frames:
            seg_audio = AudioSegment.empty()
            for frm in buffer_frames:
                seg_audio += AudioSegment(frm, sample_width=2, frame_rate=sample_rate, channels=1)
            if len(last_tail_audio) > 0:
                seg_audio = last_tail_audio + seg_audio
            segments.append({"seq": seq, "audio": seg_audio})
        return segments
```

**说明**：

- 精确时间对齐可用 `pydub` 的 `AudioSegment` 直接切片，上面的帧拼接仅为示意，生产代码建议直接操作 `AudioSegment` 的毫秒级切片。
- 重叠音频会略微增加片段体积，但 1.5 秒的 16kHz 16bit 单声道仅约 48KB，完全可忽略。

### 3.3 可靠传输与调度（ReliableSender）

模拟 TCP 的确认应答、超时重传、序列号去重和并发控制。

#### 3.3.1 核心机制

- **序列号** `seq`：每个片段唯一且递增。
- **已确认集合** `acked_seqs`：记录已成功接收并写入最终文本的 seq。收到响应先检查是否已存在，存在则丢弃。
- **发送窗口** `concurrency`：同时允许的最大未完成请求数，建议 1~2。
- **超时重试**：每个请求设置 `timeout`（如 30 秒），超时或返回 5xx 错误时重试，重试使用**同一个 seq**。
- **重试上限** `max_retries`：3 次。

#### 3.3.2 流程（异步推荐，同步亦可）

```python
import asyncio
import aiohttp
from collections import OrderedDict

class ReliableSender:
    def __init__(self, asr_endpoint, api_key, concurrency=2, timeout=30, max_retries=3):
        self.endpoint = asr_endpoint
        self.api_key = api_key
        self.concurrency = concurrency
        self.timeout = timeout
        self.max_retries = max_retries
        self.acked = set()
        self.results = OrderedDict()  # seq -> text

    async def send_all(self, segments):
        sem = asyncio.Semaphore(self.concurrency)
        tasks = []
        for seg in segments:
            tasks.append(self._send_with_retry(seg, sem))
        await asyncio.gather(*tasks)
        # 按 seq 排序返回文本列表
        return [self.results[seq] for seq in sorted(self.results.keys())]

    async def _send_with_retry(self, seg, sem):
        seq = seg["seq"]
        audio_bytes = seg["audio"].raw_data  # 获取 PCM 字节

        for attempt in range(1, self.max_retries + 1):
            try:
                async with sem:
                    async with aiohttp.ClientSession() as session:
                        # 构造请求，具体按照小米 API 文档
                        form = aiohttp.FormData()
                        form.add_field('file', audio_bytes, content_type='audio/wav')
                        headers = {'Authorization': f'Bearer {self.api_key}'}
                        async with session.post(self.endpoint, data=form,
                                                headers=headers,
                                                timeout=aiohttp.ClientTimeout(total=self.timeout)) as resp:
                            if resp.status == 200:
                                result = await resp.json()
                                text = result['text']  # 根据实际返回字段提取
                                # 关键：去重
                                if seq not in self.acked:
                                    self.acked.add(seq)
                                    self.results[seq] = text
                                    print(f"片段 {seq} 成功")
                                else:
                                    print(f"片段 {seq} 重复应答，已丢弃")
                                return  # 成功即退出重试
                            elif resp.status >= 500:
                                print(f"片段 {seq} 服务端错误，第 {attempt} 次重试...")
                            else:
                                # 4xx 不重试，记录错误
                                print(f"片段 {seq} 客户端错误，放弃")
                                break
            except asyncio.TimeoutError:
                print(f"片段 {seq} 超时，第 {attempt} 次重试...")
            except Exception as e:
                print(f"片段 {seq} 异常：{e}，重试...")
            await asyncio.sleep(2 * attempt)  # 退避

        print(f"片段 {seq} 最终失败，请人工检查")
```

#### 3.3.3 为什么重传用同一个 seq 是安全的

因为接收端（客户端）通过 `acked` 集合保证了幂等：第一个成功到达的 seq 被记录，后续所有相同 seq 的迟响应都会被丢弃。不会出现文本重复。

### 3.4 文本拼接与去重（TextMerger）

由于每个片段开头都带上了前一分钟的尾部作为上下文，ASR 会在相邻两段的交界区产生重复识别。需要通过重叠区文本去重。

#### 3.4.1 方法

已知相邻两段在时间轴上的重叠长度，可以利用**时间戳**或者**最长公共子串**自动消除重复。

更好的做法：在 SmartChunker 中，每个片段记录它所包含的“干净起始时间”和“重叠起始时间”。例如：

- 片段 A 实际发送内容 = 重叠前缀(上一段尾部1.5s) + 本段新内容（如30s–600s）
- 片段 B 实际发送内容 = 重叠前缀(本段尾部1.5s) + 新内容

那么拼接时，我们取 A 的识别文本，去掉最后面对应重叠时长（约1.5秒对应的文字）的可能重复，或直接使用文本相似度比对。更稳健的做法是：

1. 将所有片段的文本按 seq 排序。
2. 从第二个片段开始，在其开头寻找与上一个片段结尾的最长公共子串（长度 > 阈值，如5个字符），然后裁掉这个公共前缀。
3. 合并。

```python
def merge_texts(texts, overlap_duration=1.5, char_rate=5): # char_rate: 每秒约几个字
    # 简化：按重叠窗口粗暴裁切
    merged = texts[0]
    for next_text in texts[1:]:
        # 假设中英文混合，大致每秒5字，1.5秒约7-8字，寻找公共串
        # 用 difflib 或 自己写滑动窗口匹配
        cut_len = int(overlap_duration * char_rate) + 5  # 稍微多裁一点
        # 从 merged 末尾和 next_text 开头找最长公共子串，然后剪裁
        overlap_str = find_longest_common_substring(merged[-cut_len:], next_text[:cut_len])
        if overlap_str and len(overlap_str) > 3:  # 至少3字符
            idx = next_text.find(overlap_str)
            next_trimmed = next_text[idx + len(overlap_str):]
        else:
            next_trimmed = next_text
        merged += next_trimmed
    return merged
```

实际面试语音中，ASR 对重叠部分的识别可能略有差异，最长公共子串能较好地对齐。

------

## 四、参数推荐（针对 30 分钟面试）

| 参数                   | 推荐值             | 说明                                      |
| :--------------------- | :----------------- | :---------------------------------------- |
| 采样率、位深、声道     | 16kHz, 16bit, mono | 与小米 ASR 最佳匹配，体积最小             |
| `min_segment_duration` | 60 秒              | 面试回答通常一段持续 1 分钟以上，减少碎片 |
| `max_segment_duration` | 600 秒             | 预留 2 分钟余量，确保不超过 10MB          |
| `min_silence_duration` | 2 秒               | 自然的语句停顿                            |
| `overlap_duration`     | 1.5 秒             | 覆盖常见单音节词和短暂停顿                |
| 发送并发 `concurrency` | 2                  | 大多数 API 限制允许 2 个并发              |
| 请求超时 `timeout`     | 30 秒              | 单段识别足够                              |
| 最大重试次数           | 3                  | 避免无限等待                              |
| VAD 敏感度             | 2（webrtcvad）     | 适中，过滤明显静音                        |

一段 30 分钟面试，最终大致切分为 **3~5 个片段**，调用次数可控。

------

## 五、异常与边界处理

- **一直无人声间隙导致片段超长**：`max_segment_duration` 强制切分，切分点会发生在有声音的地方，但重叠窗口可以保护切点处的词。
- **API 超时后重试**：重试用同一个 `seq`，成功则丢弃之前的响应（或之前响应无效），不会重复。
- **某一片段多次重试均失败**：记录失败 `seq`，最终输出时标出“此处缺省”，并可选择人工补录。
- **最后剩余尾音过短**：片段可能小于最小片段时长，直接作为一个片段发送。
- **音频接近 10MB 边界时**：由于重叠，片段实际字节数可能略超 10MB，可在 `SmartChunker` 输出前检查大小，若超过则用 `pydub` 适当降低重叠或再次切分（极少发生）。

------

## 六、完整落地的工程步骤

1. **环境准备**：

   ```tex
   pip install webrtcvad pydub aiohttp
   ```

2. **编写 `preprocess.py`**：统一音频格式。

3. **编写 `chunker.py`**：实现上述 SmartChunker 类。

4. **编写 `sender.py`**：实现 ReliableSender 类，对接小米 ASR API。

5. **编写 `merger.py`**：实现文本去重合并。

6. **编写主流程 `pipeline.py`**：

   ```tex
   预处理 → 切分 → 并发发送 → 合并 → 保存最终文本
   ```

7. **测试与调参**：用几个真实面试录音，调整 VAD 敏感度、静音时长、重叠时长等，直到转录连贯且调用次数合理。

8. **部署**：可以做成命令行工具或简单的 HTTP 服务供内部调用。