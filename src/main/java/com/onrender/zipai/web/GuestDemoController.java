package com.onrender.zipai.web;

import com.onrender.zipai.service.GuestDemoService;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class GuestDemoController {
    private static final Set<String> USER_MODULES=Set.of("properties","favorites","visits","community","inquiries");
    private final GuestDemoService demo;
    public GuestDemoController(GuestDemoService demo) {this.demo=demo;}
    @GetMapping("/demo") public String page() {return "redirect:/demo/start?mode=user";}
    @GetMapping("/api/demo") @ResponseBody
    public Map<String,Object> view(@RequestParam(defaultValue="user") String mode,HttpSession session) {
        return filtered(demo.view(session),mode);
    }
    @PostMapping("/api/demo/{module}/{action}") @ResponseBody
    public Map<String,Object> change(@PathVariable String module,@PathVariable String action,
                                    @RequestParam(defaultValue="user") String mode,
                                    @RequestBody Map<String,Object> body,HttpSession session) {
        validateMode(mode);
        if(mode.equals("user") && !module.equals("account")) {
            if(!USER_MODULES.contains(module)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"관리자 체험 모드로 전환해 주세요.");
            String status=String.valueOf(body.getOrDefault("status","pending"));
            if(!action.equals("delete")) {
                Set<String> allowed=module.equals("visits")?Set.of("pending","cancelled")
                    :module.equals("inquiries")?Set.of("pending"):Set.of("pending","active","closed");
                if(!allowed.contains(status) || module.equals("inquiries") && action.equals("update"))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"승인·답변은 관리자 체험 모드에서 진행해 주세요.");
            }
        }
        Map<String,Object> result=module.equals("account")?demo.account(session,action,body):demo.mutate(session,module,action,body);
        return filtered(result,mode);
    }
    @PostMapping("/api/demo/reset") @ResponseBody
    public Map<String,Object> reset(@RequestParam(defaultValue="user") String mode,HttpSession session) {
        validateMode(mode);demo.reset(session);return filtered(demo.view(session),mode);
    }
    private static void validateMode(String mode) {
        if(!mode.equals("user") && !mode.equals("admin")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"체험 모드를 확인해 주세요.");
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> filtered(Map<String,Object> result,String mode) {
        validateMode(mode);
        Map<String,Object> filtered=new LinkedHashMap<>(result);
        Map<String,Object> modules=new LinkedHashMap<>((Map<String,Object>)result.get("modules"));
        if(mode.equals("user")) modules.keySet().retainAll(USER_MODULES);
        filtered.put("modules",modules);filtered.put("mode",mode);return filtered;
    }
}
