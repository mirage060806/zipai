package com.zipai.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "visit_requests")
public class VisitRequest extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requester_id")
    public User requester;
    @Column(name = "room_id", nullable = false) public String roomId;
    @Column(name = "room_title", nullable = false) public String roomTitle;
    @Column(name = "visit_date", nullable = false) public LocalDate visitDate;
    @Column(name = "visit_time", nullable = false) public LocalTime visitTime;
    @Column(nullable = false) public String phone;
    @Column(nullable = false, length = 1000) public String question = "";
    @Column(nullable = false, length = 20) public String status = "pending";
}
