package com.antigravity.mes.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/** Hands out the mail sender to use right now: built from the saved SMTP settings when they're
 * complete, otherwise the default sender configured from the MAIL_* environment variables. */
@Component
public class MailSenderProvider {

    static final long SETTINGS_ID = 1L;

    @Autowired
    private MailSettingsRepository mailSettingsRepository;

    @Autowired
    private JavaMailSender defaultSender;

    public JavaMailSender current() {
        MailSettings settings = mailSettingsRepository.findById(SETTINGS_ID).orElse(null);
        if (settings == null || isBlank(settings.getSmtpUsername()) || isBlank(settings.getSmtpPassword())) {
            return defaultSender;
        }
        return build(settings.getSmtpHost(), settings.getSmtpPort(), settings.getSmtpUsername(), settings.getSmtpPassword());
    }

    /** Also used by the Test Email button, so a candidate configuration can be checked before it is saved. */
    public static JavaMailSender build(String host, int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        return sender;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
