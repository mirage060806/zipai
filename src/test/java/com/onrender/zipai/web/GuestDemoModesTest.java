package com.onrender.zipai.web;

import static org.junit.jupiter.api.Assertions.*;
import com.onrender.zipai.service.GuestDemoService;
import com.onrender.zipai.service.ZipaiPasswordService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class GuestDemoModesTest {
    private final GuestDemoController controller=new GuestDemoController(new GuestDemoService(new ZipaiPasswordService()));
    @Test void userAndAdminHaveDistinctMenus() {
        var session=new MockHttpSession();
        var user=(Map<?,?>)controller.view("user",session).get("modules");
        var admin=(Map<?,?>)controller.view("admin",session).get("modules");
        assertFalse(user.containsKey("members"));assertFalse(user.containsKey("crawl"));
        assertTrue(user.containsKey("favorites"));assertTrue(admin.containsKey("members"));assertTrue(admin.containsKey("crawl"));
    }
    @Test void adminAnswerIsVisibleInSameVisitorsUserMode() {
        var session=new MockHttpSession();
        var modules=(Map<?,?>)controller.change("inquiries","create","user",Map.of("title","체험 문의","status","pending","detail","질문 내용"),session).get("modules");
        var rows=(List<?>)modules.get("inquiries");var inquiry=(Map<?,?>)rows.get(rows.size()-1);
        controller.change("inquiries","update","admin",Map.of("id",inquiry.get("id"),"title","체험 문의","status","answered","detail","관리자 답변"),session);
        var after=(Map<?,?>)controller.view("user",session).get("modules");
        var answer=(Map<?,?>)((List<?>)after.get("inquiries")).get(rows.size()-1);
        assertEquals("질문 내용",answer.get("detail"));assertEquals("관리자 답변",answer.get("reply"));assertEquals("answered",answer.get("status"));
        var other=(Map<?,?>)controller.view("user",new MockHttpSession()).get("modules");
        assertEquals(1,((List<?>)other.get("inquiries")).size());
    }
    @Test void userModeCannotApproveOrManageMembers() {
        var session=new MockHttpSession();
        assertThrows(ResponseStatusException.class,()->controller.change("members","create","user",Map.of("title","추가"),session));
        assertThrows(ResponseStatusException.class,()->controller.change("visits","update","user",Map.of("id",100,"status","approved"),session));
    }
}
