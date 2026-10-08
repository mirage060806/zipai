package com.onrender.zipai.web;

import jakarta.servlet.http.HttpSession;
import com.onrender.zipai.service.ZipaiAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.dto.lifestyle.ItemResponse;
import com.onrender.zipai.dto.lifestyle.ItemsResponse;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.dto.lifestyle.RoomVisitResponse;
import com.onrender.zipai.service.RoomConnectService;

@RestController
@RequestMapping("/api/visits")
public class RoomVisitController {

    private final ZipaiAuthService auth;
    private final RoomConnectService roomConnectService;

    public RoomVisitController(RoomConnectService roomConnectService, ZipaiAuthService auth) {
        this.roomConnectService = roomConnectService;
        this.auth = auth;
    }

    @GetMapping
    public ItemsResponse<RoomVisitResponse> visits(HttpSession session) {
        return new ItemsResponse<>(roomConnectService.getVisits(auth.required(session).getId()));
    }

    @GetMapping("/activity")
    public java.util.Map<String, java.util.List<RoomVisitResponse>> activity(HttpSession session) {
        return roomConnectService.activity(auth.required(session).getId());
    }

    @PostMapping
    public ItemResponse<RoomVisitResponse> create(
            @RequestBody RoomVisitRequest request, HttpSession session) {
        return new ItemResponse<>(roomConnectService.createVisit(request, auth.required(session).getId()));
    }

    @PatchMapping("/{visitId}/approve")
    public ItemResponse<RoomVisitResponse> approve(@PathVariable Long visitId, HttpSession session) {
        return new ItemResponse<>(roomConnectService.approveVisit(visitId, auth.required(session).getId()));
    }

    @PatchMapping("/{visitId}/reject")
    public ItemResponse<RoomVisitResponse> reject(@PathVariable Long visitId, HttpSession session) {
        return new ItemResponse<>(roomConnectService.rejectVisit(visitId, auth.required(session).getId()));
    }
}
