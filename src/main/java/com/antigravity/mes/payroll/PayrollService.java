package com.antigravity.mes.payroll;

import com.antigravity.mes.model.EmployeeInfo;
import com.antigravity.mes.model.ProductionEntry;
import com.antigravity.mes.repository.EmployeeInfoRepository;
import com.antigravity.mes.repository.EmployeeRepository;
import com.antigravity.mes.repository.ProductionEntryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;

/** Builds the HR salary payroll sheet: one row per operator, their earnings broken down by
 * station so HR can see exactly how each figure was calculated, for a given date range.
 *
 * Grouped by station, not operation name: the rate shown is always the station's real,
 * currently-configured rate_per_piece (verifiable directly against Station Management), never
 * a derived value. Earnings per entry follow the same formula used everywhere else in the app
 * (ProductionEntryService#toResponse): pieces * station.ratePerPiece / station.operatorCount -
 * so for a station shared by more than one operator, pieces x rate will legitimately be more
 * than earnings (each operator gets their share, not the station's full output), and that is
 * called out on the sheet rather than papered over with a fudged rate. */
@Service
public class PayrollService {

    @Autowired
    private ProductionEntryRepository productionEntryRepository;

    @Autowired
    private EmployeeInfoRepository employeeInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    /** The same rows the Excel sheet uses, as plain data - lets HR preview every operator's
     * earnings breakdown in the app before committing to a download. Scoped to the caller's
     * factory - this was previously unfiltered (findByShiftDateBetween across ALL factories),
     * the same class of cross-factory leak found and fixed in EmployeeManagementService. */
    public List<OperatorPayroll> previewPayroll(LocalDate startDate, LocalDate endDate, Long factoryId) {
        return buildRows(productionEntryRepository.findByFactoryIdAndShiftDateBetween(factoryId, startDate, endDate));
    }

    public byte[] generatePayrollSheet(LocalDate startDate, LocalDate endDate, Long factoryId) {
        List<ProductionEntry> entries = productionEntryRepository.findByFactoryIdAndShiftDateBetween(factoryId, startDate, endDate);
        List<OperatorPayroll> rows = buildRows(entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            PayrollSheetWriter.write(workbook, rows, startDate, endDate);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Failed to build payroll workbook", e);
        }
    }

    private List<OperatorPayroll> buildRows(List<ProductionEntry> entries) {
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        // employeeId -> stationId -> running totals ([pieces, earnings])
        Map<Long, Map<Long, double[]>> perEmployeeStation = new LinkedHashMap<>();
        Map<Long, String> stationOperationName = new HashMap<>();
        Map<Long, Double> stationRate = new HashMap<>();
        Map<Long, Integer> stationOperatorCount = new HashMap<>();
        // employeeId -> stationId -> names of whoever logged an entry into this group -
        // usually the operator themselves, but a supervisor can log on someone else's
        // behalf, and HR wants to know when that happened.
        Map<Long, Map<Long, Set<String>>> perEmployeeStationLoggedBy = new LinkedHashMap<>();
        Map<Long, String> loggerNameCache = new HashMap<>();

        for (ProductionEntry entry : entries) {
            Long employeeId = entry.getEmployee().getId();
            employeeNames.putIfAbsent(employeeId, entry.getEmployee().getName());

            Long stationId = entry.getStation().getId();
            stationOperationName.putIfAbsent(stationId, entry.getStation().getOperationName());
            stationRate.putIfAbsent(stationId, entry.getStation().getRatePerPiece());
            stationOperatorCount.putIfAbsent(stationId, entry.getStation().getOperatorCount());

            double earned = (entry.getPiecesProduced() * entry.getStation().getRatePerPiece())
                    / entry.getStation().getOperatorCount();

            Map<Long, double[]> stations = perEmployeeStation.computeIfAbsent(employeeId, k -> new LinkedHashMap<>());
            double[] totals = stations.computeIfAbsent(stationId, k -> new double[2]);
            totals[0] += entry.getPiecesProduced();
            totals[1] += earned;

            Long loggedByEmployeeId = entry.getEnteredByEmployeeId();
            String loggerName = loggerNameCache.computeIfAbsent(loggedByEmployeeId,
                    id -> employeeRepository.findById(id).map(e -> e.getName()).orElse("Unknown"));
            perEmployeeStationLoggedBy
                    .computeIfAbsent(employeeId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(stationId, k -> new LinkedHashSet<>())
                    .add(loggerName);
        }

        List<OperatorPayroll> result = new ArrayList<>();
        for (var employeeEntry : perEmployeeStation.entrySet()) {
            Long employeeId = employeeEntry.getKey();
            List<OperationLine> lines = new ArrayList<>();
            double total = 0;
            List<Long> stationIds = new ArrayList<>(employeeEntry.getValue().keySet());
            stationIds.sort(Comparator.comparing(stationOperationName::get, String.CASE_INSENSITIVE_ORDER));
            for (Long stationId : stationIds) {
                double[] totals = employeeEntry.getValue().get(stationId);
                int pieces = (int) Math.round(totals[0]);
                double earnings = totals[1];
                // The station's real, currently-configured rate - never derived from
                // earnings/pieces, so it always matches what Station Management shows.
                double rate = stationRate.get(stationId);
                int operatorCount = stationOperatorCount.get(stationId);
                String loggedBy = String.join(", ", perEmployeeStationLoggedBy.get(employeeId).get(stationId));
                lines.add(new OperationLine(stationOperationName.get(stationId), pieces, rate, earnings, operatorCount, loggedBy));
                total += earnings;
            }
            EmployeeInfo info = employeeInfoRepository.findByEmployeeId(employeeId).orElse(null);
            result.add(new OperatorPayroll(
                    employeeNames.get(employeeId),
                    lines,
                    total,
                    info != null ? info.getBankName() : null,
                    info != null ? info.getBankAccountNumber() : null,
                    info != null ? info.getIfscCode() : null));
        }

        result.sort(Comparator.comparing(OperatorPayroll::operatorName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    record OperationLine(String operationName, int pieces, double rate, double earnings, int operatorCount, String loggedBy) {}

    record OperatorPayroll(String operatorName, List<OperationLine> operations, double totalEarnings,
                            String bankName, String bankAccountNumber, String ifscCode) {}
}
