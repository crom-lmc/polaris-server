package com.laimengchao.sso.common.entity;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 「业务实体」元数据 + 取数的<b>通用</b>底座——审批页面「与表真绑定」能力的服务端实现，一份代码全模块复用。
 * <p>
 * <b>为什么在 common</b>：早期只有 EHR 提供了这组接口，审批配置里的「数据绑定」也就只能绑 EHR 的表。
 * 要支持其它模块（cms / blog / settings / sso-center），每个模块都要能用<b>自己库</b>的
 * information_schema 自描述表结构，逻辑完全一致、只是连接的库不同。所以把实现上移成抽象基类，
 * 各服务只留一个几行的子类（标 {@code @RestController}），由各自的 {@code /internal/**} 准入策略
 * （X-Internal-Token 拦截器）把关——准入仍留在各服务，便于逐个审阅。
 * <p>
 * 之所以能「一份代码服务所有库」：不耦合任何具体实体，表名/列名全靠 information_schema 反射；
 * 谁连的 DataSource 是哪个库，接口返回的就是哪个库的表。
 * <p>
 * <b>安全</b>：表名 / 列名先过白名单（合法标识符 + 真实存在于 information_schema）才允许拼进 SQL；
 * 取值参数一律走 {@code ?} 占位符，杜绝注入。
 * <p>
 * <b>约定</b>：子类必须标 {@code @RestController}，并用
 * {@code @RequestMapping("/internal/entity")} 声明路径（或直接继承本类的类级映射）。
 */
public abstract class EntityMetaController {

    protected final JdbcTemplate jdbcTemplate;

    /** 合法 SQL 标识符：字母/下划线开头，仅含字母数字下划线。SQL 注入第一道闸。 */
    private static final Pattern IDENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    protected EntityMetaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 列出当前服务所连库下全部业务表（含表注释）——前端「绑定数据表」清单的数据源。
     * <p>
     * 不按前缀过滤：服务库里所有 BASE TABLE 都是可绑定对象，由前端按业务语义挑选。
     */
    @GetMapping("/tables")
    public R<List<TableMeta>> tables() {
        String schema = currentSchema();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT table_name, table_comment FROM information_schema.tables " +
                        "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name",
                schema);
        // 按表名去重后保持字典序：information_schema 理论上不会同库同名两行，
        // 但历史上出现过「前端拿到重复表清单」的问题，服务端这里再兜一层，
        // 让任何消费方（前端勾选、字段池）都不可能看到同名表两行。
        Map<String, TableMeta> uniq = new LinkedHashMap<>(rows.size());
        for (Map<String, Object> r : rows) {
            String name = str(r.get("TABLE_NAME"));
            if (name.isEmpty() || uniq.containsKey(name)) {
                continue;
            }
            uniq.put(name, new TableMeta(name, str(r.get("TABLE_COMMENT"))));
        }
        return R.ok(new ArrayList<>(uniq.values()));
    }

    /**
     * 某表的列元数据：字段名、类型、注释、是否可空、是否主键。
     * 前端据此把各表列渲染成「数据表字段」候选，进入字段池。
     */
    @GetMapping("/tables/{table}/columns")
    public R<List<ColumnMeta>> columns(@PathVariable String table) {
        TableInfo info = resolveTable(table);
        return R.ok(new ArrayList<>(info.columns));
    }

    /**
     * 按主键取一行（如 /data/ehr_employee/2116193763304861697）。找不到返回 null——审批单绑定的
     * 主体可能被删，调用方（settings）按「取不到」降级而不是崩。
     */
    @GetMapping("/data/{table}/{id}")
    public R<Map<String, Object>> byId(@PathVariable String table, @PathVariable String id) {
        TableInfo info = resolveTable(table);
        String pk = info.pkColumns.isEmpty() ? "id" : info.pkColumns.get(0);
        String sql = "SELECT * FROM " + quote(table) + " WHERE " + quote(pk) + " = ?" + softDelete(info);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, toValue(id));
        return R.ok(rows.isEmpty() ? null : stringifyIds(rows.get(0)));
    }

    /**
     * 按外键列取多行（如 /data/ehr_emp_bank?fk=employee_id&fkValue=123）。
     * 子表都靠外键反查，审批详情页要把它们一并带出。fk 必须是该表真实存在的列，否则 400。
     */
    @GetMapping("/data/{table}")
    public R<List<Map<String, Object>>> byFk(@PathVariable String table,
                                             @RequestParam String fk,
                                             @RequestParam String fkValue) {
        TableInfo info = resolveTable(table);
        if (!info.hasColumn(fk)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "列不存在：" + fk);
        }
        // FK_LIST 必须稳定排序：MySQL 无 ORDER BY 时返回顺序不确定，
        // 前端「取首行降级」会时而是这张历史单、时而是那张。统一按主键升序，
        // 让档案子表的展示顺序可预期（新插入的行固定排在后面）。
        String pk = info.pkColumns.isEmpty() ? null : info.pkColumns.get(0);
        String order = pk == null ? "" : " ORDER BY " + quote(pk) + " ASC";
        String sql = "SELECT * FROM " + quote(table) + " WHERE " + quote(fk)
                + " = ?" + softDelete(info) + order;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, toValue(fkValue));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(stringifyIds(r));
        }
        return R.ok(out);
    }

    // ---------------------------------------------------------------- 内部工具

    /** 当前连接的默认库名。SQL 里限定 schema，避免误读同名表。 */
    private String currentSchema() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    /**
     * 校验表名合法性 + 存在性，并一次性取回列元数据（主键、是否含 deleted）。
     * 每次都查 information_schema——这是配置期接口，不是热路径，不缓存反而能即时反映建表/迁库。
     */
    private TableInfo resolveTable(String table) {
        if (table == null || !IDENT.matcher(table).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法表名：" + table);
        }
        String schema = currentSchema();
        List<Map<String, Object>> detail = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, column_comment, is_nullable, column_key " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                schema, table);
        if (detail.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "表不存在：" + table);
        }
        List<ColumnMeta> metas = new ArrayList<>(detail.size());
        List<String> pks = new ArrayList<>();
        Set<String> names = new HashSet<>(detail.size());
        boolean hasDeleted = false;
        for (Map<String, Object> d : detail) {
            String name = str(d.get("COLUMN_NAME"));
            boolean pk = "PRI".equalsIgnoreCase(str(d.get("COLUMN_KEY")));
            names.add(name);
            if (pk) {
                pks.add(name);
            }
            if ("deleted".equalsIgnoreCase(name)) {
                hasDeleted = true;
            }
            metas.add(new ColumnMeta(name, str(d.get("DATA_TYPE")), str(d.get("COLUMN_COMMENT")),
                    !"NO".equalsIgnoreCase(str(d.get("IS_NULLABLE"))), pk));
        }
        return new TableInfo(pks, metas, names, hasDeleted);
    }

    /** 软删除过滤：表上有 {@code deleted} 列（各服务 BaseEntity 的 @TableLogic）时只取未删除行。 */
    private String softDelete(TableInfo info) {
        return info.hasDeleted ? " AND deleted = 0" : "";
    }

    /** 反引号包裹标识符（已白名单校验，这层是双保险）。 */
    private static String quote(String ident) {
        return "`" + ident + "`";
    }

    /**
     * 取值参数归一：纯数字当 Long（雪花 ID 19 位超 int），其余当 String。
     * MySQL 会把字符串参数隐式转成目标列类型，所以传 Long/String 都能正确命中索引。
     */
    private static Object toValue(String s) {
        if (s != null && s.matches("-?\\d+")) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        return s;
    }

    /**
     * 雪花主键 / 外键（Long）转字符串下发——19 位数字超过 JS Number.MAX_SAFE_INTEGER，
     * 原样下发会在前端丢精度（实测 2116193763304861697 变 ...700，再回查就是 404）。
     * 业务数字字段（Integer，如 status/gender）不受影响。
     */
    private static Map<String, Object> stringifyIds(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row.size());
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Long || v instanceof BigInteger) {
                out.put(e.getKey(), v.toString());
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    // ---------------------------------------------------------------- 内部类型

    /** 表元数据（列表用）：表名 + 表注释 */
    public record TableMeta(String table, String comment) {
    }

    /** 列元数据：字段名、类型、注释、是否可空、是否主键 */
    public record ColumnMeta(String name, String type, String comment, boolean nullable, boolean primaryKey) {
    }

    /** 运行期解析出的表信息（主键列、列集合、是否含 deleted） */
    private static final class TableInfo {
        final List<String> pkColumns;
        final List<ColumnMeta> columns;
        final Set<String> columnNames;
        final boolean hasDeleted;

        TableInfo(List<String> pkColumns, List<ColumnMeta> columns, Set<String> columnNames, boolean hasDeleted) {
            this.pkColumns = pkColumns;
            this.columns = columns;
            this.columnNames = columnNames;
            this.hasDeleted = hasDeleted;
        }

        boolean hasColumn(String name) {
            return columnNames.contains(name);
        }
    }
}
