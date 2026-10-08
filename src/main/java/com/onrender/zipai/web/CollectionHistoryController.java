package com.onrender.zipai.web;

import com.onrender.zipai.service.CollectionHistoryService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/collection-history")
public class CollectionHistoryController {
    private final ZipaiAuthService auth;
    private final CollectionHistoryService history;
    public CollectionHistoryController(ZipaiAuthService auth,CollectionHistoryService history) { this.auth=auth; this.history=history; }
    @GetMapping
    public Map<String,Object> history(@RequestParam(defaultValue="market") String category,
                                      @RequestParam(defaultValue="0") int page,HttpSession session) {
        auth.admin(session);
        return Map.of("categories",history.categories(),"items",history.list(category,page),"page",Math.max(0,page));
    }
}
