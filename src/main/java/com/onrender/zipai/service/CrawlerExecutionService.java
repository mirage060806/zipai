package com.onrender.zipai.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CrawlerExecutionService {
    private final JdbcTemplate jdbc;
    public CrawlerExecutionService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional
    public void record(Map<String,Object> body){
        String runId=text(body,"runId");
        try{if(!UUID.fromString(runId).toString().equals(runId))throw new IllegalArgumentException();}catch(RuntimeException e){throw bad();}
        String category=choice(body,"category",Set.of("market","property","lh","finance","lifestyle"));
        String trigger=choice(body,"trigger",Set.of("manual","scheduled","unknown"));
        String operation=choice(body,"operation",Set.of("crawl","upload_only","geocode_only","listing_import","build_import"));
        String stage=choice(body,"stage",Set.of("preflight","collect","geocode","upload","done"));
        String scope=choice(body,"regionScope",Set.of("single","capital","custom","other","none"));
        String status=choice(body,"status",Set.of("running","completed","partial","failed","cancelled"));
        String period=text(body,"dealYmd");if(!period.isEmpty() && !period.matches("[0-9]{4}(0[1-9]|1[0-2])"))throw bad();
        LocalDateTime started=time(body,"startedAt"),finished=body.get("finishedAt")==null?null:time(body,"finishedAt");
        if(status.equals("running") ? finished!=null : finished==null)throw bad();
        if(finished!=null && finished.isBefore(started))throw bad();
        long seq=count(body,"seq");if(seq<1)throw bad();
        int collected=count(body,"collected"),inserted=count(body,"inserted"),updated=count(body,"updated"),errors=count(body,"errors"),skipped=count(body,"skipped"),failedRegions=count(body,"failedRegions"),reused=count(body,"reusedBatches"),regionCount=count(body,"regionCount");
        String source=switch(category) {
            case "market" -> "MOLIT 아파트 실거래";
            case "lh" -> "LH 행복주택 수집";
            case "finance" -> "금융 정책 수집";
            case "lifestyle" -> "생활 데이터 정규화·반영";
            default -> operation.equals("listing_import")?"허가된 매물 파일 반영":"HTML 매물 수집";
        };
        // 같은 실행 UUID는 한 행입니다. 재전송·늦은 응답으로 완료 상태를 되돌리지 않습니다.
        jdbc.update("""
            INSERT IGNORE INTO crawler_execution_history
              (run_id,category,source_name,trigger_type,operation,stage,region_scope,region_count,deal_ymd,
               started_at,finished_at,last_seen_at,status,collected_count,inserted_count,updated_count,error_count,
               skipped_count,failed_regions,reused_batches,last_seq)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,runId,category,source,trigger,operation,stage,scope,regionCount,period,started,finished,
                LocalDateTime.now(ZoneOffset.UTC),status,collected,inserted,updated,errors,skipped,failedRegions,reused,seq);
        jdbc.update("""
            UPDATE crawler_execution_history SET category=?,source_name=?,trigger_type=?,operation=?,stage=?,
              region_scope=?,region_count=?,deal_ymd=?,finished_at=?,last_seen_at=?,status=?,collected_count=?,
              inserted_count=?,updated_count=?,error_count=?,skipped_count=?,failed_regions=?,reused_batches=?,last_seq=?
            WHERE run_id=? AND last_seq<? AND (status='running' OR ?<>'running')
            """,category,source,trigger,operation,stage,scope,regionCount,period,finished,LocalDateTime.now(ZoneOffset.UTC),
                status,collected,inserted,updated,errors,skipped,failedRegions,reused,seq,runId,seq,status);
    }
    private static String text(Map<String,Object> body,String key){return String.valueOf(body.getOrDefault(key,""));}
    private static String choice(Map<String,Object> body,String key,Set<String> choices){String value=text(body,key);if(!choices.contains(value))throw bad();return value;}
    private static int count(Map<String,Object> body,String key){try{long value=Long.parseLong(String.valueOf(body.getOrDefault(key,0)));if(value<0||value>100000000)throw bad();return (int)value;}catch(RuntimeException e){throw bad();}}
    private static LocalDateTime time(Map<String,Object> body,String key){try{return LocalDateTime.ofInstant(Instant.parse(text(body,key)),ZoneOffset.UTC);}catch(RuntimeException e){throw bad();}}
    private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"수집 이력 형식을 확인해 주세요.");}
}
