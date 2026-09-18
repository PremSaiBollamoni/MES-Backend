package com.antigravity.mes.payroll;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Lays out {@link PayrollService.OperatorPayroll} rows as a proper payroll register: one row
 * per operation (not a wrapped-text blob per operator), the operator's name and total merged
 * down the side of their operation rows, real numeric currency cells (sortable, usable in
 * Excel formulas - not pre-formatted text), alternating shading per operator, and a grand
 * total row at the bottom. */
final class PayrollSheetWriter {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final short BAND_A = IndexedColors.WHITE.getIndex();

    private PayrollSheetWriter() {
    }

    static void write(XSSFWorkbook workbook, List<PayrollService.OperatorPayroll> rows, LocalDate startDate, LocalDate endDate) {
        Sheet sheet = workbook.createSheet("Payroll");
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 26 * 256);
        sheet.setColumnWidth(2, 30 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.setColumnWidth(7, 22 * 256);
        sheet.setColumnWidth(8, 20 * 256);
        sheet.setColumnWidth(9, 14 * 256);

        Styles styles = new Styles(workbook);

        int rowIndex = writeTitleBlock(sheet, styles, startDate, endDate);
        int headerRow = rowIndex;
        writeHeaderRow(sheet, styles, rowIndex++);
        sheet.createFreezePane(0, rowIndex);

        int serial = 1;
        double grandTotal = 0;
        boolean shaded = false;
        for (PayrollService.OperatorPayroll operator : rows) {
            int firstRow = rowIndex;
            List<PayrollService.OperationLine> operations = operator.operations().isEmpty()
                    ? List.of(new PayrollService.OperationLine("-", 0, 0, 0, 1))
                    : operator.operations();

            CellStyle band = shaded ? styles.bodyShaded : styles.body;
            CellStyle bandCenter = shaded ? styles.bodyShadedCenter : styles.bodyCenter;
            CellStyle bandCurrency = shaded ? styles.bodyShadedCurrency : styles.bodyCurrency;

            for (PayrollService.OperationLine line : operations) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(17f);
                setCell(row, 0, serial, bandCenter);
                setCell(row, 1, operator.operatorName(), band);
                String operationLabel = line.operatorCount() > 1
                        ? line.operationName() + " (shared by " + line.operatorCount() + ")"
                        : line.operationName();
                setCell(row, 2, operationLabel, band);
                setCell(row, 3, line.pieces(), bandCenter);
                setCell(row, 4, line.rate(), bandCurrency);
                setCell(row, 5, line.earnings(), bandCurrency);
            }
            int lastRow = rowIndex - 1;

            setCell(sheet.getRow(firstRow), 6, operator.totalEarnings(), styles.totalCurrency(shaded));
            setCell(sheet.getRow(firstRow), 7, nullToDash(operator.bankName()), band);
            setCell(sheet.getRow(firstRow), 8, nullToDash(operator.bankAccountNumber()), band);
            setCell(sheet.getRow(firstRow), 9, nullToDash(operator.ifscCode()), band);
            if (lastRow > firstRow) {
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 0, 0));
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 1, 1));
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 6, 6));
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 7, 7));
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 8, 8));
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 9, 9));
                for (int r = firstRow; r <= lastRow; r++) {
                    if (sheet.getRow(r).getCell(0) == null) setCell(sheet.getRow(r), 0, serial, bandCenter);
                    if (sheet.getRow(r).getCell(6) == null) setCell(sheet.getRow(r), 6, operator.totalEarnings(), styles.totalCurrency(shaded));
                    if (sheet.getRow(r).getCell(7) == null) setCell(sheet.getRow(r), 7, nullToDash(operator.bankName()), band);
                    if (sheet.getRow(r).getCell(8) == null) setCell(sheet.getRow(r), 8, nullToDash(operator.bankAccountNumber()), band);
                    if (sheet.getRow(r).getCell(9) == null) setCell(sheet.getRow(r), 9, nullToDash(operator.ifscCode()), band);
                }
            }

            grandTotal += operator.totalEarnings();
            serial++;
            shaded = !shaded;
        }

        writeGrandTotalRow(sheet, styles, rowIndex, grandTotal);
        sheet.setAutoFilter(new CellRangeAddress(headerRow, headerRow, 0, 9));
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private static int writeTitleBlock(Sheet sheet, Styles styles, LocalDate startDate, LocalDate endDate) {
        int rowIndex = 0;
        Row title = sheet.createRow(rowIndex++);
        title.setHeightInPoints(24);
        setCell(title, 0, "Salary Payroll Sheet", styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row subtitle = sheet.createRow(rowIndex++);
        setCell(subtitle, 0, "Period: " + startDate.format(DATE_LABEL) + " to " + endDate.format(DATE_LABEL), styles.subtitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

        rowIndex++; // spacer row
        return rowIndex;
    }

    private static void writeHeaderRow(Sheet sheet, Styles styles, int rowIndex) {
        Row header = sheet.createRow(rowIndex);
        header.setHeightInPoints(22);
        String[] headers = {"S.No", "Operator Name", "Operation", "Pieces", "Rate (₹)", "Earnings (₹)", "Total Earnings (₹)",
                "Bank Name", "Account Number", "IFSC Code"};
        for (int col = 0; col < headers.length; col++) {
            setCell(header, col, headers[col], styles.header);
        }
    }

    private static void writeGrandTotalRow(Sheet sheet, Styles styles, int rowIndex, double grandTotal) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20f);
        setCell(row, 0, "", styles.grandTotalLabel);
        setCell(row, 1, "GRAND TOTAL", styles.grandTotalLabel);
        setCell(row, 2, "", styles.grandTotalLabel);
        setCell(row, 3, "", styles.grandTotalLabel);
        setCell(row, 4, "", styles.grandTotalLabel);
        setCell(row, 5, "", styles.grandTotalLabel);
        setCell(row, 6, grandTotal, styles.grandTotalCurrency);
        setCell(row, 7, "", styles.grandTotalLabel);
        setCell(row, 8, "", styles.grandTotalLabel);
        setCell(row, 9, "", styles.grandTotalLabel);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 5));
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 7, 9));
    }

    private static void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void setCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Every reusable cell style, built once per workbook. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle body;
        final CellStyle bodyCenter;
        final CellStyle bodyCurrency;
        final CellStyle bodyShaded;
        final CellStyle bodyShadedCenter;
        final CellStyle bodyShadedCurrency;
        final CellStyle totalCurrencyA;
        final CellStyle totalCurrencyB;
        final CellStyle grandTotalLabel;
        final CellStyle grandTotalCurrency;

        Styles(XSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();
            short currencyFormat = format.getFormat("₹#,##0.00");
            short pieceFormat = format.getFormat("#,##0");

            title = build(workbook, b -> b.bold().size(16));
            subtitle = build(workbook, b -> b.italic().size(11));

            header = build(workbook, b -> b.bold().white().fill(IndexedColors.TEAL).center().middle().border());

            body = build(workbook, b -> b.middle().border().fill(BAND_A));
            bodyCenter = build(workbook, b -> b.middle().center().border().fill(BAND_A));
            bodyCurrency = build(workbook, b -> b.middle().right().border().fill(BAND_A).format(currencyFormat));

            bodyShaded = build(workbook, b -> b.middle().border().fill(IndexedColors.GREY_25_PERCENT));
            bodyShadedCenter = build(workbook, b -> b.middle().center().border().fill(IndexedColors.GREY_25_PERCENT));
            bodyShadedCurrency = build(workbook, b -> b.middle().right().border().fill(IndexedColors.GREY_25_PERCENT).format(currencyFormat));

            totalCurrencyA = build(workbook, b -> b.bold().middle().right().border().fill(BAND_A).format(currencyFormat));
            totalCurrencyB = build(workbook, b -> b.bold().middle().right().border().fill(IndexedColors.GREY_25_PERCENT).format(currencyFormat));

            grandTotalLabel = build(workbook, b -> b.bold().size(12).middle().right().fill(IndexedColors.LIGHT_YELLOW).border());
            grandTotalCurrency = build(workbook, b -> b.bold().size(12).middle().right().fill(IndexedColors.LIGHT_YELLOW).border().format(currencyFormat));

            // Applied for real to the "pieces" column via the format param above, but pieces
            // itself is an integer count, not currency - give it its own plain number format.
            bodyCenter.setDataFormat(pieceFormat);
            bodyShadedCenter.setDataFormat(pieceFormat);
        }

        CellStyle totalCurrency(boolean shaded) {
            return shaded ? totalCurrencyB : totalCurrencyA;
        }

        private interface StyleFn {
            StyleBuilder apply(StyleBuilder b);
        }

        private CellStyle build(XSSFWorkbook workbook, StyleFn fn) {
            return fn.apply(new StyleBuilder(workbook)).style;
        }
    }

    /** Small fluent wrapper so each CellStyle above reads as one line instead of eight. The
     * font is attached to the style up front, so later font mutations (bold/size/color) still
     * take effect - POI stores a live reference to the Font object, not a snapshot. */
    private static final class StyleBuilder {
        private final CellStyle style;
        private final Font font;

        StyleBuilder(XSSFWorkbook workbook) {
            this.style = workbook.createCellStyle();
            this.font = workbook.createFont();
            this.style.setFont(font);
        }

        StyleBuilder bold() { font.setBold(true); return this; }
        StyleBuilder italic() { font.setItalic(true); return this; }
        StyleBuilder size(int points) { font.setFontHeightInPoints((short) points); return this; }
        StyleBuilder white() { font.setColor(IndexedColors.WHITE.getIndex()); return this; }
        StyleBuilder center() { style.setAlignment(HorizontalAlignment.CENTER); return this; }
        StyleBuilder right() { style.setAlignment(HorizontalAlignment.RIGHT); return this; }
        StyleBuilder middle() { style.setVerticalAlignment(VerticalAlignment.CENTER); return this; }
        StyleBuilder format(short dataFormat) { style.setDataFormat(dataFormat); return this; }

        StyleBuilder fill(IndexedColors color) {
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return this;
        }

        StyleBuilder fill(short colorIndex) {
            style.setFillForegroundColor(colorIndex);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return this;
        }

        StyleBuilder border() {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return this;
        }
    }
}
