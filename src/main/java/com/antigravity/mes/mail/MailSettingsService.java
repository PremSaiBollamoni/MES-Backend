package com.antigravity.mes.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
public class MailSettingsService {

    public record View(String smtpHost, int smtpPort, String smtpUsername, boolean passwordSet) {}

    @Autowired
    private MailSettingsRepository repository;

    @Autowired
    private MailSenderProvider senderProvider;

    public View get() {
        MailSettings s = current();
        return new View(s.getSmtpHost(), s.getSmtpPort(), s.getSmtpUsername(),
                s.getSmtpPassword() != null && !s.getSmtpPassword().isBlank());
    }

    /** A blank password means "keep the one already saved" - the screen never shows the stored value. */
    public View save(String host, int port, String username, String password) {
        MailSettings s = current();
        s.setSmtpHost(host.trim());
        s.setSmtpPort(port);
        s.setSmtpUsername(username == null || username.isBlank() ? null : username.trim());
        if (password != null && !password.isBlank()) {
            s.setSmtpPassword(password);
        }
        repository.save(s);
        return get();
    }

    public void sendTest(String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject("SMO test email");
        message.setText("This is a test email from SMO. If you can read this, the SMTP settings work.");
        senderProvider.current().send(message);
    }

    private MailSettings current() {
        return repository.findById(MailSenderProvider.SETTINGS_ID).orElseThrow(() -> new IllegalStateException("Mail settings row is missing"));
    }
}
