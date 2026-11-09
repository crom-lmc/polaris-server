package com.laimengchao.sso.common.excel;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 读取底座：表头按名称定位列、单元格取值、多格式日期解析、空行判断。
 * <p>表头比较时忽略空白与星号（兼容模板中的"姓名 *"这类必填标记）。
 */
public final class ExcelReader {

    private ExcelReader() {
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日")
    };

    /** 表头归一化：去掉空白、全/半角星号 */
    public static String key(String value) {
        return value == null ? "" : value.replaceAll("[\\s*＊]", "");
    }

    /** 第一行表头 → 列号；重复列名取第一次出现的列 */
    public static Map<String, Integer> headers(Row row) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (row == null) {
            return map;
        }
        DataFormatter fmt = new DataFormatter();
        for (Cell c : row) {
            String k = key(fmt.formatCellValue(c));
            if (!k.isEmpty()) {
                map.putIfAbsent(k, c.getColumnIndex());
            }
        }
        return map;
    }

    /** 必填列校验，缺列时抛出统一提示 */
    public static void requireHeaders(Map<String, Integer> headers, String sheet, String... required) {
        for (String r : required) {
            if (!headers.containsKey(key(r))) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "「" + sheet + "」缺少「" + r + "」列，请使用系统模板");
            }
        }
    }

    public static String text(Row row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(key(header));
        if (index == null) {
            return "";
        }
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : cellText(cell);
    }

    public static String cellText(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "是" : "否";
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.FORMULA) {
            return new DataFormatter().formatCellValue(cell).trim();
        }
        return "";
    }

    /**
     * 读取日期列：支持 Excel 日期单元格、数值日期序列与常见文本格式；
     * 无法识别时写入 errors 并返回 null，由业务方决定是否中断。
     */
    public static LocalDate date(Row row, Map<String, Integer> headers, String header, List<String> errors) {
        Integer index = headers.get(key(header));
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double v = cell.getNumericCellValue();
            if (v > 1000 && v < 100000) {
                return DateUtil.getLocalDateTime(v).toLocalDate();
            }
        }
        String raw = cellText(cell);
        if (raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, f);
            } catch (Exception ignored) {
                // 继续尝试下一个格式
            }
        }
        errors.add(header + "格式不正确（应为 yyyy-MM-dd）：" + raw);
        return null;
    }

    /** 表头覆盖的所有列都为空时视为空行，跳过 */
    public static boolean blankRow(Row row, Map<String, Integer> headers) {
        for (Integer index : headers.values()) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !cellText(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
