package com.onrender.zipai.web;

import com.onrender.zipai.service.CrawlerExecutionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/collection-runs")
public class CrawlerExecutionController {
    private final CrawlerExecutionService runs;
    public CrawlerExecutionController(CrawlerExecutionService runs){this.runs=runs;}
    @PostMapping
    public Map<String,Object> record(@RequestHeader(name="X-Property-Import-Token",required=false) String token,
                                     @RequestHeader(name="X-Finance-Import-Token",required=false) String financeToken,
                                     @RequestBody Map<String,Object> body){
        authenticate(token,financeToken,String.valueOf(body.get("category")));
        runs.record(body);return Map.of("success",true);
    }
    @GetMapping("/check")
    public Map<String,Object> check(@RequestHeader(name="X-Property-Import-Token",required=false) String token,
                                    @RequestHeader(name="X-Finance-Import-Token",required=false) String financeToken,
                                    @RequestParam(name="category") String category) {
        if(!java.util.Set.of("market","property","lh","finance","lifestyle").contains(category))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"수집 항목을 확인해 주세요.");
        authenticate(token,financeToken,category);
        return Map.of("success",true);
    }
    private static void authenticate(String token,String financeToken,String category) {
        boolean finance="finance".equals(category);
        String expected=System.getenv(finance?"FINANCE_IMPORT_TOKEN":"PROPERTY_IMPORT_TOKEN");
        if(finance) token=financeToken;
        if(expected==null || expected.isBlank() || token==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"수집 이력 인증을 확인해 주세요.");
    }

}
