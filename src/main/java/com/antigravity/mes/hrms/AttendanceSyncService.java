package com.antigravity.mes.hrms;

import com.antigravity.mes.hrms.dto.HrmsDailyAttendance;
import com.antigravity.mes.model.AttendanceRecord;
import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.repository.AttendanceRecordRepository;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Pulls attendance day-by-day from HRMS and upserts it into our own attendance_records
 * table, so the app has a full local history instead of only whatever single date HRMS's
 * /api/attendance/daily happens to return. HRMS records are matched to local employees via
 * hrms_employee_code (set when an employee is created/imported/synced) - unmatched records
 * (e.g. a device slot enrolled without a corresponding HRMS employee) are counted but skipped.
 */
@Service
public class AttendanceSyncService {

    @Autowired
    private HrmsClient hrmsClient;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Transactional
    public AttendanceSyncResult syncRange(LocalDate from, LocalDate to) {
        int synced = 0;
        int unmatched = 0;
        int days = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days++;
            List<HrmsDailyAttendance> dayRecords = hrmsClient.getDailyAttendance(date);
            for (HrmsDailyAttendance record : dayRecords) {
                if (record.employeeCode() == null) {
                    unmatched++;
                    continue;
                }
                var infoOpt = employeeInfoRepository.findByHrmsEmployeeCode(record.employeeCode());
                if (infoOpt.isEmpty()) {
                    unmatched++;
                    continue;
                }
                upsert(infoOpt.get(), date, record);
                synced++;
            }
        }

        return new AttendanceSyncResult(from, to, days, synced, unmatched);
    }

    private void upsert(EmployeeInfo info, LocalDate date, HrmsDailyAttendance record) {
        Long employeeId = info.getEmployee().getId();
        AttendanceRecord entity = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseGet(AttendanceRecord::new);

        entity.setEmployeeId(employeeId);
        entity.setEmployeeName(info.getEmployee().getName());
        entity.setEmployeeCode(info.getEmployeeCode());
        entity.setDepartmentName(record.departmentName());
        entity.setWorkDate(date);
        entity.setFirstIn(record.firstIn());
        entity.setLastOut(record.lastOut());
        entity.setTotalHours(record.totalHours());
        entity.setStatus(record.status());
        entity.setLate(Boolean.TRUE.equals(record.isLate()));
        entity.setLateMinutes(record.lateMinutes());
        entity.setSyncedAt(java.time.LocalDateTime.now());

        attendanceRecordRepository.save(entity);
    }
}
