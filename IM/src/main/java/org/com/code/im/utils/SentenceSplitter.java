package org.com.code.im.utils;

import com.hankcs.hanlp.utility.SentencesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SentenceSplitter {

    // 定义每个文本块（chunk）的最大字符数。这是一个可配置的阈值，用于确保每个块不会过长。
    private static final int CHUNK_SIZE = 300;

    // 定义块与块之间重叠的句子数量。这有助于在块的边界处保留上下文信息。
    private static final int OVERLAP_SENTENCES = 2;

    /**
     * RAG文本分块的主方法。
     * 采用“结构优先，语义其次”的精细化分块策略：
     * 1. 结构化分割: 首先按段落（由一个或多个空行定义）进行宏观分割。
     * 2. 语义化分割: 然后在每个段落内部，使用句子分割工具进行微观分割。
     * 3. 智能组合: 最后将句子组合成大小适中的文本块，并实现块间的内容重叠。
     *
     * @param text 待分块的原始完整文本。
     * @return 经过分块处理后的字符串列表，每个字符串是一个独立的chunk。
     */
    public static List<String> RagChunker(String text) {
        // --- 前置校验 ---
        // 检查输入文本是否为null或仅包含空白字符，如果是，则直接返回一个空列表。
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // --- 初始化结果容器 ---
        // 创建一个列表，用于存放最终生成的所有文本块。
        List<String> finalChunks = new ArrayList<>();

        // --- 步骤 1: 段落分割 ---
        // 使用正则表达式 "\\n\\n+" 按一个或多个连续的空行来分割文本，得到段落数组。
        // 这是保留文档宏观结构的关键一步。
        String[] paragraphs = text.split("\\n\\n+");

        // --- 步骤 2 & 3: 遍历并处理每个段落 ---
        // 循环遍历分割出的每一个段落。
        for (String paragraph : paragraphs) {
            // 调用辅助方法，将当前段落处理成一个或多个文本块。
            List<String> chunksFromParagraph = splitParagraphIntoChunks(paragraph);
            // 将从段落生成的所有文本块添加到最终的结果列表中。
            finalChunks.addAll(chunksFromParagraph);
        }

        // 返回包含所有文本块的最终列表。
        return finalChunks;
    }

    /**
     * 将单个段落分割成一个或多个符合条件的文本块（Chunk）。
     *
     * @param paragraph 单个段落的文本。
     * @return 从该段落生成的一个或多个文本块的列表。
     */
    private static List<String> splitParagraphIntoChunks(String paragraph) {
        // 初始化用于存放当前段落生成的chunks的列表。
        List<String> chunks = new ArrayList<>();

        // --- 前置处理与校验 ---
        // 去除段落首尾的空白字符。
        String trimmedPara = paragraph.trim();
        // 如果处理后段落为空，则无需继续，直接返回空列表。
        if (trimmedPara.isEmpty()) {
            return chunks;
        }

        // --- 步骤 2: 句子分割 ---
        // 使用 HanLP 的句子分割工具将整理后的段落精确地切分成句子列表。
        // 这是保证每个chunk语义完整性的核心。
        List<String> allSentences = SentencesUtil.toSentenceList(trimmedPara);

        // 如果段落中没有分割出任何句子，直接返回。
        if (allSentences.isEmpty()) {
            return chunks;
        }

        // --- 步骤 3: 句子组合成Chunk ---
        // 用于临时存放当前正在构建的chunk所包含的句子。
        List<String> currentChunkSentences = new ArrayList<>();
        // 记录当前chunk的总字符长度。
        int currentLength = 0;

        // 遍历段落中的每一个句子。
        for (int i = 0; i < allSentences.size(); i++) {
            String sentence = allSentences.get(i);
            int sentenceLength = sentence.length();

            // 判断是否可以将当前句子添加到正在构建的chunk中。
            // 条件：1. 添加后总长度不超过CHUNK_SIZE。 2. 或者，当前chunk是空的（确保即使单个句子超长也能被独立添加）。
            if (currentLength + sentenceLength <= CHUNK_SIZE || currentChunkSentences.isEmpty()) {
                // 将句子添加到当前chunk的句子列表中。
                currentChunkSentences.add(sentence);
                // 更新当前chunk的总长度。
                currentLength += sentenceLength;
            } else {
                // --- 完成并存储一个Chunk ---
                // 如果添加当前句子会超长，则说明前一个chunk已经构建完成。
                // 使用换行符将当前chunk中的所有句子连接成一个字符串，并添加到结果列表中。
                chunks.add(String.join("", currentChunkSentences));

                // --- 实现块间重叠 (Overlap) ---
                // 计算重叠部分的起始句子索引。
                // 从当前已满chunk的句子列表中，向前取OVERLAP_SENTENCES个句子作为下一个chunk的开头。
                int overlapStartIndex = Math.max(0, currentChunkSentences.size() - OVERLAP_SENTENCES);
                // 提取出这些用于重叠的句子。
                List<String> overlapSentences = currentChunkSentences.subList(overlapStartIndex, currentChunkSentences.size());

                // --- 重置并开始新的Chunk ---
                // 创建一个新的列表，以重叠的句子作为初始内容。
                currentChunkSentences = new ArrayList<>(overlapSentences);
                // 将导致“溢出”的当前句子添加到这个新的chunk中。
                currentChunkSentences.add(sentence);
                // 重新计算新chunk的初始长度。
                currentLength = currentChunkSentences.stream().mapToInt(String::length).sum();
            }
        }

        // --- 处理最后一个Chunk ---
        // 循环结束后，处理剩余在currentChunkSentences中的最后一个文本块。
        if (!currentChunkSentences.isEmpty()) {
            // 将其连接成字符串并添加到结果列表中。
            chunks.add(String.join("", currentChunkSentences));
        }

        // 返回从这个段落生成的所有文本块。
        return chunks;
    }
}
