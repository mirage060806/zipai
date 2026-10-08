package com.onrender.zipai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.repository.ZipaiUserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class AccountManagementTest {
    @Test void usernameChangePreservesDatabaseIdentityAndRole() {
        var users=mock(ZipaiUserRepository.class);var passwords=mock(ZipaiPasswordService.class);
        var user=user();var session=session();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(passwords.matches("Old1234!","hash")).thenReturn(true);
        when(users.save(any(ZipaiUser.class))).thenAnswer(invocation->invocation.getArgument(0));
        var changed=new ZipaiAuthService(users,passwords).changeUsername(Map.of("userId","new_user","currentPassword","Old1234!"),session);
        assertEquals(1L,changed.getId());assertEquals("admin",changed.getRole());assertEquals("new_user",changed.getUsername());
    }
    @Test void duplicateUsernameIsRejected() {
        var users=mock(ZipaiUserRepository.class);var passwords=mock(ZipaiPasswordService.class);
        when(users.findById(1L)).thenReturn(Optional.of(user()));when(passwords.matches("Old1234!","hash")).thenReturn(true);
        when(users.existsByUsernameIgnoreCaseAndIdNot("taken_user",1L)).thenReturn(true);
        var error=assertThrows(ResponseStatusException.class,()->new ZipaiAuthService(users,passwords).changeUsername(Map.of("userId","taken_user","currentPassword","Old1234!"),session()));
        assertEquals(409,error.getStatusCode().value());verify(users,never()).save(any(ZipaiUser.class));
    }
    @Test void expiredSocialVerificationDoesNotReplacePassword() {
        var users=mock(ZipaiUserRepository.class);var passwords=mock(ZipaiPasswordService.class);var session=session();
        when(users.findById(1L)).thenReturn(Optional.of(user()));session.setAttribute("ZIPAI_SOCIAL_VERIFIED_USER",1L);
        session.setAttribute("ZIPAI_SOCIAL_VERIFIED_AT",System.currentTimeMillis()-16*60*1000L);
        assertThrows(ResponseStatusException.class,()->new ZipaiAuthService(users,passwords).changePassword(Map.of("currentPassword","","newPassword","New1234!"),session));
        verify(users,never()).save(any(ZipaiUser.class));
    }
    private static ZipaiUser user() {var user=new ZipaiUser();user.setId(1L);user.setUsername("old_user");user.setPasswordHash("hash");user.setStatus("active");user.setRole("admin");return user;}
    private static MockHttpSession session() {var session=new MockHttpSession();session.setAttribute("ZIPAI_USER_ID",1L);return session;}
}
