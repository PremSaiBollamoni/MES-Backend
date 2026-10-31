package com.antigravity.mes.service;

import com.antigravity.mes.dto.DashboardSummaryResponse;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.model.Role;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int RECENT_JOINERS_LIMIT = 5;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    /**
     * Scoped to the caller's factory - this was previously unfiltered (findAll()), the
     * same class of cross-factory leak found and fixed in EmployeeManagementService.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long factoryId) {
        List<Employee> employees = employeeRepository.findByFactoryId(factoryId).stream()
                .filter(e -> !e.hasActiveRoleNamed("SUPER_ADMIN"))
                .toList();
        long total = employees.size();
        long active = employees.stream().filter(Employee::isActive).count();
        long inactive = total - active;

        // An employee with multiple roles is counted once per role they hold, so these
        // counts can sum to more than the total employee count - that's expected for a
        // "how many people can do X" breakdown, not a partition of the workforce.
        Map<String, Long> byRole = employees.stream()
                .flatMap(e -> e.getRoles().stream())
                .collect(Collectors.groupingBy(Role::getRoleName, Collectors.counting()));

        List<EmployeeInfo> infos = employeeInfoRepository.findByFactoryId(factoryId);

        Map<String, Long> byDepartment = infos.stream()
                .map(i -> (i.getDepartment() == null || i.getDepartment().isBlank()) ? "Unassigned" : i.getDepartment())
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        List<DashboardSummaryResponse.RecentJoiner> recentJoiners = infos.stream()
                .filter(i -> i.getDateOfJoining() != null)
                .sorted(Comparator.comparing(EmployeeInfo::getDateOfJoining).reversed())
                .limit(RECENT_JOINERS_LIMIT)
                .map(i -> new DashboardSummaryResponse.RecentJoiner(
                        i.getEmployee().getName(), i.getEmployeeCode(), i.getDepartment(), i.getDateOfJoining()))
                .toList();

        return new DashboardSummaryResponse(total, active, inactive, byRole, byDepartment, recentJoiners);
    }
}
