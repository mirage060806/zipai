package com.zipai.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipai.domain.*;
import com.zipai.repository.*;
import com.zipai.service.*;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
public class VisitController {
    private final VisitRepository visits; private final RoomOfferRepository offers;
    private final AuthService auth; private final NotificationService notices; private final ObjectMapper json;
    public VisitController(VisitRepository visits, RoomOfferRepository offers, AuthService auth,
                           NotificationService notices, ObjectMapper json) {
        this.visits=visits; this.offers=offers; this.auth=auth; this.notices=notices; this.json=json;
    }
    @GetMapping("/visits")
    Map<String,Object> visits(HttpSession s) {
        User u=auth.required(s); return Map.of("items",visits.findByRequesterIdOrderByIdDesc(u.id).stream().map(this::visit).toList());
    }
    @PostMapping("/visits") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> visit(@RequestBody Map<String,Object> p,HttpSession s) {
        User u=auth.required(s);
        try {
            VisitRequest v=new VisitRequest(); v.requester=u; v.roomId=text(p,"roomId"); v.roomTitle=text(p,"title");
            v.visitDate=LocalDate.parse(text(p,"date")); v.visitTime=LocalTime.parse(text(p,"time"));
            if(v.roomId.isBlank()||v.visitTime.getMinute()!=0) throw new Exception();
            v.phone=text(p,"phone"); v.question=text(p,"question"); visits.save(v); return Map.of("item",visit(v));
        } catch(Exception e) { throw new ResponseStatusException(BAD_REQUEST,"방, 날짜와 시간을 확인해 주세요."); }
    }
    @PatchMapping("/visits/{id}/{action}")
    Map<String,Object> decide(@PathVariable Long id,@PathVariable String action,HttpSession s) {
        auth.admin(s);
        if(!Set.of("approve","reject").contains(action)) throw new ResponseStatusException(METHOD_NOT_ALLOWED);
        VisitRequest v=visits.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"방문 요청을 찾을 수 없습니다."));
        v.status=action.equals("approve")?"approved":"rejected";
        try { visits.saveAndFlush(v); } catch(DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT,"같은 시간에 이미 확정된 예약이 있습니다.");
        }
        notices.create(v.requester,"visit_result","방문 신청 처리 결과",
            (v.roomTitle.isBlank()?"방문 신청":v.roomTitle)+"이 "+(v.status.equals("approved")?"승인":"거절")+"되었습니다.","visit",v.id);
        return Map.of("item",visit(v));
    }
    @GetMapping("/room-offers")
    Map<String,Object> offers(HttpSession s) {
        User u=auth.required(s); return Map.of("items",offers.findByOwnerIdOrderByIdDesc(u.id).stream().map(this::offer).toList());
    }
    @PostMapping("/room-offers") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> offer(@RequestBody Map<String,Object> p,HttpSession s) throws Exception {
        User u=auth.required(s);
        if(text(p,"title").isBlank()||text(p,"moveIn").isBlank())
            throw new ResponseStatusException(BAD_REQUEST,"방 제목과 입주 가능일을 확인해 주세요.");
        RoomOffer r=new RoomOffer(); r.owner=u; r.payloadJson=json.writeValueAsString(p); offers.save(r);
        return Map.of("item",offer(r));
    }
    private Map<String,Object> visit(VisitRequest v){return Map.of("id",v.id,"roomId",v.roomId,"title",v.roomTitle,
        "date",v.visitDate,"time",v.visitTime,"phone",v.phone,"question",v.question,"status",v.status,"createdAt",v.createdAt);}
    private Map<String,Object> offer(RoomOffer r){try{Map<String,Object> m=new LinkedHashMap<>(json.readValue(r.payloadJson,new TypeReference<>(){}));
        m.put("id",r.id);m.put("status",r.status);return m;}catch(Exception e){throw new IllegalStateException(e);}}
    private static String text(Map<String,Object> p,String k){return String.valueOf(p.getOrDefault(k,"")).trim();}
}
