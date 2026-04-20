package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
//Tomcat为每个请求分配一个线程
//从拦截器到控制器再到响应，整个过程都在同一个线程中
//如果没有ThreadLocal，每个线程访问同一个变量，线程不安全；如果有ThreadLocal，每个线程访问的都是自己线程的副本变量，线程安全
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
