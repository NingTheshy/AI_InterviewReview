from __future__ import annotations

import math
import os
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "md" / "images" / "class-design"
OUT.mkdir(parents=True, exist_ok=True)

FONT_REGULAR = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"


def font(size: int, bold: bool = False):
    path = FONT_BOLD if bold else FONT_REGULAR
    try:
        return ImageFont.truetype(path, size=size)
    except OSError:
        return ImageFont.load_default()


F_TITLE = font(42, True)
F_SUBTITLE = font(22)
F_HEADER = font(25, True)
F_STEREO = font(18)
F_BODY = font(18)
F_SMALL = font(16)
F_SEQ = font(18)

COLORS = {
    "ink": "#243447",
    "muted": "#61758A",
    "line": "#38506A",
    "entity": "#D9EAD3",
    "entity_stroke": "#5B8C5A",
    "control": "#D9EAF7",
    "control_stroke": "#4C78A8",
    "boundary": "#FCE8CF",
    "boundary_stroke": "#D48A2C",
    "external": "#EFE9F7",
    "external_stroke": "#8064A2",
    "neutral": "#F4F6F8",
    "neutral_stroke": "#7C8793",
    "white": "#FFFFFF",
}


def canvas(title: str, subtitle: str, width=2400, height=1600):
    img = Image.new("RGB", (width, height), COLORS["white"])
    d = ImageDraw.Draw(img)
    d.text((80, 48), title, font=F_TITLE, fill=COLORS["ink"])
    d.text((82, 110), subtitle, font=F_SUBTITLE, fill=COLORS["muted"])
    d.line((80, 150, width - 80, 150), fill="#CBD5DF", width=3)
    return img, d


def fit_lines(draw, text, max_width, font_obj):
    lines = []
    for raw in text.split("\n"):
        current = ""
        for ch in raw:
            trial = current + ch
            if draw.textbbox((0, 0), trial, font=font_obj)[2] <= max_width:
                current = trial
            else:
                if current:
                    lines.append(current)
                current = ch
        lines.append(current)
    return lines


def uml_class(draw, x, y, w, name, stereotype, attrs, methods=None,
              fill=None, stroke=None, header_h=72, line_h=27):
    fill = fill or COLORS["entity"]
    stroke = stroke or COLORS["entity_stroke"]
    methods = methods or []
    attr_lines = []
    for a in attrs:
        attr_lines.extend(fit_lines(draw, a, w - 28, F_BODY))
    method_lines = []
    for m in methods:
        method_lines.extend(fit_lines(draw, m, w - 28, F_BODY))
    attr_h = max(44, 20 + line_h * len(attr_lines))
    method_h = max(0, 18 + line_h * len(method_lines)) if methods else 0
    h = header_h + attr_h + method_h
    draw.rectangle((x, y, x + w, y + h), fill="#FFFFFF", outline=stroke, width=3)
    draw.rectangle((x, y, x + w, y + header_h), fill=fill, outline=stroke, width=3)
    sw = draw.textbbox((0, 0), stereotype, font=F_STEREO)[2]
    nw = draw.textbbox((0, 0), name, font=F_HEADER)[2]
    draw.text((x + (w - sw) / 2, y + 8), stereotype, font=F_STEREO, fill=COLORS["muted"])
    draw.text((x + (w - nw) / 2, y + 33), name, font=F_HEADER, fill=COLORS["ink"])
    yy = y + header_h + 12
    for line in attr_lines:
        draw.text((x + 14, yy), line, font=F_BODY, fill=COLORS["ink"])
        yy += line_h
    if methods:
        sep_y = y + header_h + attr_h
        draw.line((x, sep_y, x + w, sep_y), fill=stroke, width=2)
        yy = sep_y + 10
        for line in method_lines:
            draw.text((x + 14, yy), line, font=F_BODY, fill=COLORS["ink"])
            yy += line_h
    return (x, y, x + w, y + h)


def center(box):
    return ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)


def point_on(box, side):
    x1, y1, x2, y2 = box
    return {
        "top": ((x1 + x2) / 2, y1),
        "bottom": ((x1 + x2) / 2, y2),
        "left": (x1, (y1 + y2) / 2),
        "right": (x2, (y1 + y2) / 2),
    }[side]


def polyline(draw, points, color=None, width=3, dashed=False):
    color = color or COLORS["line"]
    if not dashed:
        draw.line(points, fill=color, width=width, joint="curve")
        return
    for p1, p2 in zip(points, points[1:]):
        x1, y1 = p1
        x2, y2 = p2
        length = math.hypot(x2 - x1, y2 - y1)
        if length == 0:
            continue
        ux, uy = (x2 - x1) / length, (y2 - y1) / length
        pos = 0
        while pos < length:
            end = min(pos + 14, length)
            draw.line((x1 + ux * pos, y1 + uy * pos,
                       x1 + ux * end, y1 + uy * end), fill=color, width=width)
            pos += 24


def arrow_head(draw, start, end, color=None, filled=True, size=14):
    color = color or COLORS["line"]
    angle = math.atan2(end[1] - start[1], end[0] - start[0])
    p1 = (end[0] - size * math.cos(angle - math.pi / 6),
          end[1] - size * math.sin(angle - math.pi / 6))
    p2 = (end[0] - size * math.cos(angle + math.pi / 6),
          end[1] - size * math.sin(angle + math.pi / 6))
    if filled:
        draw.polygon([end, p1, p2], fill=color)
    else:
        draw.line([p1, end, p2], fill=color, width=3)


def hollow_triangle(draw, tip, direction, color=None, size=18):
    color = color or COLORS["line"]
    if direction == "up":
        pts = [(tip[0], tip[1]), (tip[0] - size, tip[1] + size * 1.5),
               (tip[0] + size, tip[1] + size * 1.5)]
    elif direction == "left":
        pts = [(tip[0], tip[1]), (tip[0] + size * 1.5, tip[1] - size),
               (tip[0] + size * 1.5, tip[1] + size)]
    else:
        pts = [(tip[0], tip[1]), (tip[0] - size * 1.5, tip[1] - size),
               (tip[0] - size * 1.5, tip[1] + size)]
    draw.polygon(pts, fill="#FFFFFF", outline=color)
    draw.line(pts + [pts[0]], fill=color, width=3)


def diamond(draw, at, direction, color=None, filled=True, size=14):
    color = color or COLORS["line"]
    x, y = at
    if direction in ("left", "right"):
        pts = [(x, y), (x + size, y - size), (x + 2 * size, y), (x + size, y + size)]
    else:
        pts = [(x, y), (x - size, y + size), (x, y + 2 * size), (x + size, y + size)]
    draw.polygon(pts, fill=color if filled else "#FFFFFF", outline=color)


def label(draw, xy, text, bold=False, fill=None, anchor="la"):
    draw.text(xy, text, font=F_SMALL if not bold else font(16, True),
              fill=fill or COLORS["muted"], anchor=anchor,
              stroke_width=2, stroke_fill="#FFFFFF")


def save(img, name):
    path = OUT / name
    img.save(path, format="PNG", optimize=True)
    return path


def entity_diagram():
    img, d = canvas("AI 面试复盘系统核心实体类图", "UML Class Diagram · 基于当前 Java Entity 与 SQL 逻辑关联", 2600, 1900)
    boxes = {}
    boxes["BaseEntity"] = uml_class(d, 980, 190, 640, "BaseEntity", "<<abstract entity>>",
                                     ["+ id: Long", "+ deleted: Integer", "+ createdAt: LocalDateTime", "+ updatedAt: LocalDateTime"],
                                     fill=COLORS["neutral"], stroke=COLORS["neutral_stroke"])
    boxes["User"] = uml_class(d, 100, 520, 440, "User", "<<entity>>",
                               ["+ username: String", "+ email: String", "+ role: Integer", "+ status: Integer"])
    boxes["Interview"] = uml_class(d, 760, 520, 560, "Interview", "<<aggregate root>>",
                                    ["+ userId: Long", "+ companyName: String", "+ positionTitle: String", "+ transcriptText: String", "+ overallScore: Integer", "+ status / processingStep: Integer"],
                                    fill="#CFE5F5", stroke="#3F78A8")
    boxes["AiConfig"] = uml_class(d, 1900, 220, 520, "AiConfig", "<<entity>>",
                                   ["+ provider: String", "+ modelName: String", "+ configType: Integer", "+ isDefault: Integer", "+ status: Integer"],
                                   fill=COLORS["external"], stroke=COLORS["external_stroke"])
    boxes["InterviewQuestion"] = uml_class(d, 730, 1080, 560, "InterviewQuestion", "<<entity>>",
                                            ["+ interviewId: Long", "+ questionIndex: Integer", "+ questionText / answerText: String", "+ startTime / endTime: Integer", "+ score: Integer"])
    boxes["InterviewNote"] = uml_class(d, 80, 1130, 470, "InterviewNote", "<<entity>>",
                                        ["+ interviewId: Long", "+ userId: Long", "+ noteType: String", "+ questionId: Long", "+ noteContent: String"])
    boxes["InterviewFavorite"] = uml_class(d, 650, 1530, 530, "InterviewFavorite", "<<entity>>",
                                            ["+ userId: Long", "+ interviewId: Long", "+ questionId: Long", "+ remark: String"])
    boxes["InterviewShare"] = uml_class(d, 1510, 720, 500, "InterviewShare", "<<entity>>",
                                         ["+ interviewId: Long", "+ userId: Long", "+ shareToken: String", "+ expireAt: LocalDateTime", "+ isPublic / viewCount: Integer"])
    boxes["InterviewComment"] = uml_class(d, 1930, 1270, 480, "InterviewComment", "<<entity>>",
                                           ["+ shareId: Long", "+ userId: Long", "+ content: String"])

    # UML note for the common generalization, kept separate to avoid edges crossing class boxes.
    d.rounded_rectangle((640, 430, 1720, 490), radius=10, fill="#F6F8FA", outline="#7C8793", width=2)
    d.text((1180, 460), "泛化约束：User、Interview、Question、Note、Favorite、Share、Comment、AiConfig 均继承 BaseEntity",
           font=F_SMALL, fill="#596775", anchor="mm")
    base_bottom = point_on(boxes["BaseEntity"], "bottom")
    polyline(d, [base_bottom, (base_bottom[0], 430)], color="#7C8793", width=2, dashed=True)

    # User to Interview.
    u = point_on(boxes["User"], "right")
    i = point_on(boxes["Interview"], "left")
    polyline(d, [u, i])
    label(d, (u[0] + 12, u[1] - 22), "1")
    label(d, (i[0] - 12, i[1] - 22), "0..*", anchor="ra")
    label(d, ((u[0] + i[0]) / 2, u[1] - 26), "owns", anchor="mm")

    # Interview composition Question.
    s = point_on(boxes["Interview"], "bottom")
    t = point_on(boxes["InterviewQuestion"], "top")
    polyline(d, [s, t])
    diamond(d, s, "down", filled=True)
    label(d, (s[0] + 20, s[1] + 10), "1")
    label(d, (t[0] + 20, t[1] - 26), "0..*")
    label(d, (s[0] + 55, (s[1] + t[1]) / 2), "contains")

    # Interview to Note.
    s = point_on(boxes["Interview"], "left")
    t = point_on(boxes["InterviewNote"], "top")
    pts = [s, (630, s[1]), (630, 1040), (t[0], 1040), t]
    polyline(d, pts)
    label(d, (620, 980), "has 0..* notes", anchor="ra")

    # Question to Favorite.
    s = point_on(boxes["InterviewQuestion"], "bottom")
    t = point_on(boxes["InterviewFavorite"], "top")
    polyline(d, [s, t])
    label(d, (s[0] + 18, (s[1] + t[1]) / 2), "collected by 0..*")

    # Interview to Share.
    s = point_on(boxes["Interview"], "right")
    t = point_on(boxes["InterviewShare"], "left")
    polyline(d, [s, (1410, s[1]), (1410, t[1]), t])
    label(d, (1425, t[1] - 28), "1 → 0..* shares")

    # Share composition Comment.
    s = point_on(boxes["InterviewShare"], "bottom")
    t = point_on(boxes["InterviewComment"], "top")
    pts = [s, (s[0], 1190), (t[0], 1190), t]
    polyline(d, pts)
    diamond(d, s, "down", filled=True)
    label(d, ((s[0] + t[0]) / 2, 1165), "1 → 0..* comments", anchor="mm")

    # User relations routed on left/bottom corridor.
    su = point_on(boxes["User"], "left")
    tn = point_on(boxes["InterviewNote"], "left")
    polyline(d, [su, (40, su[1]), (40, tn[1]), tn], color="#607D8B", width=2)
    label(d, (48, 920), "writes", fill="#607D8B")
    tf = point_on(boxes["InterviewFavorite"], "left")
    polyline(d, [(su[0], su[1] + 40), (20, su[1] + 40), (20, 1760),
                 (600, 1760), (600, tf[1]), tf], color="#607D8B", width=2)
    label(d, (330, 1734), "creates favorites", fill="#607D8B", anchor="mm")
    tc = point_on(boxes["InterviewComment"], "right")
    polyline(d, [(su[0], su[1] - 40), (55, su[1] - 40), (55, 1830),
                 (2490, 1830), (2490, tc[1]), tc], color="#607D8B", width=2)
    label(d, (2240, 1804), "writes comments", fill="#607D8B", anchor="mm")

    # AiConfig note.
    d.rounded_rectangle((1880, 570, 2450, 650), radius=12, fill="#F8F5FC", outline="#B39DCC", width=2)
    d.text((1900, 592), "独立配置实体；由 AiClientFactory 按类型和供应商选择", font=F_BODY, fill="#5E467A")

    return save(img, "01-core-entity-class-diagram.png")


def simple_box(draw, x, y, w, h, title, stereotype, fill, stroke):
    draw.rounded_rectangle((x, y, x + w, y + h), radius=12, fill=fill, outline=stroke, width=3)
    tw = draw.textbbox((0, 0), title, font=F_HEADER)[2]
    sw = draw.textbbox((0, 0), stereotype, font=F_STEREO)[2]
    draw.text((x + (w - sw) / 2, y + 13), stereotype, font=F_STEREO, fill=COLORS["muted"])
    draw.text((x + (w - tw) / 2, y + 43), title, font=F_HEADER, fill=COLORS["ink"])
    return (x, y, x + w, y + h)


def bce_diagram():
    img, d = canvas("AI 面试复盘系统 BCE 总体类关系图", "Boundary → Control → Entity · 实线为调用/持久化依赖，虚线为外部适配", 2600, 1750)
    # Column containers.
    columns = [
        (70, 190, 690, 1450, "Boundary 边界层", COLORS["boundary"], COLORS["boundary_stroke"]),
        (730, 190, 1100, 1450, "Control 控制层", COLORS["control"], COLORS["control_stroke"]),
        (1870, 190, 660, 1450, "Entity 实体层", COLORS["entity"], COLORS["entity_stroke"]),
    ]
    for x, y, w, h, title, fill, stroke in columns:
        d.rounded_rectangle((x, y, x + w, y + h), radius=18, fill="#FFFFFF", outline=stroke, width=3)
        d.rectangle((x, y, x + w, y + 58), fill=fill, outline=stroke, width=3)
        d.text((x + 22, y + 14), title, font=F_HEADER, fill=COLORS["ink"])

    boundary_nodes = [
        ("用户页面边界", "Login / Register / Profile"),
        ("面试页面边界", "List / Upload / Detail / Favorite"),
        ("公开社区边界", "Share / Experience / Comment"),
        ("管理页面边界", "Users / Interviews / AI Config / Stats"),
        ("REST Controller", "14 个 HTTP 接入类"),
        ("外部适配边界", "ASR / LLM / File / Redis"),
    ]
    control_nodes = [
        ("AuthServiceImpl", "认证与账户"),
        ("InterviewServiceImpl", "面试主用例"),
        ("InterviewAsyncServiceImpl", "异步状态机"),
        ("Note / Favorite Service", "笔记与收藏"),
        ("Share / Experience / Comment", "分享与社区"),
        ("Transcription / Scoring", "AI 转写与评分"),
        ("AiClientFactory", "供应商选择"),
        ("AiConfig / Admin Service", "配置与管理"),
    ]
    entity_nodes = [
        ("User", "用户与角色状态"),
        ("Interview", "面试聚合根"),
        ("InterviewQuestion", "问答与逐题评分"),
        ("InterviewNote", "面试/问题笔记"),
        ("InterviewFavorite", "问题收藏"),
        ("InterviewShare", "分享与公开状态"),
        ("InterviewComment", "面经评论"),
        ("AiConfig", "ASR/LLM 配置"),
    ]

    def node_list(x, y, w, items, fill, stroke, gap=18, h=116):
        boxes = []
        for idx, (name, desc) in enumerate(items):
            yy = y + idx * (h + gap)
            d.rounded_rectangle((x, yy, x + w, yy + h), radius=10, fill=fill, outline=stroke, width=2)
            d.text((x + 18, yy + 16), name, font=F_HEADER, fill=COLORS["ink"])
            d.text((x + 18, yy + 58), desc, font=F_BODY, fill=COLORS["muted"])
            boxes.append((x, yy, x + w, yy + h))
        return boxes

    b = node_list(110, 285, 610, boundary_nodes, "#FFF7ED", COLORS["boundary_stroke"], gap=38, h=150)
    c = node_list(780, 265, 1000, control_nodes, "#F2F8FC", COLORS["control_stroke"], gap=24, h=132)
    e = node_list(1910, 265, 580, entity_nodes, "#F2F8F0", COLORS["entity_stroke"], gap=24, h=132)

    # Boundary to control connectors.
    pairs = [(0, 0), (1, 1), (1, 3), (2, 4), (3, 7), (4, 1), (5, 5), (5, 6)]
    for bi, ci in pairs:
        s = point_on(b[bi], "right")
        t = point_on(c[ci], "left")
        bend = (750, s[1])
        polyline(d, [s, bend, (bend[0], t[1]), t], color="#9A6B2F", width=2)
        arrow_head(d, (bend[0], t[1]), t, color="#9A6B2F", size=11)

    # Control to entity connectors.
    pairs2 = [(0, 0), (1, 1), (1, 2), (2, 1), (2, 2), (3, 3), (3, 4),
              (4, 1), (4, 5), (4, 6), (5, 1), (5, 2), (6, 7), (7, 0), (7, 7)]
    offsets = {}
    for ci, ei in pairs2:
        s = point_on(c[ci], "right")
        t = point_on(e[ei], "left")
        key = (ci, ei)
        lane = 1800 + (offsets.get(ci, 0) % 5) * 12
        offsets[ci] = offsets.get(ci, 0) + 1
        polyline(d, [s, (lane, s[1]), (lane, t[1]), t], color="#47704A", width=2)
        arrow_head(d, (lane, t[1]), t, color="#47704A", size=11)

    # Legend.
    legend_y = 1645
    entries = [
        (COLORS["boundary"], COLORS["boundary_stroke"], "Boundary：页面、HTTP、外部系统接口"),
        (COLORS["control"], COLORS["control_stroke"], "Control：用例编排、规则与事务"),
        (COLORS["entity"], COLORS["entity_stroke"], "Entity：可持久化领域状态"),
    ]
    x = 120
    for fill, stroke, txt in entries:
        d.rectangle((x, legend_y, x + 34, legend_y + 26), fill=fill, outline=stroke, width=2)
        d.text((x + 48, legend_y - 1), txt, font=F_SMALL, fill=COLORS["ink"])
        x += 790
    return save(img, "02-bce-overall-class-relationship.png")


def draw_lifeline(draw, x, top, bottom, name, kind="object", fill="#F2F8FC", stroke="#4C78A8"):
    w, h = 260, 92
    if kind == "actor":
        # Standard stick actor plus label.
        cx = x
        draw.ellipse((cx - 17, top, cx + 17, top + 34), outline=stroke, width=3)
        draw.line((cx, top + 34, cx, top + 72), fill=stroke, width=3)
        draw.line((cx - 24, top + 48, cx + 24, top + 48), fill=stroke, width=3)
        draw.line((cx, top + 72, cx - 22, top + 102), fill=stroke, width=3)
        draw.line((cx, top + 72, cx + 22, top + 102), fill=stroke, width=3)
        tw = draw.textbbox((0, 0), name, font=F_HEADER)[2]
        draw.text((cx - tw / 2, top + 112), name, font=F_HEADER, fill=COLORS["ink"])
        line_top = top + 155
    else:
        draw.rectangle((x - w / 2, top, x + w / 2, top + h), fill=fill, outline=stroke, width=3)
        tw = draw.textbbox((0, 0), name, font=F_HEADER)[2]
        draw.text((x - tw / 2, top + 28), name, font=F_HEADER, fill=COLORS["ink"])
        line_top = top + h
    polyline(draw, [(x, line_top), (x, bottom)], color="#7C8793", width=2, dashed=True)
    return line_top


def seq_message(draw, x1, x2, y, text, dashed=False, async_msg=False, color=None):
    color = color or COLORS["line"]
    polyline(draw, [(x1, y), (x2, y)], color=color, width=3, dashed=dashed)
    arrow_head(draw, (x1, y), (x2, y), color=color, filled=not async_msg, size=13)
    mid = (x1 + x2) / 2
    draw.text((mid, y - 30), text, font=F_SEQ, fill=COLORS["ink"], anchor="mm",
              stroke_width=3, stroke_fill="#FFFFFF")


def upload_sequence():
    img, d = canvas("面试上传与 AI 复盘时序图", "UML Sequence Diagram · 同步调用使用实线，异步调用使用虚线开放箭头", 2700, 1700)
    xs = [140, 430, 760, 1090, 1420, 1750, 2080, 2410]
    names = ["用户", "上传页面", "InterviewController", "InterviewService", "Interview", "AsyncService", "Transcription", "Scoring/Parser"]
    for idx, (x, name) in enumerate(zip(xs, names)):
        draw_lifeline(d, x, 210, 1580, name, kind="actor" if idx == 0 else "object",
                      fill=COLORS["boundary"] if idx in (1, 2) else (COLORS["entity"] if idx == 4 else COLORS["control"]),
                      stroke=COLORS["boundary_stroke"] if idx in (1, 2) else (COLORS["entity_stroke"] if idx == 4 else COLORS["control_stroke"]))
    y = 410
    steps = [
        (0, 1, "1. 提交音频、简历、JD", False, False),
        (1, 2, "2. POST /interviews/upload", False, False),
        (2, 3, "3. upload(currentUserId, files, metadata)", False, False),
        (3, 4, "4. 创建 PROCESSING / step=0", False, False),
        (3, 5, "5. processInterview(interviewId)", True, True),
        (3, 2, "6. 返回 interviewId", True, True),
        (2, 1, "7. Result<Long>", True, True),
        (5, 4, "8. 更新 step=1", False, False),
        (5, 6, "9. transcribeDetailed(audioPath)", False, False),
        (6, 5, "10. 文本、时间戳、音频时长", True, True),
        (5, 4, "11. 保存转写与公司分级，step=2", False, False),
        (5, 7, "12. analyzeWithRubric(...) / parseAndSave", False, False),
        (7, 5, "13. 结构化评分结果", True, True),
        (5, 4, "14. 保存问题与评分，step=4，COMPLETED", False, False),
    ]
    for a, b, txt, dashed, async_msg in steps:
        seq_message(d, xs[a], xs[b], y, txt, dashed=dashed, async_msg=async_msg,
                    color="#5F6B78" if dashed else COLORS["line"])
        y += 78
    d.rounded_rectangle((1780, 1480, 2590, 1585), radius=12, fill="#FFF2CC", outline="#D6B656", width=2)
    d.text((1805, 1500), "异常分支：AsyncService 捕获异常 → Interview.status=FAILED → 保存 errorMessage；用户可重试。",
           font=F_BODY, fill="#6B5715")
    return save(img, "03-interview-ai-processing-sequence.png")


def share_sequence():
    img, d = canvas("分享、面经与评论时序图", "UML Sequence Diagram · 私有分享管理与公开面经访问使用不同控制链", 2600, 1550)
    xs = [150, 470, 790, 1110, 1430, 1750, 2070, 2390]
    names = ["面试所有者", "面试详情页", "ShareController", "ShareService", "InterviewShare", "面经详情页", "ExperienceService", "CommentService"]
    kinds = ["actor", "object", "object", "object", "object", "object", "object", "object"]
    for idx, (x, name) in enumerate(zip(xs, names)):
        if idx in (1, 2, 5):
            fill, stroke = COLORS["boundary"], COLORS["boundary_stroke"]
        elif idx == 4:
            fill, stroke = COLORS["entity"], COLORS["entity_stroke"]
        else:
            fill, stroke = COLORS["control"], COLORS["control_stroke"]
        draw_lifeline(d, x, 210, 1430, name, kind=kinds[idx], fill=fill, stroke=stroke)
    y = 420
    steps = [
        (0, 1, "1. 创建分享 / 发布面经", False, False),
        (1, 2, "2. POST /interviews/{id}/share", False, False),
        (2, 3, "3. createShare(id, ownerId, expireType, isPublic)", False, False),
        (3, 4, "4. 创建唯一 token、有效期与公开状态", False, False),
        (4, 3, "5. InterviewShare", True, True),
        (3, 1, "6. ShareResponse", True, True),
        (0, 5, "7. 访客访问公开面经", False, False),
        (5, 6, "8. GET /experiences/{token} / getDetail", False, False),
        (6, 4, "9. 校验公开、未删除、未过期", False, False),
        (4, 6, "10. 分享与面试引用", True, True),
        (6, 5, "11. 脱敏 ExperienceDetailResponse", True, True),
        (5, 7, "12. 发表评论(token, currentUserId, content)", False, False),
        (7, 4, "13. 校验分享有效并保存 InterviewComment", False, False),
        (7, 5, "14. 评论成功", True, True),
    ]
    for a, b, txt, dashed, async_msg in steps:
        seq_message(d, xs[a], xs[b], y, txt, dashed=dashed, async_msg=async_msg,
                    color="#5F6B78" if dashed else COLORS["line"])
        y += 70
    d.rounded_rectangle((1500, 1360, 2490, 1460), radius=12, fill="#FCE8CF", outline="#D48A2C", width=2)
    d.text((1525, 1382), "隐私边界：公开 DTO 不返回音频、简历、完整转写、个人笔记和配置密钥。",
           font=F_BODY, fill="#704B1C")
    return save(img, "04-share-experience-comment-sequence.png")


class DrawioBuilder:
    def __init__(self):
        self.mxfile = ET.Element("mxfile", {"host": "drawio", "version": "26.0.0"})

    def page(self, name, width=2600, height=1800):
        diagram = ET.SubElement(self.mxfile, "diagram", {"name": name})
        model = ET.SubElement(diagram, "mxGraphModel", {
            "dx": "1200", "dy": "800", "grid": "1", "gridSize": "10",
            "guides": "1", "tooltips": "1", "connect": "1", "arrows": "1",
            "fold": "1", "page": "1", "pageScale": "1",
            "pageWidth": str(width), "pageHeight": str(height), "math": "0", "shadow": "0"
        })
        root = ET.SubElement(model, "root")
        ET.SubElement(root, "mxCell", {"id": "0"})
        ET.SubElement(root, "mxCell", {"id": "1", "parent": "0"})
        return root

    @staticmethod
    def vertex(root, cid, value, x, y, w, h, style, parent="1"):
        cell = ET.SubElement(root, "mxCell", {
            "id": cid, "value": value, "style": style, "vertex": "1", "parent": parent
        })
        ET.SubElement(cell, "mxGeometry", {
            "x": str(x), "y": str(y), "width": str(w), "height": str(h), "as": "geometry"
        })
        return cell

    @staticmethod
    def edge(root, cid, source, target, value="", style=None):
        style = style or "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;endFill=1;strokeColor=#38506A;fontSize=14;"
        cell = ET.SubElement(root, "mxCell", {
            "id": cid, "value": value, "style": style, "edge": "1", "parent": "1",
            "source": source, "target": target
        })
        ET.SubElement(cell, "mxGeometry", {"relative": "1", "as": "geometry"})
        return cell

    @staticmethod
    def edge_points(root, cid, source_point, target_point, value="", style=None):
        style = style or "edgeStyle=none;html=1;endArrow=block;endFill=1;strokeColor=#38506A;fontSize=14;"
        cell = ET.SubElement(root, "mxCell", {
            "id": cid, "value": value, "style": style, "edge": "1", "parent": "1"
        })
        geometry = ET.SubElement(cell, "mxGeometry", {"relative": "1", "as": "geometry"})
        ET.SubElement(geometry, "mxPoint", {
            "x": str(source_point[0]), "y": str(source_point[1]), "as": "sourcePoint"
        })
        ET.SubElement(geometry, "mxPoint", {
            "x": str(target_point[0]), "y": str(target_point[1]), "as": "targetPoint"
        })
        return cell

    def write(self, path):
        ET.ElementTree(self.mxfile).write(path, encoding="utf-8", xml_declaration=True)


def drawio_source():
    b = DrawioBuilder()
    class_style = "swimlane;fontStyle=1;align=center;startSize=46;html=1;whiteSpace=wrap;fillColor=#D9EAD3;strokeColor=#5B8C5A;fontFamily=Microsoft YaHei;fontSize=16;"
    control_style = "rounded=1;whiteSpace=wrap;html=1;fillColor=#D9EAF7;strokeColor=#4C78A8;fontFamily=Microsoft YaHei;fontSize=16;"
    boundary_style = "rounded=1;whiteSpace=wrap;html=1;fillColor=#FCE8CF;strokeColor=#D48A2C;fontFamily=Microsoft YaHei;fontSize=16;"

    # Page 1: entity classes.
    r = b.page("01-核心实体类图")
    positions = {
        "base": (980, 120, 640, 210), "user": (80, 500, 440, 250),
        "interview": (730, 500, 560, 330), "config": (1900, 240, 500, 270),
        "question": (730, 1050, 560, 300), "note": (80, 1080, 470, 280),
        "favorite": (650, 1480, 520, 250), "share": (1510, 700, 500, 290),
        "comment": (1930, 1220, 480, 230),
    }
    labels = {
        "base": "&lt;&lt;abstract entity&gt;&gt;&lt;br&gt;&lt;b&gt;BaseEntity&lt;/b&gt;&lt;hr&gt;+ id: Long&lt;br&gt;+ deleted: Integer&lt;br&gt;+ createdAt: LocalDateTime&lt;br&gt;+ updatedAt: LocalDateTime",
        "user": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;User&lt;/b&gt;&lt;hr&gt;+ username: String&lt;br&gt;+ email: String&lt;br&gt;+ role: Integer&lt;br&gt;+ status: Integer",
        "interview": "&lt;&lt;aggregate root&gt;&gt;&lt;br&gt;&lt;b&gt;Interview&lt;/b&gt;&lt;hr&gt;+ userId: Long&lt;br&gt;+ companyName: String&lt;br&gt;+ positionTitle: String&lt;br&gt;+ transcriptText: String&lt;br&gt;+ overallScore: Integer&lt;br&gt;+ status: Integer&lt;br&gt;+ processingStep: Integer",
        "config": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;AiConfig&lt;/b&gt;&lt;hr&gt;+ provider: String&lt;br&gt;+ modelName: String&lt;br&gt;+ configType: Integer&lt;br&gt;+ isDefault: Integer&lt;br&gt;+ status: Integer",
        "question": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;InterviewQuestion&lt;/b&gt;&lt;hr&gt;+ interviewId: Long&lt;br&gt;+ questionIndex: Integer&lt;br&gt;+ questionText: String&lt;br&gt;+ answerText: String&lt;br&gt;+ startTime/endTime: Integer&lt;br&gt;+ score: Integer",
        "note": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;InterviewNote&lt;/b&gt;&lt;hr&gt;+ interviewId: Long&lt;br&gt;+ userId: Long&lt;br&gt;+ noteType: String&lt;br&gt;+ questionId: Long&lt;br&gt;+ noteContent: String",
        "favorite": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;InterviewFavorite&lt;/b&gt;&lt;hr&gt;+ userId: Long&lt;br&gt;+ interviewId: Long&lt;br&gt;+ questionId: Long&lt;br&gt;+ remark: String",
        "share": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;InterviewShare&lt;/b&gt;&lt;hr&gt;+ interviewId: Long&lt;br&gt;+ userId: Long&lt;br&gt;+ shareToken: String&lt;br&gt;+ expireAt: LocalDateTime&lt;br&gt;+ isPublic/viewCount: Integer",
        "comment": "&lt;&lt;entity&gt;&gt;&lt;br&gt;&lt;b&gt;InterviewComment&lt;/b&gt;&lt;hr&gt;+ shareId: Long&lt;br&gt;+ userId: Long&lt;br&gt;+ content: String",
    }
    for cid, pos in positions.items():
        style = class_style
        if cid == "base":
            style = style.replace("#D9EAD3", "#F4F6F8").replace("#5B8C5A", "#7C8793")
        if cid == "config":
            style = style.replace("#D9EAD3", "#EFE9F7").replace("#5B8C5A", "#8064A2")
        b.vertex(r, cid, labels[cid], *pos, style)
    inheritance = "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;endFill=0;strokeColor=#7C8793;"
    b.vertex(r, "inherit_note",
             "&lt;&lt;constraint&gt;&gt;&lt;br&gt;所有具体实体均继承 BaseEntity",
             760, 390, 900, 90,
             "shape=note;whiteSpace=wrap;html=1;fillColor=#F4F6F8;strokeColor=#7C8793;fontFamily=Microsoft YaHei;fontSize=16;")
    b.edge(r, "gen_note", "inherit_note", "base", "泛化约束", inheritance)
    assoc = "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=none;strokeColor=#38506A;fontSize=14;"
    comp = assoc + "startArrow=diamondThin;startFill=1;"
    b.edge(r, "e_ui", "user", "interview", "1 owns 0..*", assoc)
    b.edge(r, "e_iq", "interview", "question", "1 contains 0..*", comp)
    b.edge(r, "e_in", "interview", "note", "1 has 0..*", assoc)
    b.edge(r, "e_qn", "question", "note", "question note", assoc)
    b.edge(r, "e_qf", "question", "favorite", "1 collected by 0..*", assoc)
    b.edge(r, "e_is", "interview", "share", "1 shared as 0..*", assoc)
    b.edge(r, "e_sc", "share", "comment", "1 receives 0..*", comp)
    b.edge(r, "e_uc", "user", "comment", "writes", assoc)

    # Page 2: BCE overview.
    r = b.page("02-BCE总体关系")
    b.vertex(r, "lane_b", "Boundary 边界层", 60, 90, 680, 1450,
             "swimlane;startSize=50;html=1;fillColor=#FFF7ED;strokeColor=#D48A2C;fontStyle=1;fontSize=20;fontFamily=Microsoft YaHei;")
    b.vertex(r, "lane_c", "Control 控制层", 800, 90, 980, 1450,
             "swimlane;startSize=50;html=1;fillColor=#F2F8FC;strokeColor=#4C78A8;fontStyle=1;fontSize=20;fontFamily=Microsoft YaHei;")
    b.vertex(r, "lane_e", "Entity 实体层", 1840, 90, 680, 1450,
             "swimlane;startSize=50;html=1;fillColor=#F2F8F0;strokeColor=#5B8C5A;fontStyle=1;fontSize=20;fontFamily=Microsoft YaHei;")
    boundary = ["用户页面边界", "面试页面边界", "公开社区边界", "管理页面边界", "REST Controller", "外部适配边界"]
    control = ["AuthServiceImpl", "InterviewServiceImpl", "InterviewAsyncServiceImpl", "Note/Favorite Service", "Share/Experience/Comment", "Transcription/Scoring", "AiClientFactory", "AiConfig/Admin Service"]
    entity = ["User", "Interview", "InterviewQuestion", "InterviewNote", "InterviewFavorite", "InterviewShare", "InterviewComment", "AiConfig"]
    for idx, name in enumerate(boundary):
        b.vertex(r, f"b{idx}", name, 40, 100 + idx * 205, 600, 130, boundary_style, parent="lane_b")
    for idx, name in enumerate(control):
        b.vertex(r, f"c{idx}", name, 50, 70 + idx * 165, 880, 110, control_style, parent="lane_c")
    for idx, name in enumerate(entity):
        b.vertex(r, f"n{idx}", name, 50, 70 + idx * 165, 580, 110, class_style, parent="lane_e")
    for bi, ci in [(0,0),(1,1),(1,3),(2,4),(3,7),(4,1),(5,5),(5,6)]:
        b.edge(r, f"bc{bi}_{ci}", f"b{bi}", f"c{ci}")
    for ci, ei in [(0,0),(1,1),(1,2),(2,1),(2,2),(3,3),(3,4),(4,1),(4,5),(4,6),(5,1),(5,2),(6,7),(7,0),(7,7)]:
        b.edge(r, f"ce{ci}_{ei}", f"c{ci}", f"n{ei}")

    # Page 3 and 4: sequence pages using standard UML lifeline cells.
    def sequence_page(page_name, participants, messages):
        root = b.page(page_name, 2800, 1800)
        xs = [80 + i * 320 for i in range(len(participants))]
        for i, p in enumerate(participants):
            b.vertex(root, f"p{i}", p, xs[i], 90, 240, 1450,
                     "shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;html=1;container=1;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;fillColor=#F2F8FC;strokeColor=#4C78A8;fontFamily=Microsoft YaHei;fontSize=16;")
        sync = "edgeStyle=none;html=1;verticalAlign=bottom;endArrow=block;endFill=1;strokeColor=#38506A;fontFamily=Microsoft YaHei;fontSize=14;"
        async_style = "edgeStyle=none;html=1;verticalAlign=bottom;endArrow=open;endFill=0;dashed=1;strokeColor=#6B7785;fontFamily=Microsoft YaHei;fontSize=14;"
        for idx, (src, dst, label_text, async_flag) in enumerate(messages):
            y = 300 + idx * 86
            source_x = xs[src] + 120
            target_x = xs[dst] + 120
            b.edge_points(root, f"m{idx}", (source_x, y), (target_x, y), label_text,
                          async_style if async_flag else sync)
        return root

    sequence_page("03-面试上传与AI复盘时序", ["用户", "上传页面", "InterviewController", "InterviewService", "Interview", "AsyncService", "Transcription", "Scoring/Parser"], [
        (0,1,"提交音频、简历、JD",False),(1,2,"POST /interviews/upload",False),(2,3,"upload(...) ",False),
        (3,4,"创建 PROCESSING 记录",False),(3,5,"processInterview(id)",True),(3,2,"返回 interviewId",True),
        (5,4,"step=1",False),(5,6,"transcribeDetailed",False),(6,5,"转写结果",True),(5,4,"保存转写，step=2",False),
        (5,7,"analyzeWithRubric / parseAndSave",False),(7,5,"结构化评分",True),(5,4,"保存评分并 COMPLETED",False)
    ])
    sequence_page("04-分享面经与评论时序", ["所有者", "面试详情页", "ShareController", "ShareService", "InterviewShare", "面经详情页", "ExperienceService", "CommentService"], [
        (0,1,"创建分享",False),(1,2,"POST /interviews/{id}/share",False),(2,3,"createShare",False),(3,4,"创建唯一 token",False),
        (4,3,"InterviewShare",True),(3,1,"ShareResponse",True),(0,5,"访问公开面经",False),(5,6,"getDetail(token)",False),
        (6,4,"校验公开与过期",False),(4,6,"分享引用",True),(6,5,"脱敏详情",True),(5,7,"发表评论",False),
        (7,4,"校验分享并保存评论",False),(7,5,"评论成功",True)
    ])
    path = OUT / "AI面试复盘系统-UML图.drawio"
    b.write(path)
    return path


if __name__ == "__main__":
    outputs = [entity_diagram(), bce_diagram(), upload_sequence(), share_sequence(), drawio_source()]
    for output in outputs:
        print(output)
