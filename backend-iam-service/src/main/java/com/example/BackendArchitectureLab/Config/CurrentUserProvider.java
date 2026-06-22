package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.annotation.RequestScope;

import java.lang.reflect.Method;

@Configuration
public class CurrentUserProvider {

    @Bean
    @RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        try {
            Method getUser = principal.getClass().getMethod("getUser");
            Object result = getUser.invoke(principal);
            return result instanceof User ? (User) result : null;
        } catch (Exception e) {
            return null;
        }
    }

}
