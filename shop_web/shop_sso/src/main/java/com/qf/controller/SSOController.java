package com.qf.controller;

import com.qf.entity.Email;
import com.qf.entity.ResultData;
import com.qf.entity.User;
import com.qf.service.IUserService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/sso")
public class SSOController {

    @Autowired
    private IUserService userService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 去到注册页面
     * @return
     */
    @RequestMapping("/toRegister")
    public String toRegister(){
        return "register";
    }

    /**
     * 去到登录页面
     * @return
     */
    @RequestMapping("/toLogin")
    public String toLogin(){
        return "login";
    }

    /**
     * 注册用户
     * @return
     */
    @RequestMapping("/register")
    public String register(User user, Model model){

        //调用后台服务进行注册
        int result = userService.register(user);

        if(result > 0){
            //注册成功跳转到登录页面
            return "login";
        }

        if (result == -1) {
            model.addAttribute("error", "用户名已经存在!");
        } else {
            model.addAttribute("error", "注册失败，情联系管理员!");
        }

        return "register";
    }

    /**
     * 跳转到忘记密码的页面
     * @return
     */
    @RequestMapping("/toForgetPassword")
    public String toForgetPassword(){
        return "forgetpassword";
    }

    /**
     * 找回密码
     * @return
     */
    @RequestMapping("/findPassword")
    @ResponseBody
    public ResultData findPassword(String username){
        System.out.println("需要找回密码的账号：" + username);

        //调用业务层通过用户名找到用户信息 - 用户的邮箱
        User user = userService.queryByUserName(username);
        if(user == null){
            //说明用户名不存在
            return new ResultData().setCode("1000").setMsg("用户名不存在！");
        }

        //再给用户的邮箱发送一个找回密码的邮件
        //1、时效性
        //1、一次性
        //1、不能拿过来改其他人的密码
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(token, username);
        stringRedisTemplate.expire(token, 5, TimeUnit.MINUTES);

        String url = "http://localhost:16666/sso/toUpdatePassword?token=" + token;
        String content = "点击如下链接，找回密码：<a href='" + url + "'>" + url + "</a>";


        Email email = new Email()
                .setSubject("京北商城找回密码邮件！")
                .setTo(user.getEmail())
                .setContent(content);
        rabbitTemplate.convertAndSend("mail_exchange", "", email);

        //准备参数
        Map<String, String> data = new HashMap<>();

        //获得邮箱中间的内容
        String emailMiddleInfo = user.getEmail().substring(3, user.getEmail().indexOf("@"));
        String mailInfo = user.getEmail().replace(emailMiddleInfo, "******");
        data.put("mailInfo", mailInfo);

        //获得邮箱的跳转页面
        String emailUrl = "http://mail." + user.getEmail().substring(user.getEmail().indexOf("@") + 1);
        data.put("emailUrl", emailUrl);

        return new ResultData().setCode("0000").setMsg("邮件发送成功！").setData(data);
    }

    /**
     * 跳转到修改密码的页面
     * @return
     */
    @RequestMapping("/toUpdatePassword")
    public String toUpdatePassword(){
        return "updatepassword";
    }

    /**
     * 进行密码的修改
     * @return
     */
    @RequestMapping("/updatePassword")
    public String updatePasssword(String token, String newpassword){

        //从redis中获得token对应的用户名
        String username = stringRedisTemplate.opsForValue().get(token);

        if(username != null){
            //可以正常修改密码
            int result = userService.updatePasswordByUserName(username, newpassword);

            if(result > 0){
                //删除redis
                stringRedisTemplate.delete(token);

                return "login";
            }
        }

        return "updateError";
    }
}