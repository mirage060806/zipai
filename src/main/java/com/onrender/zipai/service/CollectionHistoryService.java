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
            Map.of("id","market","label","아파트 실거래","tracking","서버 반영 이력"),
            Map.of("id","property","label","매물 수집","tracking","서버 반영 이력"),
            Map.of("id","lh","label","LH 공고","tracking","기존 크롤러 실행 이력"),
            Map.of("id","finance","label","금융 정책","tracking","검토 후보 수신 이력"));
    }

    public List<Map<String,Object>> list(String category, int page) {
        int offset = Math.max(0, Math.min(page, 1000)) * 20;
        String sql = switch(category) {
            case "market", "property" -> """
                SELECT crawl_id AS id, source_name AS source, started_at AS startedAt,
                       finished_at AS finishedAt, collected_count AS collected,
                       inserted_count AS inserted, updated_count AS updated,
                       error_count AS errors, message AS status
                FROM property_crawl_history WHERE category=? ORDER BY started_at DESC,crawl_id DESC LIMIT 20 OFFSET ?
                """;
            case "lh" -> """
                SELECT crawl_id AS id,crawler_name AS source,started_at AS startedAt,
                       finished_at AS finishedAt,notice_count AS noticeCount,rule_count AS ruleCount,
                       status FROM crawl_history ORDER BY started_at DESC,crawl_id DESC LIMIT 20 OFFSET ?
                """;
            case "finance" -> """
                SELECT id,'금융 정책 수신' AS source,started_at AS startedAt,finished_at AS finishedAt,
                       received_count AS collected,baseline_count AS baselined,
                       unchanged_count AS unchanged,detected_count AS detected,missing_count AS missingPolicies,
                       status FROM finance_import_history ORDER BY started_at DESC,id DESC LIMIT 20 OFFSET ?
                """;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"수집 항목을 확인해 주세요.");
        };
        Object[] params = category.equals("market") || category.equals("property")
            ? new Object[]{category,offset} : new Object[]{offset};
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
            if(row.get("collected") instanceof Number collected && row.get("errors") instanceof Number errors
                    && collected.intValue()>0 && errors.intValue()>=collected.intValue()) normalized="failed";
            row.put("status", normalized);
            return row;
        },params);
    }

    public static String normalizeStatus(String status,Object finishedAt) {
        String value=status.toUpperCase(java.util.Locale.ROOT);
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
