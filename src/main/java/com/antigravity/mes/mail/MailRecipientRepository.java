package com.antigravity.mes.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MailRecipientRepository extends JpaRepository<MailRecipient, Long> {
    List<MailRecipient> findAllByOrderByNameAscEmailAsc();
    Optional<MailRecipient> findByEmailIgnoreCase(String email);
}
