package com.onrender.zipai.config;

import com.onrender.zipai.service.NativeDemoService;
import com.onrender.zipai.service.ZipaiPasswordService;
import com.onrender.zipai.service.PropertyListingService;
import jakarta.servlet.FilterChain;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeDemoFilterTest {
    private final NativeDemoService demo=new NativeDemoService(new ZipaiPasswordService());
    @Test void demoWritesNeverReachOperatingController() throws Exception {
        var session=new MockHttpSession();demo.start(session,"admin");
        var request=new MockHttpServletRequest("POST","/api/unknown-import");request.setSession(session);
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new NativeDemoFilter(demo,new ObjectMapper()).doFilter(request,response,chain);
        assertEquals(404,response.getStatus());verifyNoInteractions(chain);
    }
    @Test void ordinaryRequestsKeepExistingFlow() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/auth/me");
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new NativeDemoFilter(demo,new ObjectMapper()).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);
    }
    @Test void modesShareOnlyVisitorsOwnInquiry() {
        var session=new MockHttpSession();demo.start(session,"user");
        var result=(Map<?,?>)demo.request(session,"/api/inquiries","POST",Map.of("title","체험 문의","message","질문"),Map.of());
        long id=((Number)result.get("id")).longValue();
        demo.start(session,"admin");demo.request(session,"/api/admin/inquiries/"+id+"/answer","POST",Map.of("answer","체험 답변"),Map.of());
        demo.start(session,"user");
        var rows=(java.util.List<?>)((Map<?,?>)demo.request(session,"/api/inquiries","GET",Map.of(),Map.of())).get("items");
        assertEquals("체험 답변",((Map<?,?>)rows.get(1)).get("answer"));
        var other=new MockHttpSession();demo.start(other,"user");
        assertEquals(1,((java.util.List<?>)((Map<?,?>)demo.request(other,"/api/inquiries","GET",Map.of(),Map.of())).get("items")).size());
    }
    @Test void presentationListingsAndPhotosAreCopiedWithoutOperatingWrites() {
        var listings=mock(PropertyListingService.class);
        var source=new java.util.LinkedHashMap<String,Object>(Map.of("id",130005L,"title","발표 매물","dealType","MONTHLY","studyData",true,"imageUrl","/api/properties/images/photo.jpg","imageUrls",new java.util.ArrayList<>(java.util.List.of("/api/properties/images/photo.jpg"))));
        when(listings.find(null,null,true)).thenReturn(java.util.List.of(source));
        var liveDemo=new NativeDemoService(new ZipaiPasswordService(),listings);
        var visitor=new MockHttpSession();liveDemo.start(visitor,"user");
        var catalog=(java.util.List<?>)((Map<?,?>)liveDemo.request(visitor,"/api/properties","GET",Map.of(),Map.of())).get("items");
        assertEquals(1,catalog.size());assertEquals(source.get("imageUrl"),((Map<?,?>)catalog.get(0)).get("imageUrl"));
        liveDemo.start(visitor,"admin");
        var adminCatalog=(java.util.List<?>)((Map<?,?>)liveDemo.request(visitor,"/api/admin/properties","GET",Map.of(),Map.of())).get("items");
        assertEquals(130005L,((Map<?,?>)adminCatalog.get(0)).get("id"));
        liveDemo.request(visitor,"/api/admin/properties/130005/status","PATCH",Map.of("status","closed"),Map.of());
        assertFalse(source.containsKey("status"));
        var other=new MockHttpSession();liveDemo.start(other,"user");
        assertEquals(1,((java.util.List<?>)((Map<?,?>)liveDemo.request(other,"/api/properties","GET",Map.of(),Map.of())).get("items")).size());
        verify(listings,times(2)).find(null,null,true);verifyNoMoreInteractions(listings);
    }
}
