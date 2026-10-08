package com.onrender.zipai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.onrender.zipai.domain.RoomVisit;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.repository.*;

class RoomConnectAccessTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final RoomVisitRepository visits = mock(RoomVisitRepository.class);
    final RoomOfferRepository offers = mock(RoomOfferRepository.class);
    final RoomConnectService service = new RoomConnectService(jdbc, visits, offers,
        mock(LifestylePropertyRepository.class), mock(LifestyleAreaRepository.class),
        mock(PropertyImageRepository.class), mock(PropertyImageStorageService.class));

    RoomVisit visit(Long applicant, Long owner) {
        RoomVisit v = new RoomVisit(); v.setVisitId(1L); v.setRoomId("LISTING-8");
        v.setTitle("실제 제목"); v.setApplicantUserId(applicant); v.setOwnerUserId(owner);
        v.setVisitDate(LocalDate.now().plusDays(1)); v.setVisitTime(LocalTime.of(14, 0));
        v.setPhone("010-1234-5678"); v.setStatus("pending"); return v;
    }
    @Test void applicantCannotApproveOwnRequest() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(10L, 20L)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.approveVisit(1L, 10L));
        assertEquals(403, ex.getStatusCode().value()); verify(visits, never()).save(any());
    }
    @Test void unrelatedUserCannotReject() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(10L, 20L)));
        assertThrows(ResponseStatusException.class, () -> service.rejectVisit(1L, 30L));
        verify(visits, never()).save(any());
    }
    @Test void anonymousLegacyRequestCannotBeClaimed() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(null, null)));
        assertThrows(ResponseStatusException.class, () -> service.approveVisit(1L, 20L));
    }
    @Test void activitySeparatesSentAndReceived() {
        when(visits.findByApplicantUserIdOrderByVisitIdDesc(10L)).thenReturn(List.of(visit(10L, 20L)));
        when(visits.findByOwnerUserIdOrderByVisitIdDesc(10L)).thenReturn(List.of());
        var data = service.activity(10L);
        assertEquals(1, data.get("sent").size()); assertTrue(data.get("received").isEmpty());
        assertFalse(data.get("sent").get(0).isManageable());
        verify(visits, never()).findAllByOrderByVisitIdDesc();
    }
    @Test void ownerCanRejectAndApplicantIsNotified() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(10L, 20L)));
        when(jdbc.update(contains("UPDATE room_visit"), anyString(), any(), eq(1L), eq(20L))).thenReturn(1);
        var result = service.rejectVisit(1L, 20L);
        assertEquals("rejected", result.getStatus()); assertTrue(result.isManageable());
        verify(jdbc).update(contains("user_notification"), eq(10L), anyString(), anyString(), any());
    }
    @Test void homepageVisitUsesServerTitleAndSessionUser() {
        RoomVisitRequest q = new RoomVisitRequest(); q.setRoomId("LISTING-8"); q.setTitle("위조 제목");
        q.setDate(LocalDate.now().plusDays(1)); q.setTime(LocalTime.of(14,0)); q.setPhone("01012345678");
        when(jdbc.queryForList(anyString(), eq(8L))).thenReturn(List.of(Map.of("title", "실제 제목", "owner_user_id", 20L)));
        when(visits.findByRoomIdAndVisitDateAndVisitTimeAndStatus(anyString(), any(), any(), anyString())).thenReturn(List.of());
        when(visits.save(any())).thenAnswer(i -> { RoomVisit v = i.getArgument(0); v.setVisitId(1L); return v; });
        var result = service.createVisit(q, 10L);
        assertEquals("실제 제목", result.getTitle());
        verify(visits).save(argThat(v -> Long.valueOf(10).equals(v.getApplicantUserId()) && Long.valueOf(20).equals(v.getOwnerUserId())));
    }
}
