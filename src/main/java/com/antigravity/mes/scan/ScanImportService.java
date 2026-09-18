package com.antigravity.mes.scan;

import com.antigravity.mes.model.Employee;
import com.antigravity.mes.model.Station;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.StationRepository;
import com.antigravity.mes.scan.dto.ConfirmScanRequest;
import com.antigravity.mes.scan.dto.ScannedPage;
import com.antigravity.mes.scan.dto.ScannedRow;
import com.antigravity.mes.service.ProductionEntryService;
import com.antigravity.mes.web.ErrorCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns a raw page reading into a reviewable draft, and a confirmed draft into production entries. */
@Service
public class ScanImportService {

    @Autowired private PageScanService pageScanService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private ProductionEntryService productionEntryService;

    // Books write the date loosely - "18/07-2026", "18/07/2026", "18-07-2026" all appear.
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\D+(\\d{1,2})\\D+(\\d{2,4})");

    /**
     * Everyone who can own a production book.
     *
     * Deliberately not today's attendance roster: books being imported are historical, so the
     * worker whose book this is may not be present today - or at all any more.
     */
    public List<ScannableEmployee> scannableEmployees() {
        return employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(e -> new ScannableEmployee(e.getId(), e.getName(), e.getUsername()))
                .toList();
    }

    public record ScannableEmployee(Long id, String name, String username) {}

    public ScannedPage scan(byte[] image, String contentType, Long employeeId, LocalDate expectedDate)
            throws Exception {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorCodes.EMPLOYEE_NOT_FOUND));

        PageScanService.ScanReading reading = pageScanService.read(image, contentType);
        Map<Long, Station> stations = stationRepository.findAll().stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));

        List<ScannedRow> rows = applyCarryForward(reading.rows(), stations);
        LocalDate shiftDate = parseDate(reading.rawDate());

        String warning = null;
        if (shiftDate == null) {
            warning = "Could not read the date on this page - set it yourself before saving.";
        } else if (shiftDate.isAfter(LocalDate.now())) {
            // A book page cannot be from the future, so this is a misread year rather than a real
            // date - and it is the one error that hides the whole import, since the entries land
            // on a date nobody thinks to look at.
            warning = "Page reads " + shiftDate + ", which is in the future - check the year before saving.";
        } else if (expectedDate != null && !expectedDate.equals(shiftDate)) {
            warning = "Page reads " + shiftDate + " but " + expectedDate + " was expected from the book order.";
        }

        return new ScannedPage(
                employee.getId(), employee.getName(), shiftDate, reading.rawDate(), warning, rows,
                (int) rows.stream().filter(ScannedRow::needsAttention).count());
    }

    /**
     * Fills in the station on rows whose style was left blank because the operation continued from
     * the row above. The book only writes the style when it changes, so this is a deterministic
     * rule rather than an inference - but it is applied here, on data we control, instead of being
     * left to the reading step.
     */
    private List<ScannedRow> applyCarryForward(List<ScannedRow> raw, Map<Long, Station> stations) {
        List<ScannedRow> result = new ArrayList<>();
        Long lastStationId = null;

        for (ScannedRow row : raw.stream().sorted((a, b) -> a.hour() - b.hour()).toList()) {
            if (!ShiftHours.isValid(row.hour())) continue;

            Long stationId = row.stationId();
            boolean carried = row.carriedForward();
            if (stationId == null && carried && lastStationId != null) {
                stationId = lastStationId;
            }
            if (stationId != null) {
                lastStationId = stationId;
            }

            Station station = stationId == null ? null : stations.get(stationId);
            result.add(new ScannedRow(
                    row.hour(), station == null ? null : station.getId(),
                    station == null ? null : station.getOperationName(),
                    row.rawStyle(), carried, row.pieces(), row.note()));
        }
        return result;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        Matcher m = DATE.matcher(raw);
        if (!m.find()) return null;
        try {
            int year = Integer.parseInt(m.group(3));
            if (year < 100) year += 2000;
            return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes the confirmed rows to the production log. Each count becomes its own entry inside a
     * slice of its hour, so the normal validation - no overlaps, never more than sixty minutes in
     * an hour - applies to imported history exactly as it does to entries typed in by hand.
     */
    @Transactional
    public int confirm(ConfirmScanRequest request, Long enteredBy) {
        if (request.shiftDate() == null) {
            throw new IllegalArgumentException(ErrorCodes.SCAN_DATE_REQUIRED);
        }
        int saved = 0;

        for (ConfirmScanRequest.ConfirmedRow row : request.rows()) {
            if (row.stationId() == null || row.pieces() == null || row.pieces().isEmpty()) continue;
            if (!ShiftHours.isValid(row.hour())) {
                throw new IllegalArgumentException(ErrorCodes.SCAN_HOUR_INVALID);
            }

            List<LocalTime[]> slices = ShiftHours.slice(row.hour(), row.pieces().size());
            for (int i = 0; i < row.pieces().size(); i++) {
                LocalTime[] slice = slices.get(i);
                productionEntryService.logEntry(
                        request.employeeId(), row.stationId(), request.shiftDate(),
                        slice[0], slice[1], row.pieces().get(i), null, null, enteredBy);
                saved++;
            }
        }
        return saved;
    }

    static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
}
