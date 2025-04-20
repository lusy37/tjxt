package com.tianji.chat.utils;

import cn.hutool.core.util.StrUtil;
import com.tianji.chat.domain.po.MarkdownChunk;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MarkdownSplitter {

    // 解析 Markdown 为标题+正文块
    public static List<MarkdownChunk> splitByH2(String markdown) {
        return MarkdownSplitter.getMarkdownChunksByH(markdown, 2);
    }

    public static List<MarkdownChunk> splitByH3(String markdown) {
        return MarkdownSplitter.getMarkdownChunksByH(markdown, 3);
    }
    public static @NotNull List<MarkdownChunk> getMarkdownChunksByH(String markdown, int level) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        Node document = parser.parse(markdown);

        List<MarkdownChunk> chunks = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading) {
                Heading heading = (Heading) node; // 手动类型转换
                if (heading.getLevel() == level) {
                    if (currentTitle != null) {
                        chunks.add(new MarkdownChunk(currentTitle, currentContent.toString().trim()));
                        currentContent = new StringBuilder();
                    }
                    currentTitle = heading.getText().toString().trim(); // ← 转为 String
                }
            } else {
                BasedSequence nodeText = node.getChars();
                if (currentTitle != null) {
                    currentContent.append(nodeText.toString()).append("\n\n");
                }
            }
        }

        // 添加最后一个 chunk
        if (currentTitle != null && StrUtil.isNotEmpty(currentContent)) {
            chunks.add(new MarkdownChunk(currentTitle, currentContent.toString().trim()));
        }

        return chunks;
    }

}
