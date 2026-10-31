package com.antigravity.mes.web;

import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.RoleRepository;
import com.antigravity.mes.security.JwtService;
import com.antigravity.mes.security.TokenPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Gates any controller method annotated with {@link RequiresActivity}: the caller must
 * present a valid JWT (issued at login) whose role has that activity in its allow-list
 * (roles.activities) and whose role is still ACTIVE. Endpoints without the annotation
 * (health check, login) are left untouched.
 *
 * On success, publishes the caller's identity as request attributes ("employeeId",
 * "role") so self-service endpoints (e.g. Profile) can read them via
 * {@code @RequestAttribute} without a separate auth lookup.
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresActivity required = handlerMethod.getMethodAnnotation(RequiresActivity.class);
        if (required == null) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return false;
        }

        Optional<TokenPrincipal> principal = jwtService.validate(authHeader.substring(7));
        if (principal.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, ErrorCodes.SESSION_INVALID);
            return false;
        }

        TokenPrincipal p = principal.get();

        // SUPER_ADMIN bypass: check all activities since they manage the entire system
        if (p.isSuperAdmin()) {
            request.setAttribute("employeeId", p.employeeId());
            request.setAttribute("role", p.role());
            request.setAttribute("factoryId", p.factoryId());
            request.setAttribute("isSuperAdmin", true);
            return true;
        }

        // Roles are global - shared by every factory - so this is a plain name lookup.
        Optional<Role> role = roleRepository.findByRoleName(p.role());
        if (role.isEmpty() || !role.get().isActive() || !role.get().canAccess(required.value())) {
            reject(response, HttpStatus.FORBIDDEN, ErrorCodes.NO_ACCESS);
            return false;
        }

        request.setAttribute("employeeId", p.employeeId());
        request.setAttribute("role", p.role());
        request.setAttribute("factoryId", p.factoryId());
        request.setAttribute("isSuperAdmin", false);
        return true;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String codeOrMessage) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json");
        // Codes travel alongside an English fallback so the app can translate; anything
        // that is not a code (e.g. a malformed header) goes out as a plain message.
        if (codeOrMessage.startsWith("ERR_")) {
            response.getWriter().write("{\"code\":\"" + codeOrMessage + "\",\"error\":\"" + ErrorCodes.fallback(codeOrMessage) + "\"}");
        } else {
            response.getWriter().write("{\"error\":\"" + codeOrMessage + "\"}");
        }
    }
}
