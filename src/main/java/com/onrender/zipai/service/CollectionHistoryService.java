package com.onrender.zipai.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectionHistoryService {
    private final JdbcTemplate jdbc;
    public CollectionHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> categories() {
        return List.of(
            Map.of("id","market","label","아파트 실거래","tracking","PC 실행 이력 + 배치별 서버 반영 이력"),
            Map.of("id","property","label","매물 수집","tracking","PC 실행 이력 + 배치별 서버 반영 이력"),
            Map.of("id","lh","label","LH 공고","tracking","PC 실행 이력 + 기존 DB 반영 이력"),
            Map.of("id","finance","label","금융 정책","tracking","PC 실행 이력 + 검토 후보 수신 이력"),
            Map.of("id","lifestyle","label","생활 데이터","tracking","공식 CSV 정규화·DB 반영 실행 이력"));
    }

    public List<Map<String,Object>> list(String category, int page) {
        int offset = Math.max(0, Math.min(page, 1000)) * 20;
        String sql = switch(category) {
            case "market", "property" -> """
                SELECT * FROM (
                  SELECT CONCAT('import-',crawl_id) AS id, source_name AS source, started_at AS startedAt,
                         finished_at AS finishedAt, collected_count AS collected,
                         inserted_count AS inserted, updated_count AS updated,
                         error_count AS errors, message AS status, 'import' AS recordType,
                         NULL AS triggerType,NULL AS operation,NULL AS stage,NULL AS regionScope,
                         NULL AS regionCount,NULL AS dealYmd,NULL AS failedRegions,NULL AS skipped,
                         NULL AS reusedBatches,NULL AS lastSeenAt
                  FROM property_crawl_history WHERE category=?
                  UNION ALL
                  SELECT run_id AS id,source_name AS source,started_at AS startedAt,finished_at AS finishedAt,
                         collected_count AS collected,inserted_count AS inserted,updated_count AS updated,
                         error_count AS errors,status,'execution' AS recordType,
                         trigger_type AS triggerType,operation,stage,region_scope AS regionScope,
                         region_count AS regionCount,deal_ymd AS dealYmd,failed_regions AS failedRegions,
                         skipped_count AS skipped,reused_batches AS reusedBatches,last_seen_at AS lastSeenAt
                  FROM crawler_execution_history WHERE category=?
                ) history ORDER BY startedAt DESC,id DESC LIMIT 20 OFFSET ?
                """;
            case "lh" -> """
                SELECT * FROM (
                  SELECT CONCAT('lh-',crawl_id) AS id,crawler_name AS source,started_at AS startedAt,
                       finished_at AS finishedAt,notice_count AS collected,NULL AS errors,status,'import' AS recordType,
                       NULL AS triggerType,NULL AS operation,NULL AS stage,NULL AS lastSeenAt,
                       notice_count AS noticeCount,rule_count AS ruleCount,NULL AS baselined,NULL AS unchanged,
                       NULL AS detected,NULL AS missingPolicies FROM crawl_history
                  UNION ALL
                  SELECT run_id AS id,source_name AS source,started_at AS startedAt,finished_at AS finishedAt,
                         collected_count AS collected,error_count AS errors,status,'execution' AS recordType,
                         trigger_type AS triggerType,operation,stage,last_seen_at AS lastSeenAt,
                         NULL AS noticeCount,NULL AS ruleCount,NULL AS baselined,NULL AS unchanged,
                         NULL AS detected,NULL AS missingPolicies
                  FROM crawler_execution_history WHERE category=?
                ) history ORDER BY startedAt DESC,id DESC LIMIT 20 OFFSET ?
                """;
            case "finance" -> """
                SELECT * FROM (
                  SELECT CONCAT('finance-',id) AS id,'금융 정책 수신' AS source,started_at AS startedAt,
                       finished_at AS finishedAt,received_count AS collected,NULL AS errors,status,'import' AS recordType,
                       NULL AS triggerType,NULL AS operation,NULL AS stage,NULL AS lastSeenAt,
                       NULL AS noticeCount,NULL AS ruleCount,baseline_count AS baselined,unchanged_count AS unchanged,
                       detected_count AS detected,missing_count AS missingPolicies FROM finance_import_history
                  UNION ALL
                  SELECT run_id AS id,source_name AS source,started_at AS startedAt,finished_at AS finishedAt,
                         collected_count AS collected,error_count AS errors,status,'execution' AS recordType,
                         trigger_type AS triggerType,operation,stage,last_seen_at AS lastSeenAt,
                         NULL AS noticeCount,NULL AS ruleCount,NULL AS baselined,NULL AS unchanged,
                         NULL AS detected,NULL AS missingPolicies
                  FROM crawler_execution_history WHERE category=?
                ) history ORDER BY startedAt DESC,id DESC LIMIT 20 OFFSET ?
                """;
            case "lifestyle" -> """
                SELECT * FROM (
                  SELECT run_id AS id,source_name AS source,started_at AS startedAt,finished_at AS finishedAt,
                         collected_count AS collected,error_count AS errors,status,'execution' AS recordType,
                         trigger_type AS triggerType,operation,stage,last_seen_at AS lastSeenAt,
                         NULL AS noticeCount,NULL AS ruleCount,NULL AS baselined,NULL AS unchanged,
                         NULL AS detected,NULL AS missingPolicies
                  FROM crawler_execution_history WHERE category=?
                ) history ORDER BY startedAt DESC,id DESC LIMIT 20 OFFSET ?
                """;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"수집 항목을 확인해 주세요.");
        };
        Object[] params = category.equals("market") || category.equals("property")
            ? new Object[]{category,category,offset} : new Object[]{category,offset};
        return jdbc.query(sql, (rs, i) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            var metadata = rs.getMetaData();
            for(int col=1;col<=metadata.getColumnCount();col++) {
                Object value=rs.getObject(col);
                row.put(metadata.getColumnLabel(col), value instanceof java.sql.Timestamp stamp ? stamp.toLocalDateTime().toString() : value);
            }
            String status=String.valueOf(row.get("status"));
            // Never expose raw crawler exception text, which can contain request URLs or keys.
            String normalized=normalizeStatus(status,row.get("finishedAt"));
            if(!"execution".equals(row.get("recordType")) && row.get("collected") instanceof Number collected && row.get("errors") instanceof Number errors
                    && collected.intValue()>0 && errors.intValue()>=collected.intValue()) normalized="failed";
            row.put("status", normalized);
            if("execution".equals(row.get("recordType"))) {
                for(String key:List.of("startedAt","finishedAt","lastSeenAt")) {
                    if(row.get(key)!=null) row.put(key,row.get(key).toString()+"Z");
                }
            }
            return row;
        },params);
    }

    public static String normalizeStatus(String status,Object finishedAt) {
        String value=status.toUpperCase(java.util.Locale.ROOT);
        if(value.equals("CANCELLED")) return "cancelled";
        if(value.contains("PARTIAL")) return "partial";
        if(value.contains("FAIL") || value.contains("ERROR")) return "failed";
        if(value.equals("SUCCESS") || value.equals("COMPLETED") || value.equals("DONE")) return "completed";
        if(finishedAt==null) return "running";
        return "unknown";
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void financeResult(LocalDateTime started,int received,Map<String,Object> result,boolean failed) {
        jdbc.update("""
            INSERT INTO finance_import_history
              (started_at,finished_at,received_count,baseline_count,unchanged_count,detected_count,missing_count,status)
            VALUES (?,?,?,?,?,?,?,?)
            """,started,LocalDateTime.now(),received,count(result,"baselined"),count(result,"unchanged"),
            count(result,"detected"),count(result,"missingPolicies"),failed?"FAILED":count(result,"missingPolicies")>0?"PARTIAL":"SUCCESS");
    }
    private static int count(Map<String,Object> map,String key) { return map.get(key) instanceof Number n?n.intValue():0; }
}
