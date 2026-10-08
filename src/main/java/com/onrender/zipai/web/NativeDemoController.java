package com.onrender.zipai.web;

import com.onrender.zipai.service.NativeDemoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NativeDemoController {
    private final NativeDemoService demo;
    public NativeDemoController(NativeDemoService demo) {this.demo=demo;}
    @GetMapping("/demo/start")
    public String start(@RequestParam(defaultValue="user") String mode,HttpServletRequest request) {
        var session=request.getSession(true);
        demo.start(session,mode);
        session.removeAttribute("SPRING_SECURITY_CONTEXT");
        request.changeSessionId();
        return "redirect:"+(mode.equals("admin")?"/admin":"/");
    }
    @GetMapping("/demo/end")
    public String end(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session!=null) {demo.end(session);request.changeSessionId();}
        return "redirect:/member/login";
    }
}
