package com.onrender.zipai.service;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CrawlerExecutionServiceTest {
    private Map<String,Object> sample(){
        var body=new LinkedHashMap<String,Object>(Map.of("runId","19f3f451-b61a-40ad-aa52-c72da83cd82b","category","market","trigger","scheduled","operation","crawl","stage","done","regionScope","single","dealYmd","202610","status","completed","seq",3));
        body.put("startedAt","2026-10-07T00:00:00Z");body.put("finishedAt","2026-10-07T00:01:00Z");return body;
    }
    @Test void malformedPayloadNeverWrites(){
        var jdbc=mock(JdbcTemplate.class);var service=new CrawlerExecutionService(jdbc);var body=sample();
        body.put("category","https://example.com?secret=invalid");
        assertThrows(ResponseStatusException.class,()->service.record(body));verifyNoInteractions(jdbc);
    }
    @Test void terminalStatusRequiresFinishedTimestamp(){
        var jdbc=mock(JdbcTemplate.class);var service=new CrawlerExecutionService(jdbc);var body=sample();body.remove("finishedAt");
        assertThrows(ResponseStatusException.class,()->service.record(body));verifyNoInteractions(jdbc);
    }
    @Test void validSnapshotUsesOnlyHistoryTable(){
        var jdbc=mock(JdbcTemplate.class);new CrawlerExecutionService(jdbc).record(sample());
        verify(jdbc,times(2)).update(anyString(),any(Object[].class));verifyNoMoreInteractions(jdbc);
    }
    @Test void allCollectionCategoriesAcceptExecutionSnapshots(){
        for(String category:java.util.List.of("market","property","lh","finance","lifestyle")) {
            var jdbc=mock(JdbcTemplate.class);var body=sample();body.put("category",category);
            new CrawlerExecutionService(jdbc).record(body);
            verify(jdbc,times(2)).update(anyString(),any(Object[].class));
        }
    }

}
