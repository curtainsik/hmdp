package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private IUserService userService;

    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 判断是关注还是取关
        if(isFollow){
            // 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            
            // 如果数据库保存成功，同步到Redis
            if(success){
                // Redis key: follows:userId, value: Set{followUserId1, followUserId2, ...}
                String key = FOLLOW_USER_KEY + userId;
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            // 取关，删除数据，delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean success = remove(new QueryWrapper<Follow>()   //QueryWrapper<Follow>是一个条件构造器，用于构建查询条件
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            
            // 如果数据库删除成功，同步从Redis中移除
            if(success){
                String key = FOLLOW_USER_KEY + userId;
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result followOrNot(Long followUserId) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        
        // 先从Redis中查询：检查userId的关注集合中是否包含followUserId
        String key = FOLLOW_USER_KEY + userId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        
        // 如果Redis中有数据，直接返回；否则查数据库
        if(isMember != null && isMember){
            return Result.ok(true);
        }
        
        // Redis中没有，查数据库
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
            
        // 如果 count > 0，说明已关注，返回 true；否则返回 false
        return Result.ok(count > 0);
    }

    /**
     * 查询共同关注
     * @param userId 目标用户ID
     * @return 共同关注的用户信息列表（UserDTO）
     */
    @Override
    public Result followCommon(Long userId) {
        // 获取当前登录用户
        Long myId = UserHolder.getUser().getId();
        
        // 构建两个key
        String myKey = FOLLOW_USER_KEY + myId;      // 我关注的集合
        String otherKey = FOLLOW_USER_KEY + userId; // 对方关注的集合
        
        // 求交集：sinter key1 key2，返回两个集合的共同元素（用户ID）
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(myKey, otherKey);
        
        // 如果没有共同关注，直接返回空列表
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        
        // 将Set<String>转换为List<Long>
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)   //把流里的每一个字符串，都调用Long的静态方法Long.valueOf转成Long对象
                .collect(Collectors.toList());
        
        // 根据ID批量查询用户信息
        List<User> users = userService.listByIds(ids);
        
        // 将User对象转换为UserDTO对象（只返回id、nickName、icon）
        List<UserDTO> userDTOS = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        
        // 返回共同关注的用户信息列表
        return Result.ok(userDTOS);
    }
}
