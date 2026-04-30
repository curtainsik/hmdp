package com.hmdp.dto;

import lombok.Data;

import java.util.List;
// 滚动分页查询返回的实体类
@Data
public class ScrollResult {
    private List<?> list;   //小于指定时间戳的笔记集合
    private Long minTime;   //本次查询推送的最小时间戳
    private Integer offset;   //偏移量
}
