package com.zipai.web;

import com.zipai.domain.*;
import com.zipai.repository.InquiryRepository;
import com.zipai.service.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
public class InquiryController {
    private static final Set<String> CATEGORIES=Set.of("매물 이용","계약 안전","로그인·계정","서비스 오류","기타");
    private final InquiryRepository inquiries;private final AuthService auth;private final NotificationService notices;
    public InquiryController(InquiryRepository i,AuthService a,NotificationService n){inquiries=i;auth=a;notices=n;}
    @GetMapping("/api/inquiries")Map<String,Object> list(HttpSession s){User u=auth.required(s);
        return Map.of("items",inquiries.findByUserIdOrderByIdDesc(u.id).stream().map(this::view).toList());}
    @PostMapping("/api/inquiries")@ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> create(@RequestBody Map<String,Object>b,HttpSession s){User u=auth.required(s);
        String category=t(b,"category"),title=t(b,"title"),content=t(b,b.containsKey("message")?"message":"content"),email=t(b,"email").toLowerCase();
        if(!CATEGORIES.contains(category))throw new ResponseStatusException(BAD_REQUEST,"문의 유형을 확인해 주세요.");
        if(title.length()<2||title.length()>60)throw new ResponseStatusException(BAD_REQUEST,"제목은 2~60자로 입력해 주세요.");
        if(content.length()<5||content.length()>1000)throw new ResponseStatusException(BAD_REQUEST,"문의 내용은 5~1000자로 입력해 주세요.");
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||email.length()>254)throw new ResponseStatusException(BAD_REQUEST,"답변받을 이메일을 확인해 주세요.");
        Inquiry i=new Inquiry();i.user=u;i.category=category;i.title=title;i.content=content;i.contactEmail=email;inquiries.save(i);
        return Map.of("item",view(i));}
    @GetMapping("/api/inquiries/{id}")Map<String,Object> one(@PathVariable Long id,HttpSession s){User u=auth.required(s);
        Inquiry i=inquiries.findByIdAndUserId(id,u.id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"문의를 찾을 수 없습니다."));
        return Map.of("item",view(i));}
    @GetMapping("/api/admin/inquiries")Map<String,Object> adminList(HttpSession s){auth.admin(s);
        return Map.of("items",inquiries.findAllByOrderByIdDesc().stream().map(this::view).toList());}
    @PatchMapping("/api/admin/inquiries/{id}/answer")
    Map<String,Object> answer(@PathVariable Long id,@RequestBody Map<String,Object>b,HttpSession s){User admin=auth.admin(s);
        Inquiry i=inquiries.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"문의를 찾을 수 없습니다."));
        String status=t(b,"status"),answer=t(b,"answer");
        if(!Set.of("received","in_progress","answered").contains(status))throw new ResponseStatusException(BAD_REQUEST,"문의 처리 상태를 확인해 주세요.");
        if(answer.length()>3000||(status.equals("answered")&&answer.length()<2))throw new ResponseStatusException(BAD_REQUEST,"답변 완료 시 2~3000자의 답변을 입력해 주세요.");
        i.status=status;i.answer=answer;i.answeredBy=answer.isBlank()?null:admin;i.answeredAt=answer.isBlank()?null:LocalDateTime.now();inquiries.save(i);
        if(status.equals("answered"))notices.create(i.user,"inquiry_answer","문의 답변이 등록되었습니다.",i.title,"inquiry",i.id);
        return Map.of("item",view(i));}
    private Map<String,Object> view(Inquiry i){Map<String,Object>m=new LinkedHashMap<>();m.put("id",i.id);m.put("userId",i.user.id);
        m.put("username",i.user.username);m.put("category",i.category);m.put("title",i.title);m.put("message",i.content);
        m.put("email",i.contactEmail);m.put("status",i.status);m.put("answer",i.answer);m.put("answeredAt",i.answeredAt);
        m.put("createdAt",i.createdAt);m.put("updatedAt",i.updatedAt);return m;}
    private static String t(Map<String,Object>b,String k){return String.valueOf(b.getOrDefault(k,"")).trim();}
}
