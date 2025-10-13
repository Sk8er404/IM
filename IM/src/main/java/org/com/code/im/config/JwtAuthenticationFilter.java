package org.com.code.im.config;

import io.jsonwebtoken.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.com.code.im.mapper.UserMapper;
import org.com.code.im.utils.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collection;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;

    @Autowired
    public JwtAuthenticationFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    //使用filter中的init()方法来在filter的生命周期中我们手动注入需要使用的Service；

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException, java.io.IOException {

        long userId;

        // 1. 提取请求头中的JWT令牌
        String token = request.getHeader("token");

        // 2. 验证请求头中的JWT令牌
        try {
            /**
             * 如果令牌无效，则抛出异常，filterChain.doFilter把网页请求放行,这里也就是request和response放行
             * ( filterChain.doFilter(request, response); 的作用是确保请求能够继续沿着过滤器链传递，最终到达目标控制器。它是过滤器链机制的核心部分，不可或缺。
             * 如果省略这行代码，请求会被拦截在当前过滤器，导致后续逻辑无法执行。)
             * 此时SecurityContextHolder里面没有用户账号信息的完整上下文,
             * 所以之后就会**自动触发**的 表单登录的滤器链 ,登录成功后则会生成一个新的上下文对象存储到SecurityContextHolder中
             */
            userId=JWTUtils.checkToken(token);
        }catch (Exception e){
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 如果令牌有效，则设置用户信息到SecurityContextHolder中
        //从数据库中查询用户的权限信息,然后将它存储到Security的上下文中
        Collection<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(userMapper.getAuth(userId)));

        //将JWT包含的用户的id信息打包到UserDetails中
        UserDetails userDetails = new User(String.valueOf(userId),"null",authorities);

        //将UserDetails信息打包到UsernamePasswordAuthenticationToken中
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, "null", authorities);

        //将UsernamePasswordAuthenticationToken信息打包到SecurityContextHolder中
        //SecurityContextHolder.getContext().setAuthentication()将完整的认证对象存储到线程绑定的安全上下文中。
        //此后在任何控制器或服务层，都能通过SecurityContextHolder获取当前用户身份
        SecurityContextHolder.getContext().setAuthentication(authentication);

        /**
         * 如果令牌有效，则设置用户信息到SecurityContextHolder中,
         * 然后filterChain.doFilter(request, response)把网页请求放行给后面的过滤器链
         * 然后后面的表单登录过滤器链发现有SecurityContextHolder里面有用户账号信息的完整上下文,
         * 然后就会表单登录过滤器直接放行,就不用表单登录了
         */
        filterChain.doFilter(request, response);
    }
}