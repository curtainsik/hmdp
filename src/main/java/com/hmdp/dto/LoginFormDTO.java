package com.hmdp.dto;

import lombok.Data;

//数据传输对象
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
