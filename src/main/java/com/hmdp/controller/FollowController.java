package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")   //id为要关注的用户id，isFollow为是否关注
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return  followService.follow(followUserId, isFollow);
    }

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")   //id为要关注的用户id，isFollow为是否关注
    public Result followOrNot(@PathVariable("id") Long followUserId) {
        return  followService.followOrNot(followUserId);
    }

    /**
     * 获取共同关注
     * @param userId
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long userId) {
        return followService.followCommon(userId);
    }
}
