package com.onrender.zipai.config;

import com.onrender.zipai.service.NativeDemoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class NativeDemoFilter extends OncePerRequestFilter {
    private final NativeDemoService demo;
    private final ObjectMapper json;
    public NativeDemoFilter(NativeDemoService demo,ObjectMapper json) {this.demo=demo;this.json=json;}
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getRequestURI();
        var session=request.getSession(false);
        if(NativeDemoService.mode(session)==null || !path.startsWith("/api/") || path.startsWith("/api/demo")) {
            chain.doFilter(request,response);return;
        }
        if(demo.publicRead(path,request.getMethod())) {chain.doFilter(request,response);return;}
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        try {
            Map<String,Object> body=new LinkedHashMap<>();
            Map<String,String> query=new LinkedHashMap<>();
            String contentType=request.getContentType();
            if(contentType!=null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/form-data")) {
                demo.multipart(request,body);
            } else {
                request.getParameterMap().forEach((key,values)->{if(values.length>0)query.put(key,values[0]);});
                if(contentType!=null && contentType.contains("application/json")) {
                    byte[] bytes=request.getInputStream().readNBytes(1024*1024+1);
                    if(bytes.length>1024*1024) throw new IllegalArgumentException("체험 요청 크기는 1MB 이하입니다.");
                    if(bytes.length>0) body.putAll(json.readValue(new String(bytes,StandardCharsets.UTF_8),Map.class));
                }
            }
            Object result=demo.request(session,path,request.getMethod(),body,query);
            response.getWriter().write(json.writeValueAsString(result));
        } catch(ResponseStatusException error) {
            response.setStatus(error.getStatusCode().value());
            response.getWriter().write(json.writeValueAsString(Map.of("message",error.getReason()==null?"체험 요청을 확인해 주세요.":error.getReason())));
        } catch(IllegalArgumentException error) {
            response.setStatus(400);response.getWriter().write(json.writeValueAsString(Map.of("message","체험 입력 형식·크기를 확인해 주세요.")));
        } catch(Exception error) {
            response.setStatus(400);response.getWriter().write(json.writeValueAsString(Map.of("message","체험 요청을 처리하지 못했습니다. 입력 내용을 확인해 주세요.")));
        }
    }
}
