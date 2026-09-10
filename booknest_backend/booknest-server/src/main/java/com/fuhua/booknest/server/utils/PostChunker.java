package com.fuhua.booknest.server.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 帖子正文 Markdown 清洗 + 中文分块工具类。
 *
 * <p>为 RAG 向量化入库服务：先将帖子正文（Markdown）清洗为纯文本，再按中文语义
 * 切分为不超过 {@link #CHUNK_SIZE} 字的文本块，相邻块之间保留 {@link #OVERLAP_SIZE}
 * 字重叠，避免关键句被硬截断导致语义丢失。</p>
 *
 * <p>纯静态工具类，私有构造禁止实例化。</p>
 */
public final class PostChunker {

    /** 单个分块最大字符数 */
    public static final int CHUNK_SIZE = 512;

    /** 相邻分块之间的重叠字符数 */
    public static final int OVERLAP_SIZE = 50;

    /** 句子切分符（中文 + 英文标点） */
    private static final String SENTENCE_SPLIT_REGEX = "(?<=[。！？；.!?;])";

    private PostChunker() {
        // 工具类禁止实例化
    }

    /**
     * 清洗 Markdown 文本，去除语法标记，得到纯文本。
     *
     * <p>去除顺序：代码围栏 → 图片 → 链接 → HTML 标签 → 标题 → 加粗/斜体 → 行内代码
     * → 多余空行。图片必须先于链接去除（图片语法内含链接语法）。</p>
     *
     * @param text 原始 Markdown 文本
     * @return 清洗后的纯文本
     */
    public static String cleanMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        // 去除代码围栏（``` 包裹的多行代码块，DOTALL 跨行匹配，非贪婪）
        result = result.replaceAll("(?s)```.*?```", " ");
        // 去除图片 ![...](...)
        result = result.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        // 去除链接 [...](...)
        result = result.replaceAll("\\[[^\\]]*\\]\\([^)]*\\)", " ");
        // 去除 HTML 标签 <...>
        result = result.replaceAll("<[^>]+>", " ");
        // 去除标题标记（行首的 #）
        result = result.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        // 去除加粗 ** 与斜体 *
        result = result.replaceAll("\\*{1,3}", "");
        // 去除行内代码反引号 `
        result = result.replaceAll("`", "");
        // 去除删除线 ~~
        result = result.replaceAll("~~", "");
        // 合并多余空行（3 个及以上换行合并为两个）
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result.trim();
    }

    /**
     * 中文分块：将长文本切分为带重叠的分块列表。
     *
     * <ol>
     *   <li>先按段落（空行或换行）切分；</li>
     *   <li>贪心合并段落，使每块不超过 {@link #CHUNK_SIZE} 字；</li>
     *   <li>单个段落超长时按中文/英文句点再切分；</li>
     *   <li>相邻块之间保留 {@link #OVERLAP_SIZE} 字重叠（上一块末尾拼到下一块开头）。</li>
     * </ol>
     *
     * @param text 待分块文本
     * @return 分块字符串列表（已过滤空块）
     */
    public static List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 第一步：按段落切分（空行优先，其次单换行），过滤空白段落
        List<String> paragraphs = new ArrayList<>();
        for (String line : text.split("\\n+")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty()) {
            return chunks;
        }

        // 第二步：贪心合并段落 + 超长段落按句点再切分
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            // 单个段落本身超长，先按句点切分后逐段处理
            if (paragraph.length() > CHUNK_SIZE) {
                // 落盘当前累计的块
                if (current.length() > 0) {
                    merged.add(current.toString());
                    current.setLength(0);
                }
                for (String piece : splitLongParagraph(paragraph)) {
                    merged.add(piece);
                }
                continue;
            }
            if (current.length() == 0) {
                current.append(paragraph);
            } else if (current.length() + 1 + paragraph.length() <= CHUNK_SIZE) {
                current.append('\n').append(paragraph);
            } else {
                merged.add(current.toString());
                current.setLength(0);
                current.append(paragraph);
            }
        }
        if (current.length() > 0) {
            merged.add(current.toString());
        }

        // 第三步：相邻块之间叠加 OVERLAP_SIZE 字重叠
        for (int i = 0; i < merged.size(); i++) {
            String chunk = merged.get(i);
            if (i > 0 && OVERLAP_SIZE > 0) {
                String previous = chunks.get(chunks.size() - 1);
                // 取上一块末尾 OVERLAP_SIZE 字作为本块开头
                String overlap = previous.length() <= OVERLAP_SIZE
                        ? previous
                        : previous.substring(previous.length() - OVERLAP_SIZE);
                chunk = overlap + "\n" + chunk;
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 切分超长段落：按中文/英文句点切分为句子，再贪心合并为不超过 {@link #CHUNK_SIZE}
     * 字的块；若单句仍超长则按长度硬截断。
     *
     * @param paragraph 超长段落
     * @return 切分后的分块列表
     */
    private static List<String> splitLongParagraph(String paragraph) {
        List<String> result = new ArrayList<>();
        // 按句点切分（保留句点）
        String[] sentences = paragraph.split(SENTENCE_SPLIT_REGEX);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 单句超长，按长度硬截断
            if (trimmed.length() > CHUNK_SIZE) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                int start = 0;
                while (start < trimmed.length()) {
                    int end = Math.min(start + CHUNK_SIZE, trimmed.length());
                    result.add(trimmed.substring(start, end));
                    start = end;
                }
                continue;
            }
            if (current.length() == 0) {
                current.append(trimmed);
            } else if (current.length() + 1 + trimmed.length() <= CHUNK_SIZE) {
                current.append(' ').append(trimmed);
            } else {
                result.add(current.toString());
                current.setLength(0);
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}
