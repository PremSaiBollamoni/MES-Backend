package com.antigravity.mes.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class MailRecipientService {

    public enum ReportKind { PAYROLL, ATTENDANCE, PRODUCTION }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Autowired
    private MailRecipientRepository repository;

    public List<MailRecipient> list() {
        return repository.findAllByOrderByNameAscEmailAsc();
    }

    public MailRecipient create(String email, String name, boolean payroll, boolean attendance, boolean production, boolean active) {
        String clean = normalizeEmail(email);
        if (repository.findByEmailIgnoreCase(clean).isPresent()) {
            throw new IllegalArgumentException("That email is already on the list");
        }
        MailRecipient r = new MailRecipient();
        apply(r, clean, name, payroll, attendance, production, active);
        return repository.save(r);
    }

    public MailRecipient update(Long id, String email, String name, boolean payroll, boolean attendance, boolean production, boolean active) {
        MailRecipient r = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Recipient not found"));
        String clean = normalizeEmail(email);
        Optional<MailRecipient> clash = repository.findByEmailIgnoreCase(clean);
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new IllegalArgumentException("That email is already on the list");
        }
        apply(r, clean, name, payroll, attendance, production, active);
        return repository.save(r);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** Active recipients who opted in to this report type - the list every send uses. */
    public List<String> emailsFor(ReportKind kind) {
        Predicate<MailRecipient> optedIn = switch (kind) {
            case PAYROLL -> MailRecipient::isReceivesPayroll;
            case ATTENDANCE -> MailRecipient::isReceivesAttendance;
            case PRODUCTION -> MailRecipient::isReceivesProduction;
        };
        return repository.findAllByOrderByNameAscEmailAsc().stream()
                .filter(MailRecipient::isActive)
                .filter(optedIn)
                .map(MailRecipient::getEmail)
                .toList();
    }

    private void apply(MailRecipient r, String email, String name, boolean payroll, boolean attendance, boolean production, boolean active) {
        r.setEmail(email);
        r.setName(name == null || name.isBlank() ? null : name.trim());
        r.setReceivesPayroll(payroll);
        r.setReceivesAttendance(attendance);
        r.setReceivesProduction(production);
        r.setActive(active);
    }

    private static String normalizeEmail(String email) {
        String clean = email == null ? "" : email.trim();
        if (!EMAIL.matcher(clean).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return clean;
    }
}
