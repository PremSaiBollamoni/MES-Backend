package com.antigravity.mes.mail;

import com.antigravity.mes.security.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** The single SMTP configuration row (id always 1), editable from the SUPER_ADMIN settings screen. */
@Entity
@Table(name = "mail_settings")
public class MailSettings {

    @Id
    private Long id;

    @Column(name = "smtp_host", length = 150, nullable = false)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_username", length = 150)
    private String smtpUsername;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "smtp_password", length = 500)
    private String smtpPassword;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
