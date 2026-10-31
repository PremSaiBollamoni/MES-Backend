package com.antigravity.mes.support;

import com.antigravity.mes.model.SupportTicket;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.SupportTicketRepository;
import com.antigravity.mes.web.RequiresActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@CrossOrigin(origins = "*")
public class SupportController {

    @Autowired
    private SupportChatService chatService;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    public record ChatRequest(List<Map<String, String>> history) {}
    public record ChatResponse(String reply) {}
    public record RaiseTicketRequest(String subject, String description) {}
    public record RaisePublicTicketRequest(String username, String subject, String description) {}
    public record UpdateTicketRequest(String status, String resolutionNotes) {}
    public record DraftTicketResponse(String subject, String description) {}

    @RequiresActivity("SUPPORT_CHAT")
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        return doChat(request);
    }

    /** Unauthenticated - reachable from the Login screen before anyone's signed in
     * (e.g. "forgot my password", "app won't open"). Answers the same way as the
     * in-app chat; only ticket raising needs an identity, which the public ticket
     * endpoint below collects explicitly instead of relying on a JWT. */
    @PostMapping("/chat/public")
    public ResponseEntity<?> publicChat(@RequestBody ChatRequest request) {
        return doChat(request);
    }

    private ResponseEntity<?> doChat(ChatRequest request) {
        if (!chatService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Support chat is currently unavailable"));
        }
        try {
            String reply = chatService.reply(request.history());
            return ResponseEntity.ok(new ChatResponse(reply));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Couldn't reach the support assistant right now"));
        }
    }

    @RequiresActivity("SUPPORT_CHAT")
    @PostMapping("/draft-ticket")
    public ResponseEntity<?> draftTicket(@RequestBody ChatRequest request) {
        return doDraftTicket(request);
    }

    /** Unauthenticated counterpart to /chat/public - lets the Login screen's chat also
     * offer an AI-drafted ticket before the employee fills in their username. */
    @PostMapping("/draft-ticket/public")
    public ResponseEntity<?> publicDraftTicket(@RequestBody ChatRequest request) {
        return doDraftTicket(request);
    }

    private ResponseEntity<?> doDraftTicket(ChatRequest request) {
        if (!chatService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Support chat is currently unavailable"));
        }
        try {
            var draft = chatService.draftTicket(request.history());
            return ResponseEntity.ok(new DraftTicketResponse(draft.subject(), draft.description()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Couldn't draft a ticket right now"));
        }
    }

    @RequiresActivity("SUPPORT_CHAT")
    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> raiseTicket(
            @RequestBody RaiseTicketRequest request,
            @RequestAttribute("employeeId") Long employeeId,
            @RequestAttribute(required = false) Long factoryId) {
        var employee = employeeRepository.findById(employeeId).orElseThrow();

        SupportTicket ticket = new SupportTicket();
        ticket.setFactoryId(factoryId != null ? factoryId : employee.getFactoryId());
        ticket.setEmployeeId(employeeId);
        ticket.setEmployeeName(employee.getName());
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setStatus(SupportTicket.Status.OPEN);

        return ResponseEntity.ok(ticketRepository.save(ticket));
    }

    /** Unauthenticated - reachable from the Login screen. Since there's no JWT to read an
     * identity from, the employee identifies themselves by username instead; a wrong or
     * unknown username is rejected rather than silently filed against nobody. */
    @PostMapping("/tickets/public")
    public ResponseEntity<?> raisePublicTicket(@RequestBody RaisePublicTicketRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        var employeeOpt = employeeRepository.findByUsername(request.username().trim());
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No employee found with that username"));
        }
        var employee = employeeOpt.get();

        SupportTicket ticket = new SupportTicket();
        ticket.setFactoryId(employee.getFactoryId());
        ticket.setEmployeeId(employee.getId());
        ticket.setEmployeeName(employee.getName());
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setStatus(SupportTicket.Status.OPEN);

        return ResponseEntity.ok(ticketRepository.save(ticket));
    }

    /** SUPER_ADMIN-only in practice: SUPPORT_TICKETS_ADMIN is granted to no other role,
     * and SUPER_ADMIN bypasses activity checks entirely (RoleAuthorizationInterceptor). */
    @RequiresActivity("SUPPORT_TICKETS_ADMIN")
    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicket>> listTickets() {
        return ResponseEntity.ok(ticketRepository.findAllByOrderByCreatedAtDesc());
    }

    @RequiresActivity("SUPPORT_TICKETS_ADMIN")
    @PutMapping("/tickets/{id}")
    public ResponseEntity<?> updateTicket(@PathVariable Long id, @RequestBody UpdateTicketRequest request) {
        return ticketRepository.findById(id).<ResponseEntity<?>>map(ticket -> {
            if (request.status() != null) {
                ticket.setStatus(SupportTicket.Status.valueOf(request.status()));
            }
            if (request.resolutionNotes() != null) {
                ticket.setResolutionNotes(request.resolutionNotes());
            }
            return ResponseEntity.ok(ticketRepository.save(ticket));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Ticket not found")));
    }
}
