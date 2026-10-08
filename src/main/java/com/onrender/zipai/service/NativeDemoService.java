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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Native screen DTOs backed only by the visitor's session. No repositories. */
@Service
public class NativeDemoService {
    public static final String MODE="ZIPAI_NATIVE_DEMO_MODE";
    private static final String DATA="ZIPAI_NATIVE_DEMO_DATA";
    private final ZipaiPasswordService passwords;
    private final PropertyListingService listings;
    public NativeDemoService(ZipaiPasswordService passwords) { this(passwords,null); }
    @Autowired
    public NativeDemoService(ZipaiPasswordService passwords,PropertyListingService listings) {
        this.passwords=passwords;this.listings=listings;
    }
    public static String mode(HttpSession session) {
        if(session==null) return null;
        Object mode=session.getAttribute(MODE);
        return "user".equals(mode)||"admin".equals(mode)?mode.toString():null;
    }
    public void start(HttpSession session,String mode) {
        if(!Set.of("user","admin").contains(mode)) throw bad("체험 모드를 확인해 주세요.");
        session.removeAttribute("ZIPAI_USER_ID");
        session.setAttribute(MODE,mode);state(session);
    }
    public void end(HttpSession session) { session.removeAttribute(MODE);session.removeAttribute(DATA); }
    private static class State implements Serializable {
        private static final long serialVersionUID=1L;
        long next=91000100;boolean listingsLoaded;
        Map<String,Object> account=new LinkedHashMap<>();String hash;
        List<Map<String,Object>> properties=new ArrayList<>(),visits=new ArrayList<>(),inquiries=new ArrayList<>(),posts=new ArrayList<>(),comments=new ArrayList<>(),offers=new ArrayList<>(),notifications=new ArrayList<>(),audit=new ArrayList<>(),finance=new ArrayList<>();
        List<Long> favorites=new ArrayList<>();Map<String,Object> diagnosis;
    }
    private State state(HttpSession session) {
        synchronized(session) {
            if(session.getAttribute(DATA) instanceof State existing) return existing;
            State s=new State();s.account.putAll(map("id","demo_user","email","demo@example.com","phone","01000000000"));s.hash=passwords.encode("Demo1234!");
            s.properties.add(property(91000001L,map("title","체험용 수원 월세 매물","address","경기도 수원시 장안구","sigungu","수원시","deposit",1000,"monthly",50,"area",30)));
            s.inquiries.add(map("id",91000002L,"category","기타","email","demo@example.com","title","체험 문의","message","이 문의는 체험 데이터입니다.","answer","","status","received","username","demo_user","createdAt",now(),"updatedAt",now()));
            s.visits.add(map("id",91000003L,"roomId","LISTING-91000001","title","체험용 수원 월세 매물","date",java.time.LocalDate.now().plusDays(1).toString(),"time","14:00","phone","01000000000","question","방을 보고 싶습니다.","status","pending","manageable",true));
            s.posts.add(post(91000004L,map("title","체험 게시글","content","로그인 없이 기존 화면에서 작성과 관리를 체험합니다.","category","free")));
            s.finance.add(map("id",91000005L,"policyId",91000005L,"policyName","체험 청년 주거 금융정책","category","청년","targetType","청년","limitInfo","체험 한도","rateInfo","체험 금리","description","실제 정책이 아닌 체험 예시입니다.","sourceName","체험 수집","sourceUrl","/board/finance-policy","snapshotExcerpt","체험 정책 변경 후보입니다.","status","pending","detectedAt",now()));
            session.setAttribute(DATA,s);return s;
        }
    }
    private void loadPresentationListings(State s) {
        if(s.listingsLoaded || listings==null) return;
        // 공개 매물 조회만 사용합니다. 운영 데이터 변경 서비스는 호출하지 않습니다.
        List<Map<String,Object>> actual=listings.find(null,null,true);
        List<Map<String,Object>> snapshot=new ArrayList<>();
        for(var source:actual) {
            var row=new LinkedHashMap<String,Object>(source);
            for(var entry:source.entrySet()) if(entry.getValue() instanceof List<?> values)
                row.put(entry.getKey(),new ArrayList<>(values));
            row.put("status","active");row.putIfAbsent("owner","발표용 매물");
            row.put("demoSnapshot",true);snapshot.add(row);
        }
        s.properties.removeIf(row->((Number)row.get("id")).longValue()==91000001L);
        s.properties.addAll(snapshot);
        if(!snapshot.isEmpty()) {
            var first=snapshot.get(0);
            for(var visit:s.visits) if(((Number)visit.get("id")).longValue()==91000003L) {
                visit.put("roomId","LISTING-"+first.get("id"));visit.put("title",first.get("title"));
            }
            for(var row:snapshot) s.next=Math.max(s.next,((Number)row.get("id")).longValue()+1);
        } else s.visits.removeIf(row->((Number)row.get("id")).longValue()==91000003L);
        s.listingsLoaded=true;
    }
    private static List<Map<String,Object>> visibleProperties(State s,Map<String,String> query) {
        String deal=query.getOrDefault("dealType","");
        boolean includeStudy=!"false".equalsIgnoreCase(query.getOrDefault("includeStudy","true"));
        Set<Long> ids=new java.util.HashSet<>();
        if(query.containsKey("ids")) for(String id:query.get("ids").split(",")) ids.add(Long.parseLong(id.trim()));
        return copy(s.properties.stream().filter(p->"active".equals(p.get("status")))
            .filter(p->deal.isBlank() || deal.equalsIgnoreCase(String.valueOf(p.get("dealType"))))
            .filter(p->includeStudy || !Boolean.TRUE.equals(p.get("studyData")))
            .filter(p->ids.isEmpty() || ids.contains(((Number)p.get("id")).longValue())).toList());
    }
    public void multipart(jakarta.servlet.http.HttpServletRequest request,Map<String,Object> body) throws Exception {
        List<String> images=new ArrayList<>();long imageBytes=0;
        for(var part:request.getParts()) {
            if(part.getSubmittedFileName()!=null && part.getSize()>0) {
                if(part.getSize()>5L*1024*1024 || images.size()>=10) throw bad("체험 사진은 장당 5MB, 최대 10장입니다.");
                byte[] bytes;
                try(var input=part.getInputStream()) {bytes=input.readNBytes(5*1024*1024+1);}
                if(bytes.length>5*1024*1024) throw bad("사진 크기를 줄여 주세요.");
                var encoded=SharedPhotoStorageService.encode(bytes);
                imageBytes+=encoded.bytes().length;
                if(imageBytes>2L*1024*1024) throw bad("체험 사진 합계는 압축 후 2MB 이하입니다.");
                images.add("data:"+encoded.type()+";base64,"+java.util.Base64.getEncoder().encodeToString(encoded.bytes()));
            } else if(part.getSubmittedFileName()==null) {
                try(var input=part.getInputStream()) {
                    byte[] bytes=input.readNBytes(10001);if(bytes.length>10000) throw bad("입력 내용이 너무 깁니다.");
                    body.put(part.getName(),new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        if(!images.isEmpty()) {body.put("imageUrls",images);body.put("imageUrl",images.get(0));}
    }
    public boolean publicRead(String path,String method) {
        if(method.equals("GET")) return path.startsWith("/api/safety/") || path.startsWith("/api/happy-housing/")
            || path.startsWith("/api/lifestyle/areas") || path.startsWith("/api/lifestyle/properties")
            || path.startsWith("/api/lifestyle/property-images/") || path.startsWith("/api/properties/images/")
            || path.equals("/api/properties/market-transactions") || path.equals("/api/finance/policies") || path.equals("/api/auth/social-providers");
        return method.equals("POST") && Set.of("/api/finance/calculate","/api/happy-housing/matches","/api/lifestyle/recommend","/api/lifestyle/recommend/ml","/api/chat").contains(path);
    }
    public Object request(HttpSession session,String path,String method,Map<String,Object> body,Map<String,String> query) {
        String mode=mode(session);if(mode==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"체험을 시작해 주세요.");
        State s=state(session);
        synchronized(s) {
            if(path.startsWith("/api/properties") || path.startsWith("/api/admin/properties")
                    || path.equals("/api/favorites") || path.equals("/api/admin/summary")) loadPresentationListings(s);
            if(body.containsKey("imageUrl") || body.containsKey("imageUrls")) {
                long existing=0;
                for(var rows:List.of(s.properties,s.posts,s.offers)) for(var row:rows) existing+=imageSize(row);
                if(existing+imageSize(body)>4L*1024*1024) throw bad("체험 사진 저장 한도에 도달했습니다. 체험 종료 후 다시 시작해 주세요.");
            }
            if(path.startsWith("/api/admin/") || path.startsWith("/api/finance/policy-updates") || path.matches("/api/finance/policies/[0-9]+")) {
                if(!mode.equals("admin")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"관리자 체험 모드에서 이용해 주세요.");
            }
            if(path.equals("/api/auth/me")) return map("authenticated",true,"user",user(s,mode));
            if(path.equals("/api/auth/logout")) { end(session);return map("success",true); }
            if(path.startsWith("/api/account") || path.equals("/api/auth/signup") || path.equals("/api/auth/login")) return account(s,mode,path,method,body);
            if(path.equals("/api/admin/summary")) return map("newMembers",1,"activeMembers",1,"newInquiries",s.inquiries.size(),"unansweredInquiries",s.inquiries.stream().filter(i->!i.get("status").equals("answered")).count(),"pendingProperties",s.properties.size(),"pendingVisits",s.visits.stream().filter(v->v.get("status").equals("pending")).count());
            if(path.equals("/api/admin/collection-history")) {
                String category=query.getOrDefault("category","market");
                List<Map<String,Object>> categories=List.of(map("id","market","label","아파트 실거래","tracking","체험 예시"),map("id","property","label","매물 수집","tracking","체험 예시"),map("id","lh","label","LH 공고","tracking","체험 예시"),map("id","finance","label","금융 정책","tracking","체험 예시"));
                List<Map<String,Object>> rows=Integer.parseInt(query.getOrDefault("page","0"))==0?List.of(map("id",1,"source","체험 "+category,"startedAt",now(),"finishedAt",now(),"status","completed","collected",10,"inserted",6,"updated",4,"errors",0,"noticeCount",10,"ruleCount",5,"baselined",6,"unchanged",4,"detected",0,"missingPolicies",0)):List.of();
                return map("categories",categories,"items",rows,"page",0);
            }
            if(path.equals("/api/admin/audit")) return items(copy(s.audit));
            if(path.equals("/api/admin/inquiries") || path.equals("/api/inquiries")) {
                if(method.equals("POST")) {Map<String,Object> item=map("id",s.next++,"category",text(body,"category","기타"),"email",text(body,"email","demo@example.com"),"title",text(body,"title","체험 문의"),"message",text(body,"message",""),"answer","","status","received","username",s.account.get("id"),"createdAt",now(),"updatedAt",now());add(s.inquiries,item);audit(s,"문의 등록",item.get("id"));return map("success",true,"id",item.get("id"));}
                return items(copy(s.inquiries));
            }
            if(path.matches("/api/admin/inquiries/[0-9]+/answer")) {
                var item=find(s.inquiries,id(path,4));String status=text(body,"status","answered"),answer=text(body,"answer","");
                if(status.equals("answered") && answer.isBlank()) throw bad("답변을 입력해 주세요.");
                item.put("status",status);item.put("answer",answer);item.put("updatedAt",now());item.put("answeredAt",now());
                notify(s,"문의 답변이 등록되었습니다","/board/customer-center#inquiry-history");audit(s,"문의 답변",item.get("id"));return map("success",true);
            }
            if(path.equals("/api/notifications")) return map("items",copy(s.notifications),"unreadCount",s.notifications.stream().filter(n->!Boolean.TRUE.equals(n.get("read"))).count());
            if(path.startsWith("/api/notifications/") && method.equals("PATCH")) {for(var n:s.notifications) if(path.endsWith("read-all") || ((Number)n.get("id")).longValue()==id(path,3)) n.put("read",true);return map("success",true);}
            if(path.equals("/api/favorites")) {
                if(method.equals("PUT")) {List<Long> ids=new ArrayList<>();if(body.get("ids") instanceof List<?> raw) for(Object value:raw) {if(ids.size()>=100) throw bad("체험 찜하기는 최대 100개입니다.");ids.add(Long.parseLong(value.toString()));}s.favorites=ids;}
                return map("success",true,"ids",new ArrayList<>(s.favorites),"items",copy(s.properties.stream().filter(p->s.favorites.contains(((Number)p.get("id")).longValue())).toList()));
            }
            if(path.equals("/api/admin/properties") || path.equals("/api/properties/mine")) return items(copy(s.properties));
            if(path.equals("/api/properties")) {
                if(method.equals("POST")) {var p=property(s.next++,body);add(s.properties,p);audit(s,"체험 매물 등록",p.get("id"));return map("item",p,"success",true);}
                return items(visibleProperties(s,query));
            }
            if(path.matches("/api/(admin/)?properties/[0-9]+(/status)?")) {
                boolean admin=path.startsWith("/api/admin/");long id=id(path,admin?4:3);var p=find(s.properties,id);
                if(method.equals("DELETE")) {s.properties.remove(p);s.favorites.remove(id);}
                else if(path.endsWith("/status")) p.put("status",text(body,"status","active").equals("approved")?"active":text(body,"status","active"));
                else if(method.equals("PUT")) {var replacement=property(id,body);if(!body.containsKey("imageUrls")) {replacement.put("imageUrls",p.get("imageUrls"));replacement.put("imageUrl",p.get("imageUrl"));}p.clear();p.putAll(replacement);}
                if(!method.equals("GET")) audit(s,"매물 변경",id);
                return map("item",new LinkedHashMap<>(p),"success",true,"comparable",List.of());
            }
            if(path.equals("/api/admin/visits") || path.equals("/api/visits")) {
                if(method.equals("POST")) {var v=new LinkedHashMap<>(body);v.put("id",s.next++);v.put("status","pending");v.put("manageable",false);add(s.visits,v);audit(s,"방문 신청",v.get("id"));return map("item",new LinkedHashMap<>(v));}
                return items(copy(s.visits));
            }
            if(path.equals("/api/visits/activity")) return map("sent",copy(s.visits),"received",copy(s.visits.stream().filter(v->Boolean.TRUE.equals(v.get("manageable"))).toList()));
            if(path.matches("/api/(admin/)?visits/[0-9]+/(status|approve|reject)")) {
                boolean admin=path.startsWith("/api/admin/");var v=find(s.visits,id(path,admin?4:3));
                if(!admin && !Boolean.TRUE.equals(v.get("manageable"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 체험 매물의 소유자만 처리할 수 있습니다. 관리자 체험에서 승인해 주세요.");
                v.put("status",path.endsWith("approve")?"approved":path.endsWith("reject")?"rejected":text(body,"status","approved"));notify(s,"방문 신청 상태가 변경되었습니다","/member/mypage#my-visits");audit(s,"방문 상태 변경",v.get("id"));return map("item",v,"success",true);
            }
            if(path.equals("/api/room-offers")) {if(method.equals("POST")){var offer=new LinkedHashMap<>(body);offer.put("id",s.next++);offer.put("status","pending");offer.put("propertyCode","DEMO-"+offer.get("id"));offer.putIfAbsent("imageUrls",List.of());add(s.offers,offer);return map("item",offer);}return items(copy(s.offers));}
            if(path.equals("/api/admin/community/posts") || path.equals("/api/community/posts")) {
                if(method.equals("POST")){var post=post(s.next++,body);add(s.posts,post);audit(s,"게시글 등록",post.get("id"));return map("item",post);}
                return items(copy(s.posts));
            }
            if(path.equals("/api/community/posts/upload")) return map("url",body.getOrDefault("imageUrl",""));
            if(path.matches("/api/(admin/)?community/posts/[0-9]+(/(like|report|comments))?")) {
                boolean admin=path.startsWith("/api/admin/");var post=find(s.posts,id(path,admin?5:4));
                if(method.equals("DELETE")){s.posts.remove(post);audit(s,"게시글 삭제",post.get("id"));return map("success",true);}
                if(path.endsWith("/like")){post.put("liked",true);post.put("likes",((Number)post.get("likes")).intValue()+1);return map("likes",post.get("likes"),"liked",true);}
                if(path.endsWith("/report")) return map("reported",true,"isBlinded",false,"reportsCount",1);
                if(path.endsWith("/comments")){if(method.equals("POST")){add(s.comments,map("id",s.next++,"postId",post.get("id"),"author",s.account.get("id"),"content",text(body,"content",""),"createdAt",now()));post.put("commentCount",((Number)post.get("commentCount")).intValue()+1);}return items(copy(s.comments.stream().filter(c->c.get("postId").equals(post.get("id"))).toList()));}
                return map("item",new LinkedHashMap<>(post));
            }
            if(path.equals("/api/finance/policy-updates")) return items(copy(s.finance));
            if(path.matches("/api/finance/policy-updates/[0-9]+/review")){var row=find(s.finance,id(path,4));row.put("status",text(body,"decision","approved"));audit(s,"금융 후보 검토",row.get("id"));return map("success",true);}
            if(path.matches("/api/finance/policies/[0-9]+")){var row=find(s.finance,id(path,4));row.putAll(body);audit(s,"금융정책 수정",row.get("id"));return map("success",true);}
            if(path.equals("/api/fraud-diagnoses/latest")) return s.diagnosis==null?map("available",false):map("available",true,"diagnosis",new LinkedHashMap<>(s.diagnosis));
            if(path.equals("/api/fraud-diagnoses") && method.equals("POST")){s.diagnosis=diagnosis(s.next++,body);return map("success",true,"diagnosis",s.diagnosis);}
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"체험 데이터에 연결되지 않은 요청입니다. 실제 회원 로그인 후 이용해 주세요.");
        }
    }
    private static Map<String,Object> diagnosis(long id,Map<String,Object> body) {
        String ref=text(body,"clientRef","");if(ref.isBlank()||ref.length()>64)throw bad("진단 요청 번호를 확인해 주세요.");
        int[] counts=new int[4];String[] keys={"safeCount","cautionCount","dangerCount","unansweredCount"};int sum=0;
        for(int i=0;i<4;i++){double value=number(body,keys[i],0).doubleValue();if(value<0||value>12||value!=Math.rint(value))throw bad("진단 응답 개수를 확인해 주세요.");counts[i]=(int)value;sum+=counts[i];}
        if(sum!=12)throw bad("진단 응답은 총 12개입니다.");
        int score=(int)Math.round((counts[0]+counts[1]*0.5+counts[3]*0.25)/12*100),total=score;
        Double ratio=null;if(body.get("jeonseRatio")!=null){ratio=number(body,"jeonseRatio",0).doubleValue();if(ratio<0||ratio>1000)throw bad("전세가율을 확인해 주세요.");int ratioScore=ratio<70?100:ratio<80?60:20;total=(int)Math.round(score*0.8+ratioScore*0.2);}
        return map("id",id,"clientRef",ref,"checklistScore",score,"finalScore",total,"safeCount",counts[0],"cautionCount",counts[1],"dangerCount",counts[2],"unansweredCount",counts[3],"jeonseRatio",ratio,"riskLevel",total>=80?"safe":total>=55?"caution":"danger","updatedAt",now());
    }
    private Object account(State s,String mode,String path,String method,Map<String,Object> body) {
        if(path.equals("/api/account") && method.equals("GET")) return map("user",user(s,mode));
        if(path.equals("/api/auth/login")){if(!text(body,"userId","").equals(s.account.get("id")) || !passwords.matches(text(body,"password",""),s.hash)) throw bad("체험 아이디·비밀번호를 확인해 주세요.");return map("user",user(s,mode));}
        if(path.equals("/api/account/profile") || path.equals("/api/auth/signup")) {
            String email=text(body,"email",""),phone=text(body,"phone","").replaceAll("[^0-9]","");if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||!phone.matches("[0-9]{10,11}")) throw bad("이메일·전화번호를 확인해 주세요.");
            if(path.endsWith("signup")){String name=text(body,"userId","");validateUsername(name);String password=text(body,"password","");validatePassword(password);s.account.put("id",name);s.hash=passwords.encode(password);}
            s.account.put("email",email);s.account.put("phone",phone);return map("user",user(s,mode));
        }
        String current=text(body,path.equals("/api/account")?"password":"currentPassword","");
        if(!passwords.matches(current,s.hash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"현재 체험 비밀번호를 확인해 주세요. 초기값은 Demo1234!입니다.");
        if(path.endsWith("/username")){String name=text(body,"userId","");validateUsername(name);s.account.put("id",name);return map("user",user(s,mode));}
        if(path.endsWith("/password")){String next=text(body,"newPassword","");validatePassword(next);s.hash=passwords.encode(next);return map("success",true);}
        if(path.equals("/api/account") && method.equals("DELETE")){s.account.clear();return map("success",true);}
        throw bad("계정 요청을 확인해 주세요.");
    }
    private static void validateUsername(String v){if(!v.matches("[A-Za-z0-9_가-힣]{4,20}"))throw bad("아이디는 4~20자입니다.");}
    private static void validatePassword(String v){if(v.length()<8||v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!v.matches(".*[A-Za-z].*")||!v.matches(".*[0-9].*")||!v.matches(".*[^A-Za-z0-9].*"))throw bad("비밀번호는 영문·숫자·특수문자 포함 8자 이상입니다.");}
    private static Map<String,Object> user(State s,String mode){var user=new LinkedHashMap<>(s.account);user.put("userId",91000000L);user.put("role",mode.equals("admin")?"admin":"member");user.put("demo",true);user.put("demoMode",mode);user.put("loginAt",now());return user;}
    private static Map<String,Object> property(long id,Map<String,Object> body){var p=map("id",id,"title",text(body,"title","체험 매물"),"address",text(body,"address","경기도 수원시 장안구"),"sido","경기도","sigungu",text(body,"sigungu","수원시"),"neighborhood","정자동","dealType",text(body,"dealType","MONTHLY"),"buildingType",text(body,"buildingType","원룸"),"deposit",number(body,"deposit",1000),"monthly",number(body,"monthly",50),"monthlyRent",number(body,"monthly",50),"maintenance",number(body,"maintenance",5),"salePrice",number(body,"salePrice",0),"area",number(body,"area",30),"floor",text(body,"floor","3"),"description",text(body,"description","실제 거래 대상이 아닌 체험 데이터입니다."),"contact",text(body,"contact","01000000000"),"status","active","owner","demo_user","ownerId",91000000L,"latitude",37.3035,"longitude",127.0105,"sourceType","USER","imageUrl","","imageUrls",List.of(),"createdAt",now());if(body.get("imageUrls") instanceof List<?> images&&!images.isEmpty()){p.put("imageUrls",images);p.put("imageUrl",images.get(0));}return p;}
    private static Map<String,Object> post(long id,Map<String,Object> body){return map("id",id,"title",text(body,"title","체험 게시글"),"content",text(body,"content",""),"category",text(body,"category","free"),"area",text(body,"area","수원시"),"author","demo_user","authorId",91000000L,"rating",number(body,"rating",5),"likes",0,"views",1,"commentCount",0,"liked",false,"reportsCount",0,"isBlinded",false,"imageUrl",text(body,"imageUrl",""),"createdAt",now(),"updatedAt",now());}
    private static Map<String,Object> find(List<Map<String,Object>> rows,long id){return rows.stream().filter(r->((Number)r.get("id")).longValue()==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"체험 항목이 없습니다."));}
    private static long id(String path,int index){try{return Long.parseLong(path.split("/")[index]);}catch(RuntimeException e){throw bad("항목 번호를 확인해 주세요.");}}
    private static void add(List<Map<String,Object>> rows,Map<String,Object> value){if(rows.size()>=100)throw bad("체험 항목은 최대 100개입니다. 체험 종료 후 다시 시작해 주세요.");rows.add(value);}
    private static void notify(State s,String title,String target){if(s.notifications.size()>=100)s.notifications.remove(0);s.notifications.add(map("id",s.next++,"title",title,"message","체험 요청이 처리되었습니다.","targetUrl",target,"read",false,"createdAt",now()));}
    private static void audit(State s,String action,Object id){if(s.audit.size()>=100)s.audit.remove(0);s.audit.add(0,map("id",s.next++,"action",action,"targetType","demo","targetId",id.toString(),"admin","체험 관리자","details","방문자 세션의 체험 데이터 변경","createdAt",now()));}
    private static List<Map<String,Object>> copy(List<Map<String,Object>> rows){return rows.stream().map(r->(Map<String,Object>)new LinkedHashMap<String,Object>(r)).toList();}
    private static Map<String,Object> items(List<Map<String,Object>> rows){return map("items",rows,"count",rows.size(),"totalCount",rows.size(),"page",1,"size",20,"totalPages",1);}
    private static String text(Map<String,Object> body,String key,String fallback){String v=String.valueOf(body.getOrDefault(key,fallback)).trim();if(v.length()>10000)throw bad("입력 내용이 너무 깁니다.");return v;}
    private static Number number(Map<String,Object> body,String key,long fallback){try{double value=Double.parseDouble(String.valueOf(body.getOrDefault(key,fallback)));if(!Double.isFinite(value))throw bad("숫자 입력을 확인해 주세요.");return value;}catch(RuntimeException e){throw bad("숫자 입력을 확인해 주세요.");}}
    private static long imageSize(Map<String,Object> body) {
        if(body.get("imageUrls") instanceof List<?> urls) return urls.stream().mapToLong(value->value.toString().length()).sum();
        return String.valueOf(body.getOrDefault("imageUrl","")).length();
    }
    private static String now(){return LocalDateTime.now().toString();}
    private static Map<String,Object> map(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)map.put(pairs[i].toString(),pairs[i+1]);return map;}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
