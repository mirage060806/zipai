package com.onrender.zipai.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.onrender.zipai.domain.LifestyleProperty;
import com.onrender.zipai.domain.PropertyImage;
import com.onrender.zipai.domain.RoomOffer;
import com.onrender.zipai.domain.RoomVisit;
import com.onrender.zipai.dto.lifestyle.RoomOfferRequest;
import com.onrender.zipai.dto.lifestyle.RoomOfferResponse;
import com.onrender.zipai.dto.lifestyle.RoomVisitRequest;
import com.onrender.zipai.dto.lifestyle.RoomVisitResponse;
import com.onrender.zipai.repository.LifestyleAreaRepository;
import com.onrender.zipai.repository.LifestylePropertyRepository;
import com.onrender.zipai.repository.PropertyImageRepository;
import com.onrender.zipai.repository.RoomOfferRepository;
import com.onrender.zipai.repository.RoomVisitRepository;

@Service
public class RoomConnectService {

    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String REJECTED = "rejected";
    private static final String READY = "ready";


    private final JdbcTemplate jdbc;
    private final RoomVisitRepository roomVisitRepository;
    private final RoomOfferRepository roomOfferRepository;
    private final LifestylePropertyRepository lifestylePropertyRepository;
    private final LifestyleAreaRepository lifestyleAreaRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyImageStorageService imageStorageService;

    public RoomConnectService(
            JdbcTemplate jdbc,
            RoomVisitRepository roomVisitRepository,
            RoomOfferRepository roomOfferRepository,
            LifestylePropertyRepository lifestylePropertyRepository,
            LifestyleAreaRepository lifestyleAreaRepository,
            PropertyImageRepository propertyImageRepository,
            PropertyImageStorageService imageStorageService) {
        this.jdbc = jdbc;
        this.roomVisitRepository = roomVisitRepository;
        this.roomOfferRepository = roomOfferRepository;
        this.lifestylePropertyRepository = lifestylePropertyRepository;
        this.lifestyleAreaRepository = lifestyleAreaRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional(readOnly = true)
    public List<RoomVisitResponse> getVisits(Long userId) {
        return java.util.stream.Stream.concat(
                roomVisitRepository.findByApplicantUserIdOrderByVisitIdDesc(userId).stream(),
                roomVisitRepository.findByOwnerUserIdOrderByVisitIdDesc(userId).stream())
            .collect(java.util.stream.Collectors.toMap(RoomVisit::getVisitId, v -> v, (a, b) -> a))
            .values().stream().sorted(java.util.Comparator.comparing(RoomVisit::getVisitId).reversed())
            .map(v -> RoomVisitResponse.from(v).forViewer(userId, v)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, List<RoomVisitResponse>> activity(Long userId) {
        return Map.of(
            "sent", roomVisitRepository.findByApplicantUserIdOrderByVisitIdDesc(userId).stream()
                .map(v -> RoomVisitResponse.from(v).forViewer(userId, v)).toList(),
            "received", roomVisitRepository.findByOwnerUserIdOrderByVisitIdDesc(userId).stream()
                .map(v -> RoomVisitResponse.from(v).forViewer(userId, v)).toList());
    }

    @Transactional
    public RoomVisitResponse createVisit(RoomVisitRequest request, Long userId) {
        validateVisitRequest(request);

        String roomId = request.getRoomId().trim();
        Long ownerId;
        String title;
        if (roomId.matches("LISTING-[0-9]+")) {
            Long listingId = Long.valueOf(roomId.substring(8));
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title, owner_user_id FROM property_listing WHERE property_id=? AND status='active'", listingId);
            if (rows.isEmpty()) throw new IllegalArgumentException("현재 방문 신청 가능한 매물이 아닙니다.");
            Object owner = rows.get(0).get("owner_user_id");
            ownerId = owner == null ? null : ((Number) owner).longValue();
            title = String.valueOf(rows.get(0).get("title"));
        } else {
            LifestyleProperty property = lifestylePropertyRepository
                .findByPropertyCodeAndActiveTrueAndStatus(roomId, READY)
                .orElseThrow(() -> new IllegalArgumentException("현재 등록된 방문 가능 매물이 아닙니다."));
            title = property.getTitle();
            ownerId = roomId.matches("OFFER-[0-9]+")
                ? roomOfferRepository.findById(Long.valueOf(roomId.substring(6)))
                    .map(RoomOffer::getOwnerUserId).orElse(null) : null;
        }
        if (Objects.equals(userId, ownerId)) {
            throw new IllegalArgumentException("본인이 등록한 매물에는 방문 신청할 수 없습니다.");
        }
        if (!roomVisitRepository.findByRoomIdAndVisitDateAndVisitTimeAndStatus(
                roomId, request.getDate(), request.getTime(), APPROVED).isEmpty()) {
            throw new IllegalArgumentException("이미 확정된 방문 일정입니다. 다른 시간을 선택해 주세요.");
        }
        RoomVisit visit = new RoomVisit();
        visit.setRoomId(roomId);
        visit.setTitle(title);
        visit.setApplicantUserId(userId);
        visit.setOwnerUserId(ownerId);
        visit.setVisitDate(request.getDate());
        visit.setVisitTime(request.getTime());
        visit.setPhone(request.getPhone().trim());
        visit.setQuestion(normalizeOptional(request.getQuestion()));
        visit.setStatus(PENDING);

        visit = roomVisitRepository.save(visit);
        if (ownerId != null) notifyVisit(ownerId, "새 방문 요청", title + " 방문 요청이 도착했습니다.");
        return RoomVisitResponse.from(visit).forViewer(userId, visit);
    }

    @Transactional
    public RoomVisitResponse approveVisit(Long visitId, Long userId) {
        RoomVisit visit = findVisit(visitId);
        if (!Objects.equals(userId, visit.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 매물 등록자만 처리할 수 있습니다.");
        }
        lockRoom(visit.getRoomId());
        visit = findVisit(visitId);
        requirePending(visit);

        boolean slotTaken = !jdbc.queryForList(
            "SELECT visit_id FROM room_visit WHERE room_id=? AND visit_date=? AND visit_time=? AND status='approved' AND visit_id<>? FOR UPDATE",
            visit.getRoomId(), visit.getVisitDate(), visit.getVisitTime(), visitId).isEmpty();

        if (slotTaken) {
            throw new IllegalArgumentException(
                    "이미 승인된 방문 일정입니다. 다른 시간을 선택해 주세요.");
        }

        RoomVisit updated = changePendingStatus(visit, APPROVED, userId);
        if (updated.getApplicantUserId() != null) {
            notifyVisit(updated.getApplicantUserId(), "방문 요청 처리", updated.getTitle() + " 방문 요청이 "
                + (APPROVED.equals(updated.getStatus()) ? "승인" : "거절") + "되었습니다.");
        }
        return RoomVisitResponse.from(updated).forViewer(userId, updated);
    }

    @Transactional
    public RoomVisitResponse rejectVisit(Long visitId, Long userId) {
        RoomVisit visit = findVisit(visitId);
        if (!Objects.equals(userId, visit.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 매물 등록자만 처리할 수 있습니다.");
        }
        requirePending(visit);

        RoomVisit updated = changePendingStatus(visit, REJECTED, userId);
        if (updated.getApplicantUserId() != null) {
            notifyVisit(updated.getApplicantUserId(), "방문 요청 처리", updated.getTitle() + " 방문 요청이 "
                + (APPROVED.equals(updated.getStatus()) ? "승인" : "거절") + "되었습니다.");
        }
        return RoomVisitResponse.from(updated).forViewer(userId, updated);
    }

    @Transactional(readOnly = true)
    public List<RoomOfferResponse> getOffers(Long userId) {
        return roomOfferRepository.findByOwnerUserIdOrderByOfferIdDesc(userId)
                .stream()
                .map(this::toOfferResponse)
                .toList();
    }

    @Transactional
    public RoomOfferResponse createOffer(RoomOfferRequest request, Long userId) {
        return createOffer(request, new MultipartFile[0], userId);
    }

    @Transactional
    public RoomOfferResponse createOffer(
            RoomOfferRequest request,
            MultipartFile[] images, Long userId) {
        validateOfferRequest(request);
        validateOfferImages(images);

        RoomOffer offer = new RoomOffer();
        offer.setOwnerUserId(userId);
        offer.setTitle(request.getTitle().trim());
        offer.setDistrict(request.getDistrict().trim());
        offer.setDeposit(request.getDeposit());
        offer.setMonthly(request.getMonthly());
        offer.setMaintenance(request.getMaintenance());
        offer.setContractEnd(request.getContractEnd());
        offer.setMoveIn(request.getMoveIn());
        offer.setAvailableTime(request.getAvailableTime().trim());
        offer.setAgreement(request.getAgreement().trim());
        offer.setDescription(normalizeOptional(request.getDescription()));
        offer.setStatus(READY);

        offer = roomOfferRepository.save(offer);
        RegionParts region = resolveRegion(request.getDistrict());

        LifestyleProperty property = new LifestyleProperty();
        property.setPropertyCode("OFFER-" + offer.getOfferId());
        property.setSido(region.sido());
        property.setSigungu(region.sigungu());
        property.setDong(region.detail());
        property.setTitle(offer.getTitle());
        property.setDeposit(offer.getDeposit());
        property.setMonthlyRent(offer.getMonthly());
        property.setMaintenanceFee(offer.getMaintenance());
        property.setAvailableTime(offer.getAvailableTime());
        property.setStatus(READY);
        property.setActive(Boolean.TRUE);
        property.setSampleData(Boolean.FALSE);

        List<PropertyImageStorageService.StoredImage> storedImages = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    storedImages.add(imageStorageService.store(image));
                }
            }
        }

        if (!storedImages.isEmpty()) {
            property.setThumbnailUrl(storedImages.get(0).imageUrl());
            property.setPhotoCredit("등록자 업로드");
        }

        property = lifestylePropertyRepository.save(property);

        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < storedImages.size(); i++) {
            PropertyImageStorageService.StoredImage stored = storedImages.get(i);

            PropertyImage image = new PropertyImage();
            image.setPropertyId(property.getPropertyId());
            image.setImageUrl(stored.imageUrl());
            image.setOriginalName(stored.originalName());
            image.setStoredName(stored.storedName());
            image.setSortOrder(i);
            image.setRepresentative(i == 0);
            propertyImageRepository.save(image);

            imageUrls.add(stored.imageUrl());
        }

        return RoomOfferResponse.from(offer, property.getPropertyCode(), imageUrls);
    }

    private RoomOfferResponse toOfferResponse(RoomOffer offer) {
        String propertyCode = "OFFER-" + offer.getOfferId();

        return lifestylePropertyRepository
                .findByPropertyCodeAndActiveTrueAndStatus(propertyCode, READY)
                .map(property -> RoomOfferResponse.from(
                        offer,
                        property.getPropertyCode(),
                        propertyImageRepository
                                .findByPropertyIdOrderBySortOrderAscImageIdAsc(
                                        property.getPropertyId())
                                .stream()
                                .map(PropertyImage::getImageUrl)
                                .toList()))
                .orElseGet(() -> RoomOfferResponse.from(offer));
    }

    private void validateOfferImages(MultipartFile[] images) {
        if (images == null || images.length == 0) return;

        int count = 0;
        for (MultipartFile image : images) {
            if (image != null && !image.isEmpty()) count++;
        }
        if (count > 5) {
            throw new IllegalArgumentException("방 사진은 최대 5장까지 업로드할 수 있습니다.");
        }
    }

    private RegionParts resolveRegion(String district) {
        String normalized = district == null ? "" : district.trim();

        return lifestyleAreaRepository
                .findByActiveTrueOrderBySidoAscSigunguAscDongAsc()
                .stream()
                .filter(area -> normalized.contains(area.getSigungu()))
                .findFirst()
                .map(area -> {
                    String detail = normalized
                            .replace(area.getSido(), "")
                            .replace(area.getSigungu(), "")
                            .trim();
                    return new RegionParts(
                            area.getSido(),
                            area.getSigungu(),
                            detail.isBlank() ? null : detail);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lifestyle 추천 지역과 연결할 수 있도록 지역에 시·군명을 정확히 입력해 주세요. 예: 성남시 분당구"));
    }

    private record RegionParts(String sido, String sigungu, String detail) {}

    private void notifyVisit(Long userId, String title, String message) {
        jdbc.update("INSERT INTO user_notification (user_id, notification_type, title, message, target_url, is_read, created_at) VALUES (?, 'visit_status', ?, ?, '/member/mypage#my-visits', FALSE, ?)",
            userId, title, message, java.time.LocalDateTime.now());
    }

    private void lockRoom(String roomId) {
        if (roomId.matches("LISTING-[0-9]+")) {
            jdbc.queryForList("SELECT property_id FROM property_listing WHERE property_id=? FOR UPDATE",
                Long.valueOf(roomId.substring(8)));
        } else {
            jdbc.queryForList("SELECT property_id FROM lifestyle_property WHERE property_code=? FOR UPDATE", roomId);
        }
    }

    private RoomVisit findVisit(Long visitId) {
        if (visitId == null) {
            throw new IllegalArgumentException("방문 요청 번호가 필요합니다.");
        }
        return roomVisitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "방문 요청을 찾을 수 없습니다."));
    }

    private void requirePending(RoomVisit visit) {
        if (!PENDING.equals(visit.getStatus())) {
            throw new IllegalArgumentException(
                    "이미 처리된 방문 요청입니다.");
        }
    }

    private RoomVisit changePendingStatus(RoomVisit visit, String status, Long ownerId) {
        int changed = jdbc.update("UPDATE room_visit SET status=?, updated_at=? WHERE visit_id=? AND owner_user_id=? AND status='pending'",
            status, java.time.LocalDateTime.now(), visit.getVisitId(), ownerId);
        if (changed == 0) throw new IllegalArgumentException("이미 처리된 방문 요청입니다. 현황을 새로고침해 주세요.");
        visit.setStatus(status);
        return visit;
    }

    private void validateVisitRequest(RoomVisitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("방문 신청 내용이 없습니다.");
        }
        requireText(request.getRoomId(), "방을 선택해 주세요.");

        if (request.getDate() == null) {
            throw new IllegalArgumentException("방문 희망일을 선택해 주세요.");
        }
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("지난 날짜에는 방문 신청을 할 수 없습니다.");
        }
        if (request.getTime() == null) {
            throw new IllegalArgumentException("방문 시간을 선택해 주세요.");
        }
        requireText(request.getPhone(), "연락처를 입력해 주세요.");
        if (!request.getPhone().trim().matches("[0-9+() -]{8,20}")) {
            throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        }
    }

    private void validateOfferRequest(RoomOfferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("방 등록 내용이 없습니다.");
        }
        requireText(request.getTitle(), "방 제목을 입력해 주세요.");
        requireText(request.getDistrict(), "지역을 입력해 주세요.");
        requireNonNegative(request.getDeposit(), "보증금");
        requireNonNegative(request.getMonthly(), "월세");
        requireNonNegative(request.getMaintenance(), "관리비");
        if (request.getContractEnd() == null) {
            throw new IllegalArgumentException("계약 종료일을 입력해 주세요.");
        }
        if (request.getMoveIn() == null) {
            throw new IllegalArgumentException("입주 가능일을 입력해 주세요.");
        }
        requireText(request.getAvailableTime(), "방문 가능 시간을 입력해 주세요.");
        requireText(request.getAgreement(), "집주인·중개사 협의 상태를 선택해 주세요.");
    }

    private void requireNonNegative(Long value, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(label + "은(는) 0 이상의 값이어야 합니다.");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
