package com.zipai.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipai.domain.*;
import com.zipai.repository.*;
import com.zipai.service.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final PropertyRepository properties;private final PostRepository posts;private final AuthService auth;
    private final NotificationService notices;private final ObjectMapper json;
    public AdminController(PropertyRepository p,PostRepository r,AuthService a,NotificationService n,ObjectMapper j){properties=p;posts=r;auth=a;notices=n;json=j;}
    @GetMapping("/properties")Map<String,Object> properties(HttpSession s){auth.admin(s);
        return Map.of("items",properties.findAllByOrderByIdDesc().stream().map(this::property).toList());}
    @PatchMapping("/properties/{id}/status")
    Map<String,Object> property(@PathVariable Long id,@RequestBody Map<String,Object>b,HttpSession s){auth.admin(s);
        String status=String.valueOf(b.getOrDefault("status",""));
        if(!Set.of("approved","rejected","closed").contains(status))throw new ResponseStatusException(BAD_REQUEST,"매물 처리 상태를 확인해 주세요.");
        PropertyListing p=properties.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"매물을 찾을 수 없습니다."));
        p.status=status;properties.save(p);String title=String.valueOf(property(p).getOrDefault("title","매물 #"+p.id));
        String label=status.equals("approved")?"승인":status.equals("rejected")?"거절":"종료";
        notices.create(p.owner,"property_review","매물 검토 결과",title+" 매물이 "+label+" 처리되었습니다.","property",p.id);
        return Map.of("success",true,"status",status);}
    @GetMapping("/community/posts")Map<String,Object> posts(HttpSession s){auth.admin(s);
        return Map.of("items",posts.findAllByOrderByIdDesc().stream().map(p->Map.of("id",p.id,"title",p.title,
            "category",p.category,"content",p.content,"username",p.author.username,"created_at",p.createdAt)).toList());}
    @DeleteMapping("/community/posts/{id}")Map<String,Object> delete(@PathVariable Long id,HttpSession s){auth.admin(s);
        if(!posts.existsById(id))throw new ResponseStatusException(NOT_FOUND,"게시글을 찾을 수 없습니다.");posts.deleteById(id);return Map.of("success",true);}
    private Map<String,Object> property(PropertyListing p){try{Map<String,Object>m=new LinkedHashMap<>(json.readValue(p.payloadJson,new TypeReference<>(){}));
        m.put("id",p.id);m.put("ownerId",p.owner.id);m.put("owner",p.owner.username);m.put("status",p.status);m.put("createdAt",p.createdAt);return m;
        }catch(Exception e){throw new IllegalStateException(e);}}
}
