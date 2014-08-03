package halo.query.mapping;

import halo.query.HaloQuerySpringBeanUtil;
import halo.query.annotation.Column;
import halo.query.annotation.Id;
import halo.query.annotation.Table;
import halo.query.dal.DALParser;
import halo.query.dal.DALParserUtil;
import halo.query.dal.ParsedInfo;
import halo.query.idtool.HaloMySQLMaxValueIncrementer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.incrementer.DB2SequenceMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.OracleSequenceMaxValueIncrementer;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表与实体类的映射信息类,此对象的所有操作请在同一个线程完成，本类的所有操作非线程安全
 *
 * @param <T>
 * @author akwei
 */
public class EntityTableInfo<T> {

    /**
     * 表映射的类型
     */
    private Class<T> clazz;

    private DALParser dalParser;

    private String tableName;

    /**
     * 表的别名
     */
    private String tableAlias;

    private DataFieldMaxValueIncrementer dataFieldMaxValueIncrementer;

    /**
     * 对应数据表中的所有字段
     */
    private final List<String> columnNames = new ArrayList<String>();

    /**
     * 表中的id字段
     */
    private String idColumnName;

    private String selectedFieldSQL;

    private Field idField;

    /**
     * 保存对应数据库字段的field集合
     */
    private final List<Field> tableFields = new ArrayList<Field>();

    private RowMapper<T> rowMapper;

    private SQLMapper<T> sqlMapper;

    public DALParser getDalParser() {
        return dalParser;
    }

    /**
     * 类的属性名与数据表字段的对应key为field,value为column
     */
    private final Map<String, String> fieldColumnMap = new HashMap<String, String>();

    private String db2Sequence;

    private String oracleSequence;

    private String mysqlSequence;

    private String mysqlSequenceColumnName;

    private String sequenceDsBeanId;

    private boolean hasSequence;

    private String columnNamePostfix;

    public EntityTableInfo(Class<T> clazz) {
        super();
        this.clazz = clazz;
        this.init();
    }

    private boolean isEmpty(String str) {
        if (str == null) {
            return true;
        }
        if (str != null && str.trim().length() == 0) {
            return true;
        }
        return false;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        this.tableAlias = tableAlias;
    }

    public void setSequenceDsBeanId(String sequenceDsBeanId) {
        if (this.isEmpty(sequenceDsBeanId)) {
            this.sequenceDsBeanId = null;
        }
        else {
            this.sequenceDsBeanId = sequenceDsBeanId;
        }
    }

    public String getSequenceDsBeanId() {
        return sequenceDsBeanId;
    }

    public void setMysqlSequenceColumnName(String mysqlSequenceColumnName) {
        if (this.isEmpty(mysqlSequenceColumnName)) {
            this.mysqlSequenceColumnName = null;
        }
        else {
            this.mysqlSequenceColumnName = mysqlSequenceColumnName;
        }
    }

    public String getMysqlSequenceColumnName() {
        return mysqlSequenceColumnName;
    }

    public DataFieldMaxValueIncrementer getDataFieldMaxValueIncrementer() {
        return dataFieldMaxValueIncrementer;
    }

    public void setMysqlSequence(String mysqlSequence) {
        if (this.isEmpty(mysqlSequence)) {
            this.mysqlSequence = null;
        }
        else {
            this.mysqlSequence = mysqlSequence;
        }
    }

    public String getMysqlSequence() {
        return mysqlSequence;
    }

    public void setHasSequence(boolean hasSequence) {
        this.hasSequence = hasSequence;
    }

    public boolean isHasSequence() {
        return hasSequence;
    }

    public String getDb2Sequence() {
        return db2Sequence;
    }

    public String getOracleSequence() {
        return oracleSequence;
    }

    public void setDb2Sequence(String db2Sequence) {
        if (this.isEmpty(db2Sequence)) {
            this.db2Sequence = null;
        }
        else {
            this.db2Sequence = db2Sequence;
        }
    }

    public void setOracleSequence(String oracleSequence) {
        if (this.isEmpty(oracleSequence)) {
            this.oracleSequence = null;
        }
        else {
            this.oracleSequence = oracleSequence;
        }
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public String getTableName() {
        return tableName;
    }

//    public String parseDAL() {
//        DALInfo dalInfo = DALStatus.getDalInfo(this.clazz);
//        if (dalInfo != null) {
//            DALStatus.setDsKey(dalInfo.getDsKey());
//            return dalInfo.getRealTable(this.clazz);
//        }
//        ParsedInfo parsedInfo = this.dalParser.parse(DALStatus.getParamMap());
//        if (parsedInfo != null) {
//            DALStatus.setDsKey(parsedInfo.getDsKey());
//            return parsedInfo.getRealTableName();
//        }
//        return null;
//    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public String getIdColumnName() {
        return idColumnName;
    }

    public String getSelectedFieldSQL() {
        return this.selectedFieldSQL;
    }

    /**
     * 获得delete by id方式的删除sql，支持增加表名后缀
     *
     * @return
     */
    public String getDeleteSQL() {
        return this.buildDeleteSQL();
    }

    /**
     * 获得insert标准sql,支持增加表名后缀
     *
     * @param hasIdColumn 生成的sql中是否含有id字段
     * @return
     */
    public String getInsertSQL(boolean hasIdColumn) {
        return this.buildInsertSQL(hasIdColumn);
    }

    /**
     * 获得update table set .... where id=? sql,支持增加表名后缀
     *
     * @return
     */
    public String getUpdateSQL() {
        return this.buildUpdateSQL();
    }

    /**
     * 获得作为id的field
     *
     * @return
     */
    public Field getIdField() {
        return idField;
    }

    /**
     * 获得所有与数据库对应的field
     *
     * @return
     */
    public List<Field> getTableFields() {
        return tableFields;
    }

    /**
     * 获得spring RowMapper对象
     *
     * @return
     */
    public RowMapper<T> getRowMapper() {
        return rowMapper;
    }

    /**
     * 获得 SQLMapper对象
     *
     * @return
     */
    public SQLMapper<T> getSqlMapper() {
        return sqlMapper;
    }

    /**
     * 是否是id的field
     *
     * @param field
     * @return
     */
    public boolean isIdField(Field field) {
        if (this.idField.equals(field)) {
            return true;
        }
        return false;
    }

    private void init() {
        this.buildTable();
        this.buildFields();
        this.buildIdColumn();
        this.buildSelectedFieldSQL();
        this.createRowMapper();
        this.createSQLMapper();
        if (idField == null) {
            throw new RuntimeException("no id field for "
                    + this.clazz.getName());
        }
    }

    /**
     * 创建select的字段sql片段
     */
    private void buildSelectedFieldSQL() {
        StringBuilder sb = new StringBuilder();
        for (String col : columnNames) {
            sb.append(this.tableAlias);
            sb.append(".");
            sb.append(col);
            sb.append(" as ");
            sb.append(this.getColumnAlias(col));
            sb.append(",");
        }
        if (!columnNames.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        this.selectedFieldSQL = sb.toString();
    }

    private String buildInsertSQL(boolean hasIdColumn) {
        StringBuilder sb = new StringBuilder("insert into ");
        ParsedInfo parsedInfo = DALParserUtil.process(this.clazz,
                this.getDalParser());
        if (parsedInfo == null || parsedInfo.getRealTableName() == null) {
            sb.append(this.tableName);
        }
        else {
            sb.append(parsedInfo.getRealTableName());
        }
        sb.append("(");
        for (String col : columnNames) {
            if (!hasIdColumn && col.equals(this.idColumnName)) {
                continue;
            }
            sb.append(col);
            sb.append(",");
        }
        if (!columnNames.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(")");
        sb.append(" values");
        sb.append("(");
        int len = columnNames.size();
        if (!hasIdColumn) {
            len = len - 1;
        }
        for (int i = 0; i < len; i++) {
            sb.append("?,");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    private String buildDeleteSQL() {
        ParsedInfo parsedInfo = DALParserUtil.process(clazz,
                this.getDalParser());
        StringBuilder sb = new StringBuilder("delete from ");
        if (parsedInfo == null || parsedInfo.getRealTableName() == null) {
            sb.append(this.tableName);
        }
        else {
            sb.append(parsedInfo.getRealTableName());
        }
        sb.append(" where ");
        sb.append(this.idColumnName);
        sb.append("=?");
        return sb.toString();
    }

    private String buildUpdateSQL() {
        ParsedInfo parsedInfo = DALParserUtil.process(clazz,
                this.getDalParser());
        StringBuilder sb = new StringBuilder("update ");
        if (parsedInfo == null || parsedInfo.getRealTableName() == null) {
            sb.append(this.tableName);
        }
        else {
            sb.append(parsedInfo.getRealTableName());
        }
        sb.append(" set ");
        for (String col : columnNames) {
            if (col.equals(idColumnName)) {
                continue;
            }
            sb.append(col);
            sb.append("=?,");
        }
        if (!columnNames.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(" where ");
        sb.append(this.idColumnName);
        sb.append("=?");
        return sb.toString();
    }

    /**
     * 初始化表信息
     */
    private void buildTable() {
        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new RuntimeException("tableName not set [ " + clazz.getName()
                    + " ]");
        }
        this.tableName = table.name();
        if (this.tableName == null || this.tableName.trim().length() == 0) {
            throw new RuntimeException("tableName not set [ " + clazz.getName()
                    + " ]");
        }
        this.tableAlias = this.tableName.replaceAll("\\.", "_") + "_";
        try {
            this.dalParser = (DALParser) (table.dalParser().getConstructor()
                    .newInstance());
        }
        catch (Exception e) {
            throw new RuntimeException("DALParser init error", e);
        }
        this.columnNamePostfix = "";
        // 对sequence赋值
        this.setSequenceDsBeanId(table.sequence_ds_bean_id());
        this.setMysqlSequence(table.mysql_sequence());
        this.setMysqlSequenceColumnName(table.mysql_sequence_column_name());
        this.setDb2Sequence(table.db2_sequence());
        this.setOracleSequence(table.oracle_sequence());
        // 判断当前是否有sequence信息
        if (this.db2Sequence != null || this.oracleSequence != null
                || this.mysqlSequence != null) {
            this.hasSequence = true;
            if (this.sequenceDsBeanId == null) {
                throw new RuntimeException("sequenceDsBeanId must be not null");
            }
        }
        else {
            this.hasSequence = false;
        }
        if (this.mysqlSequence != null) {
            if (this.sequenceDsBeanId == null) {
                throw new RuntimeException("sequenceDsBeanId must be not null");
            }
            DataSource ds = (DataSource) HaloQuerySpringBeanUtil.instance()
                    .getBean(
                            this.sequenceDsBeanId);
            // mysql使用一张表来作为id自增
            HaloMySQLMaxValueIncrementer incrementer = new HaloMySQLMaxValueIncrementer(
                    ds, this.mysqlSequence, this.mysqlSequenceColumnName);
            this.dataFieldMaxValueIncrementer = incrementer;
        }
        if (this.db2Sequence != null) {
            DataSource ds = (DataSource) HaloQuerySpringBeanUtil.instance()
                    .getBean(
                            this.sequenceDsBeanId);
            DB2SequenceMaxValueIncrementer incrementer = new DB2SequenceMaxValueIncrementer(
                    ds, this.db2Sequence);
            this.dataFieldMaxValueIncrementer = incrementer;
        }
        if (this.oracleSequence != null) {
            DataSource ds = (DataSource) HaloQuerySpringBeanUtil.instance()
                    .getBean(
                            this.sequenceDsBeanId);
            OracleSequenceMaxValueIncrementer incrementer = new OracleSequenceMaxValueIncrementer(
                    ds, this.oracleSequence);
            this.dataFieldMaxValueIncrementer = incrementer;
        }
    }

    private void buildFields() {
        this.buildFieldsForClass(clazz);
    }

    /**
     * 检测类和父类的所有字段，获得表对应的field,以及有逻辑外键引用的field
     *
     * @param clazz
     */
    private void buildFieldsForClass(Class<?> clazz) {
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            this.buildFieldsForClass(superClazz);
        }
        Field[] fs = clazz.getDeclaredFields();
        Column column;
        for (Field f : fs) {
            f.setAccessible(true);
            column = f.getAnnotation(Column.class);
            // 如果有Column annotation，field就是与数据表对应的字段
            if (column != null) {
                tableFields.add(f);
                if (column.value().trim().length() == 0) {
                    fieldColumnMap.put(f.getName(), f.getName());
                    columnNames.add(f.getName());
                }
                else {
                    fieldColumnMap.put(f.getName(), column.value().trim());
                    columnNames.add(column.value().trim());
                }
            }
        }
    }

    /**
     * 检测表的主键field
     */
    private void buildIdColumn() {
        Field[] fs = clazz.getDeclaredFields();
        Id id;
        for (Field f : fs) {
            id = f.getAnnotation(Id.class);
            if (id == null) {
                continue;
            }
            f.setAccessible(true);
            this.idField = f;
            Column column = f.getAnnotation(Column.class);
            if (column == null) {
                throw new RuntimeException(
                        "must has @Column annotation on field "
                                + clazz.getName() + "." + f.getName());
            }
            String value = column.value();
            if (value == null || value.trim().length() == 0) {
                idColumnName = f.getName();
            }
            else {
                idColumnName = column.value().trim();
            }
            break;
        }
    }

    /**
     * 获得数据库对应的列名称
     *
     * @param fieldName java对象的字段名称
     * @return
     */
    public String getColumn(String fieldName) {
        return fieldColumnMap.get(fieldName);
    }

    /**
     * 获得列名称的别名，表示为table_column
     *
     * @param fieldName
     * @return
     */
    public String getColumnAliasByFieldName(String fieldName) {
        return this.tableAlias + this.getColumn(fieldName)
                + this.columnNamePostfix;
    }

    public String getColumnAlias(String columnName) {
        return this.tableAlias + columnName + this.columnNamePostfix;
    }

    public String getColumnFullNameByFieldName(String fieldName) {
        return this.tableAlias + "." + this.getColumn(fieldName);
    }

    @SuppressWarnings("unchecked")
    private void createSQLMapper() {
        if (this.getTableFields().isEmpty()) {
            return;
        }
        if (this.getIdField() == null) {
            return;
        }
        if (this.getIdField() != null && this.getTableFields().size() == 1) {
            return;
        }
        JavassitSQLMapperClassCreater creater = new JavassitSQLMapperClassCreater(
                this);
        Class<SQLMapper<T>> mapperClass = (Class<SQLMapper<T>>) creater
                .getMapperClass();
        try {
            this.sqlMapper = mapperClass.getConstructor().newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void createRowMapper() {
        JavassitRowMapperClassCreater creater = new JavassitRowMapperClassCreater(
                this);
        Class<RowMapper<T>> mapperClass = (Class<RowMapper<T>>) creater
                .getMapperClass();
        try {
            this.rowMapper = mapperClass.getConstructor().newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}