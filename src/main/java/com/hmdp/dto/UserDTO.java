package com.hmdp.dto;

import lombok.Data;
//用于前后端交互，只包含需要暴露给前端的字段，更安全、更灵活
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
