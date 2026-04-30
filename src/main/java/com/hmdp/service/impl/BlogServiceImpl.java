package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    /**
     * 根据id查询博客
     * 关联点击博客查看详情操作
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);   //为博客补全发帖用户的名字和头像信息
        isBlogLiked(blog);   //为博客补全当前用户是否点赞状态
        return Result.ok(blog);
    }

    /**
     * 按点赞数从多到少分页获取笔记
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")   //按点赞数(liked)从高到低排序
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();   //拿到这一页的所有博客
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);   //为博客补全发帖用户的名字和头像信息
            this.isBlogLiked(blog);   //为博客补全当前用户是否点赞状态
        });
        return Result.ok(records);
    }

    /**
     * 点赞或取消点赞功能
     * @param id
     * @return 响应结果
     */
    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        //score判断某个元素是否存在于指定的集合中
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            //如果未点赞，可以点赞
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                //保存用户到redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //如果已点赞，取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                //保存用户到redis
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    /**
     * 获取笔记top5点赞用户
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 按时间查询top5的点赞用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok();
        }
        // 解析出用户id（保持时间顺序）
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据用户id查询用户
        List<User> users = userService.listByIds(ids);
        // 将查询结果转为 Map，key 为用户ID，value 为用户对象
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 按照原来的 ids 顺序重新排列，保证返回的用户顺序与点赞时间顺序一致
        List<UserDTO> UserDTOS = ids.stream()
                .map(userId -> {
                    User user = userMap.get(userId);
                    return BeanUtil.copyProperties(user, UserDTO.class);
                })
                .collect(Collectors.toList());
        return Result.ok(UserDTOS);
    }

    /**
     * 保存博文并推送给粉丝
     * 关联发布按键
     * @param blog
     * @return
     */
    @Override
    public Result blogSave(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("笔记保存失败");
        }
        // 查询博客作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记id给所有粉丝
        for(Follow follow : follows){
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 滚动获取当前用户所关注的用户所发过的笔记
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Long offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.获取id
            ids.add(Long.valueOf(tuple.getValue()));   //Long.valueOf作用为转换为Long包装类
            // 4.2.获取分数(时间戳），for循环执行完即为最后一个时间
            long time = tuple.getScore().longValue();   //longValue()作用为转换为long基本类型
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
//        os = minTime == max ? os : os + offset;
        // 5.根据id查询blog，让数据库查询出来的结果，按照指定的顺序排列
        String idStr = StrUtil.join(",", ids);   //将ids里面的blogid用逗号连接起来
        //last允许在生成的SQL语句末尾强行追加一段自定义的SQL，：FIELD是mysql的一个函数，用于将字段值按照指定的顺序进行排序，
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.补全发帖用户的名字和头像信息
            queryBlogUser(blog);
            // 5.2.补全当前用户是否点赞了该笔记的信息
            isBlogLiked(blog);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }


    //为博客补全发帖用户的名字和头像信息，以上传到前端的博客中展示用户名和头像
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


    //补全当前用户是否点赞了该笔记的信息
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        // 保证用户即使未登录也能访问到笔记
        if(user == null){
            return;
        }
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
