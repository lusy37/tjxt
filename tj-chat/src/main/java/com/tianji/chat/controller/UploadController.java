package com.tianji.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 负责处理上传相关页面的控制器
 */
@Controller
public class UploadController {

    /**
     * 返回Markdown上传页面
     * @return 页面名称
     */
    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }
}