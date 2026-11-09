package com.laimengchao.sso.common.excel;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Excel 写出底座：表头样式、冻结首行、列宽、Workbook 转字节。
 * <p>只放与业务无关的通用能力，具体列映射由各业务模块自行组装。
 */
public final class ExcelWriter {

    private ExcelWriter() {
    }

    /** 默认列宽（字符数） */
    private static final int DEFAULT_WIDTH = 16;

    public static Sheet headerSheet(Workbook wb, String name, String[] headers) {
        return headerSheet(wb, name, headers, DEFAULT_WIDTH);
    }

    /** 建 sheet、写带底色加粗的表头、冻结首行，并按统一宽度设置列宽 */
    public static Sheet headerSheet(Workbook wb, String name, String[] headers, int widthChars) {
        Sheet sheet = wb.createSheet(name);
        Row row = sheet.createRow(0);
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, widthChars * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    public static byte[] toBytes(Workbook wb, String failMessage) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, failMessage);
        }
    }

    public static void set(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    public static void set(Row row, int index, BigDecimal value) {
        row.createCell(index).setCellValue(value == null ? "" : value.stripTrailingZeros().toPlainString());
    }
}
