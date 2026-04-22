package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range("ShopType", 0, -1);
        //CollUtil.isNotEmpty判断集合是否非空
        if(CollUtil.isNotEmpty(shopTypeJsonList)){
            List<ShopType> shopTypeList = shopTypeJsonList
                    .stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))   //映射规则
                    .collect(Collectors.toList());   //把处理后的结果重新收集成List
            return Result.ok(shopTypeList);
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();   //按sort字段升序查询所有ShopType数据
        if(shopTypeList == null){
            return Result.fail("没有数据");
        }

        List<String> jsonList = shopTypeList
                .stream()
                .map(JSONUtil::toJsonStr)   //也可写为map(json -> JSONUtil.toJsonStr(json))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll("ShopType", jsonList);
        return Result.ok(shopTypeList);
    }
}
