package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
class UserTokenGeneratorTest {

    // 直接修改这里即可指定 tokens.txt 的生成位置，支持绝对路径和相对路径
    private static final String TOKEN_FILE_PATH = "D:\\tempdata\\BaiduNetdiskDownload\\7、Redis入门到实战教程\\Redis-笔记资料\\02-实战篇\\资料\\tokens.txt";

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void generateTokens() throws IOException {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().orderByAsc("id"));
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("tb_user 表中没有可用用户");
        }

        Path tokenFile = resolveTokenFile();
        createParentDirectory(tokenFile);

        try (BufferedWriter writer = Files.newBufferedWriter(
                tokenFile,
                StandardCharsets.UTF_8
        )) {
            for (User user : users) {
                String token = loginUserToRedis(user);
                writer.write(token);
                writer.newLine();
            }
        }

        System.out.println("已生成 " + users.size() + " 个 token，文件路径：" + tokenFile.toAbsolutePath());
    }

    private Path resolveTokenFile() {
        return Paths.get(TOKEN_FILE_PATH);
    }

    private void createParentDirectory(Path tokenFile) throws IOException {
        Path parent = tokenFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String loginUserToRedis(User user) {
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }
}
