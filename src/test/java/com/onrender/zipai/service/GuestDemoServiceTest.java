package com.onrender.zipai.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class GuestDemoServiceTest {
    private final GuestDemoService demo=new GuestDemoService(new ZipaiPasswordService());
    @Test void visitorsHaveIndependentData() {
        var a=new MockHttpSession();var b=new MockHttpSession();
        demo.mutate(a,"properties","create",Map.of("title","나만의 체험 매물","status","active"));
        var left=(Map<?,?>)demo.view(a).get("modules");
        var right=(Map<?,?>)demo.view(b).get("modules");
        assertEquals(2,((java.util.List<?>)left.get("properties")).size());
        assertEquals(1,((java.util.List<?>)right.get("properties")).size());
        assertEquals(demo.view(new MockHttpSession()).get("account"),demo.view(b).get("account"));
    }
    @Test void failedSignupDoesNotPartiallyChangeAccount() {
        var session=new MockHttpSession();Object before=demo.view(session).get("account");
        assertThrows(ResponseStatusException.class,()->demo.account(session,"signup",Map.of("userId","new_user","password","Demo1234!","email","bad","phone","01012345678")));
        assertEquals(before,demo.view(session).get("account"));
    }
    @Test void withdrawalAndSignupStayInDemoSession() {
        var session=new MockHttpSession();
        demo.account(session,"withdraw",Map.of("password","Demo1234!"));
        assertEquals(false,demo.view(session).get("active"));
        demo.account(session,"signup",Map.of("userId","new_user","password","Demo1234!","email","new@example.com","phone","01012345678"));
        assertEquals(true,demo.view(session).get("active"));
        assertEquals("new_user",((Map<?,?>)demo.view(session).get("account")).get("id"));
    }
    @Test void arbitraryModuleIsRejected() {
        assertThrows(ResponseStatusException.class,()->demo.mutate(new MockHttpSession(),"production","delete",Map.of("id",1)));
    }
}
