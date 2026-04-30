package com.hmdp.controller;


import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 保存博文并推送给粉丝
     * 关联发布按键
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.blogSave(blog);
    }

    /**
     * 点赞或取消点赞功能
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 获取当前用户所有笔记展示在用户主页
     * @param current
     * @return
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 按点赞数从多到少分页获取笔记
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 根据id查询博客
     * 关联点击博客查看详情操作
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 获取笔记top5点赞用户
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 在他人主页按用户id查询用户发过的博客
     * @param current
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {

        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 滚动获取当前用户所关注的用户所发过的笔记
     * @param max
     * @param offset
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Long offset){

        return blogService.queryBlogOfFollow(max, offset);
    }
//    /**
//     * 删除博客图片
//     * @param filename 文件名
//     * @return 结果
//     */
//    @GetMapping("/delete")
//    public Result deleteBlogImg(@RequestParam("name") String filename) {
//        log.info("开始删除图片，文件名：{}", filename);
//
//        // 拼接完整文件路径
//        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
//        log.info("完整文件路径：{}", file.getAbsolutePath());
//
//        // 检查文件是否存在
//        if (!file.exists()) {
//            log.warn("文件不存在：{}", file.getAbsolutePath());
//            return Result.fail("文件不存在");
//        }
//
//        // 检查是否是目录，防止误删目录
//        if (file.isDirectory()) {
//            log.warn("尝试删除目录：{}", file.getAbsolutePath());
//            return Result.fail("错误的文件名称");
//        }
//
//        // 执行删除
//        boolean deleted = FileUtil.del(file);
//        if (deleted) {
//            log.info("文件删除成功：{}", file.getAbsolutePath());
//            return Result.ok();
//        } else {
//            log.error("文件删除失败：{}", file.getAbsolutePath());
//            return Result.fail("文件删除失败");
//        }
//    }

}
