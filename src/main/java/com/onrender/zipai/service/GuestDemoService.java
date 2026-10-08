package com.onrender.zipai.service;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Guest operations use only the visitor's session, never operating tables. */
@Service
public class GuestDemoService {
    private static final String KEY="ZIPAI_GUEST_DEMO";
    private static final Set<String> MODULES=Set.of("members","properties","favorites","visits","community","inquiries","finance","crawl","audit");
    private final ZipaiPasswordService passwords;
    public GuestDemoService(ZipaiPasswordService passwords) { this.passwords=passwords; }

    private static class State implements Serializable {
        private static final long serialVersionUID=1L;
        long nextId=100;
        Map<String,List<Map<String,Object>>> modules=new LinkedHashMap<>();
        Map<String,Object> account=new LinkedHashMap<>();
        String passwordHash;
        boolean active=true;
    }

    private State state(HttpSession session) {
        synchronized(session) {
            Object value=session.getAttribute(KEY);
            if(value instanceof State state) return state;
            State state=new State();
            for(String module:List.of("members","properties","favorites","visits","community","inquiries","finance","crawl","audit")) {
                String title=switch(module) {
                    case "members" -> "체험 회원"; case "properties" -> "수원 체험 매물";
                    case "favorites" -> "관심 있는 체험 매물"; case "visits" -> "체험 방문 신청";case "community" -> "청년 주거 이야기";
                    case "inquiries" -> "체험 문의";case "finance" -> "체험 금융 정책";
                    case "crawl" -> "아파트 실거래 수집 예시";default -> "체험 시작";
                };
                state.modules.put(module,new ArrayList<>(List.of(row(state.nextId++,title,"pending","실제 운영 데이터와 분리된 예시입니다."))));
            }
            state.modules.get("crawl").clear();
            for (String title : List.of("아파트 실거래", "매물 수집", "LH 공고", "금융 정책")) {
                state.modules.get("crawl").add(row(state.nextId++,title,"completed","체험 예시: 수집·반영 완료. 실제 실행 기록이 아닙니다."));
            }
            state.account.put("id","demo_user");state.account.put("email","demo@example.com");state.account.put("phone","01000000000");
            state.passwordHash=passwords.encode("Demo1234!");
            session.setAttribute(KEY,state);
            return state;
        }
    }

    public Map<String,Object> view(HttpSession session) {
        State state=state(session);
        synchronized(state) {
            Map<String,Object> modules=new LinkedHashMap<>();
            state.modules.forEach((key,rows)->modules.put(key,rows.stream().map(LinkedHashMap::new).toList()));
            return Map.of("modules",modules,"account",new LinkedHashMap<>(state.account),"active",state.active);
        }
    }

    public Map<String,Object> mutate(HttpSession session,String module,String action,Map<String,Object> body) {
        if(!MODULES.contains(module)) throw bad("체험 항목을 확인해 주세요.");
        State state=state(session);
        synchronized(state) {
            var rows=state.modules.get(module);
            if(action.equals("create")) {
                if(rows.size()>=100) throw bad("체험 항목은 최대 100개입니다. 초기화 후 다시 이용해 주세요.");
                String title=text(body,"title",100);if(title.isBlank()) throw bad("제목을 입력해 주세요.");
                rows.add(row(state.nextId++,title,text(body,"status",30),text(body,"detail",1000)));
            } else {
                long id;
                try { id=Long.parseLong(String.valueOf(body.get("id"))); }
                catch(RuntimeException e) { throw bad("항목 번호를 확인해 주세요."); }
                var item=rows.stream().filter(r->((Number)r.get("id")).longValue()==id).findFirst()
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"체험 항목이 없습니다."));
                if(action.equals("delete")) rows.remove(item);
                else if(action.equals("update")) {
                    String title=text(body,"title",100);
                    String status=text(body,"status",30), detail=text(body,"detail",1000);
                    if(!title.isBlank()) item.put("title",title);
                    item.put("status",status);
                    if(module.equals("inquiries") && status.equals("answered")) item.put("reply",detail);
                    else item.put("detail",detail);
                } else throw bad("체험 동작을 확인해 주세요.");
            }
            if(!module.equals("audit")) {
                var audit=state.modules.get("audit");
                audit.add(0,row(state.nextId++,"체험 변경: "+module,action,"현재 방문자의 데모 데이터만 변경했습니다."));
                if(audit.size()>100) audit.remove(audit.size()-1);
            }
        }
        return view(session);
    }

    public Map<String,Object> account(HttpSession session,String action,Map<String,Object> body) {
        State state=state(session);
        synchronized(state) {
            String password=String.valueOf(body.getOrDefault("password",""));
            if(action.equals("signup")) {
                String id=text(body,"userId",20);validateId(id);validatePassword(password);
                String email=email(body), phone=phone(body), hash=passwords.encode(password);
                state.account.put("id",id);state.account.put("email",email);state.account.put("phone",phone);
                state.passwordHash=hash;state.active=true;
            } else if(action.equals("login")) {
                if(!state.active || !state.account.get("id").equals(text(body,"userId",20)) || !passwords.matches(password,state.passwordHash))
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"체험 아이디·비밀번호를 확인해 주세요.");
            } else {
                if(!state.active) throw bad("체험 회원가입을 먼저 진행해 주세요.");
                if(!passwords.matches(password,state.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"현재 체험 비밀번호를 확인해 주세요.");
                switch(action) {
                    case "profile" -> { String email=email(body), phone=phone(body);state.account.put("email",email);state.account.put("phone",phone); }
                    case "username" -> {String id=text(body,"userId",20);validateId(id);state.account.put("id",id);}
                    case "password" -> {String next=String.valueOf(body.getOrDefault("newPassword",""));validatePassword(next);state.passwordHash=passwords.encode(next);}
                    case "withdraw" -> {state.active=false;state.account.clear();}
                    default -> throw bad("계정 체험 동작을 확인해 주세요.");
                }
            }
        }
        return view(session);
    }
    public void reset(HttpSession session) { session.removeAttribute(KEY); }
    private static Map<String,Object> row(long id,String title,String status,String detail) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("title",title);row.put("status",status);row.put("detail",detail);row.put("createdAt",LocalDateTime.now().toString());return row;
    }
    private static String text(Map<String,Object> body,String key,int max) {
        String value=String.valueOf(body.getOrDefault(key,"")).trim();
        if(value.length()>max) throw bad("입력 길이를 확인해 주세요.");return value;
    }
    private static String email(Map<String,Object> body) {String value=text(body,"email",254);if(!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("이메일을 확인해 주세요.");return value;}
    private static String phone(Map<String,Object> body) {String value=text(body,"phone",30).replaceAll("[^0-9]","");if(!value.matches("[0-9]{10,11}")) throw bad("전화번호를 확인해 주세요.");return value;}
    private static void validateId(String value) {if(!value.matches("[A-Za-z0-9_가-힣]{4,20}")) throw bad("아이디는 4~20자입니다.");}
    private static void validatePassword(String value) {
        if(value.length()<8 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !value.matches(".*[A-Za-z].*") || !value.matches(".*[0-9].*") || !value.matches(".*[^A-Za-z0-9].*")) throw bad("비밀번호는 8자 이상, 영문·숫자·특수문자를 포함하고 72바이트 이하여야 합니다.");
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
