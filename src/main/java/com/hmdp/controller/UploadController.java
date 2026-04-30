package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /**
     * 图片上传
     * 关联上传图片按键
     * @param image
     * @return
     */
    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));   //File能自动处理多余的斜杠
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);   //前端拿到这个fileName后，通常会拼接成访问地址
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除图片
     * 关联图片右上角×按键
     * @param filename
     * @return
     */
    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 处理前端传来的路径，去掉开头的 /imgs 前缀
        String relativePath = filename;
        if (filename.startsWith("/imgs/")) {
            relativePath = filename.substring(6); // 去掉 "/imgs/" 前缀，保留 "blogs/13/7/xxx.png"
        } else if (filename.startsWith("/")) {
            relativePath = filename.substring(1); // 去掉开头的 "/"
        }
        
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, relativePath);
        if (file.isDirectory()) {   //检查file是否是一个目录,防止误删目录
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    //这段代码通过生成基于UUID的唯一文件名，并根据文件名的哈希值创建分层目录结构，确保文件存储时不会发生冲突，并且文件目录结构更加有序。生成的路径包含了两级目录，能够有效管理大量文件
    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();   //生成一个唯一的字符串（UUID），作为文件的基础名称
        int hash = name.hashCode();
        int d1 = hash & 0xF;   // 取hash值的低4位
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
