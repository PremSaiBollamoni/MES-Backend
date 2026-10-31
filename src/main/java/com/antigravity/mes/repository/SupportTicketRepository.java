package com.antigravity.mes.repository;

import com.antigravity.mes.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    List<SupportTicket> findByFactoryIdOrderByCreatedAtDesc(Long factoryId);
}
