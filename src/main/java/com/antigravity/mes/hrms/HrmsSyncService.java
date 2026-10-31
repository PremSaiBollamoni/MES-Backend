package com.antigravity.mes.hrms;

import com.antigravity.mes.hrms.dto.HrmsEmployeeCreate;
import com.antigravity.mes.hrms.dto.HrmsEmployeeOut;
import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mirrors newly-created employees into the HRMS biometric attendance portal, and
 * offers the reverse direction (which HRMS employees have no local counterpart yet)
 * since HRMS employees created directly in the HRMS portal are never pushed to us
 * automatically - see EmployeeManagementService#importFromHrms for the pull side.
 */
@Service
public class HrmsSyncService {

    private static final Logger log = LoggerFactory.getLogger(HrmsSyncService.class);

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    public boolean isEnabled(Long factoryId) {
        return hrmsClient.isEnabled(factoryId);
    }

    /**
     * Best-effort: HRMS being unreachable must never fail or roll back the local
     * employee creation, so every failure is caught and logged, never rethrown.
     * Returns the created HRMS record, or null if sync didn't happen/failed.
     * Uses whichever HRMS connection resolves for the employee's own factory.
     */
    public HrmsEmployeeOut syncNewEmployee(Employee employee, EmployeeInfo info) {
        Long factoryId = employee.getFactoryId();
        if (!hrmsClient.isEnabled(factoryId)) {
            return null;
        }
        try {
            int nextDeviceUserId = nextDeviceUserId(factoryId);
            String deviceUserId = String.valueOf(nextDeviceUserId);
            String hrmsEmployeeCode = String.format("GTG%03d", nextDeviceUserId);

            HrmsEmployeeCreate request = new HrmsEmployeeCreate(
                    hrmsEmployeeCode,
                    deviceUserId,
                    employee.getName(),
                    null,
                    null,
                    null,
                    null,
                    info.getDesignation(),
                    "biometric",
                    null);
            HrmsEmployeeOut created = hrmsClient.createEmployee(factoryId, request);
            log.info("Synced new employee {} to HRMS as {} (device_user_id={}) - "
                            + "enroll this ID on the physical biometric device for attendance to work",
                    employee.getUsername(), hrmsEmployeeCode, deviceUserId);
            return created;
        } catch (Exception e) {
            log.warn("Failed to sync employee {} to HRMS: {}", employee.getUsername(), e.getMessage());
            return null;
        }
    }

    /** HRMS employees with no locally-linked employee_info row yet, from this factory's own HRMS. */
    public List<HrmsEmployeeOut> listUnlinkedEmployees(Long factoryId) {
        Set<String> linkedCodes = employeeInfoRepository.findByFactoryId(factoryId).stream()
                .map(EmployeeInfo::getHrmsEmployeeCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return hrmsClient.listEmployees(factoryId, true).stream()
                .filter(e -> !linkedCodes.contains(e.employeeCode()))
                .toList();
    }

    /** Not best-effort - this is a direct user action, so failures must surface. */
    public void enrollFace(Long factoryId, int hrmsEmployeeId, byte[] fileBytes, String filename) {
        hrmsClient.enrollFace(factoryId, hrmsEmployeeId, fileBytes, filename);
    }

    /**
     * HRMS assigns device_user_id as a plain auto-incrementing integer (matching the
     * physical device's own enrollment slot numbering) - their employee_code convention
     * ("GTG023", "GTG101", ...) is literally that number zero-padded. We mirror both by
     * taking the current max device_user_id in this factory's own HRMS and adding one.
     */
    private int nextDeviceUserId(Long factoryId) {
        return hrmsClient.listEmployees(factoryId, false).stream()
                .map(HrmsEmployeeOut::deviceUserId)
                .filter(id -> id != null && id.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }
}
