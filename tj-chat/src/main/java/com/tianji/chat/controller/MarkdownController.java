package com.tianji.chat.controller;

import com.tianji.chat.domain.dto.MarkdownDocs;
import com.tianji.chat.service.IMarkdownDocsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
public class MarkdownController {

    private final IMarkdownDocsService markdownDocsService;

    @PostMapping("/upload-markdown")
    public String uploadMarkdown(@RequestParam MultipartFile file,
                                            @RequestParam(defaultValue="2") Integer level) {

        return markdownDocsService.upload(file, level);

    }

    @GetMapping("/get-markdown")
    public String getMarkdown(@RequestParam Long fileId) {

        return markdownDocsService.getMarkdown(fileId);

    }

    @PutMapping("/update-markdown")
    public String updateMarkdown(@RequestBody MarkdownDocs markdownDocs) {

        return markdownDocsService.updateMarkdown(markdownDocs);

    }

    @DeleteMapping("/delete-markdown")
    public String deleteMarkkdown(@RequestParam Long fileId) {

        return markdownDocsService.removeMarkdown(fileId);

    }

}
