package com.onrender.zipai.config;

import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MemberAccessConfig implements WebMvcConfigurer {
    private final ZipaiAuthService auth;
    public MemberAccessConfig(ZipaiAuthService auth) { this.auth = auth; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                var session = request.getSession(false);
                String demoMode = com.onrender.zipai.service.NativeDemoService.mode(session);
                if (demoMode != null) {
                    if (request.getRequestURI().equals("/admin") && !demoMode.equals("admin")) {
                        response.sendRedirect("/demo/start?mode=admin");
                        return false;
                    }
                    return true;
                }
                var user = session == null ? null : auth.current(session);
                if (request.getRequestURI().equals("/admin")) {
                    if (user == null) {
                        response.sendRedirect("/member/login?returnTo=%2Fadmin");
                        return false;
                    }
                    auth.admin(session);
                } else if (user == null) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"로그인 후 이용할 수 있습니다.\"}");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/admin", "/api/chat");
    }
}
