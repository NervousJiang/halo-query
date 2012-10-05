package halo.query;

import halo.query.mapping.DefEntityTableInfoFactory;
import halo.query.mapping.EntityTableInfo;
import halo.query.mapping.EntityTableInfoFactory;
import halo.query.mapping.SQLMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.RowMapper;

public class Query {

	/**
	 * 进行多表查询时使用的RowMapper,只能用在inner join的sql中
	 * 
	 * @author akwei
	 * @param <T>
	 */
	static class MultiTableRowMapper<T> implements RowMapper<T> {

		private Query query;

		private Class<?>[] clazzes;

		@SuppressWarnings("unchecked")
		public T mapRow(ResultSet rs, int rowNum) throws SQLException {
			Map<String, Object> map = new HashMap<String, Object>(2);
			RowMapper<?> rowMaper = null;
			Object o = null;
			for (Class<?> clazz : clazzes) {
				rowMaper = query.getRowMapper(clazz);
				o = rowMaper.mapRow(rs, rowNum);
				map.put(o.getClass().getName(), o);
			}
			Set<Entry<String, Object>> set = map.entrySet();
			for (Entry<String, Object> e : set) {
				EntityTableInfo<?> info = query
						.getEntityTableInfo(e.getValue().getClass());
				for (Entry<String, Object> entry : set) {
					if (entry.equals(e)) {
						continue;
					}
					if (info.hasRefByClass(entry.getValue().getClass())) {
						info.setRefObjectValue(e.getValue(), entry.getValue());
					}
				}
			}
			return (T) (map.get(clazzes[0].getName()));
		}
	}

	private JdbcSupport jdbcSupport;

	private EntityTableInfoFactory entityTableInfoFactory = new DefEntityTableInfoFactory();

	/**
	 * select count(*) 查询。查询中的表别名必须与表名相同
	 * 
	 * @param clazzes
	 *            查询对象类型数组
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int count(Class<?>[] clazzes, String sqlAfterTable,
			Object[] values) throws QueryException {
		return this.count(clazzes, null, sqlAfterTable, values);
	}

	/**
	 * select count(*) 查询。查询中的表别名必须与表名相同
	 * 
	 * @param clazzes
	 *            查询对象类型数组
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int count(Class<?>[] clazzes, String[] tablePostfix,
			String sqlAfterTable, Object[] values) throws QueryException {
		StringBuilder sb = new StringBuilder("select count(*) from ");
		int i = 0;
		for (Class<?> clazz : clazzes) {
			EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
			sb.append(info.getTableName());
			if (tablePostfix != null && this.isNotEmpty(tablePostfix[i])) {
				sb.append(tablePostfix[i]);
			}
			sb.append(" as ");
			sb.append(info.getTableName());
			sb.append(",");
			i++;
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(" ");
		sb.append(sqlAfterTable);
		return jdbcSupport.num(sb.toString(), values).intValue();
	}

	/**
	 * select count(*) 查询
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int count(Class<T> clazz, String sqlAfterTable, Object[] values)
			throws QueryException {
		return this.count(clazz, null, sqlAfterTable, values);
	}

	/**
	 * select count(*) 查询
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int count(Class<T> clazz, String tablePostfix,
			String sqlAfterTable, Object[] values) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("select count(*) from ");
		sql.append(info.getTableName());
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" ");
		if (sqlAfterTable != null) {
			sql.append(sqlAfterTable);
		}
		return jdbcSupport.num(sql.toString(), values).intValue();
	}

	public <T> List<T> db2List(Class<?>[] clazzes,
			String where, String orderBy,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		return this.db2List(clazzes, null, where,
				orderBy, begin, size, values, rowMapper);
	}

	public <T> List<T> db2List(Class<?>[] clazzes, String[] tablePostfix,
			String where, String orderBy,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		EntityTableInfo<T> info = null;
		StringBuilder sql = new StringBuilder();
		sql.append("select * from ( select ");
		for (Class<?> clazz : clazzes) {
			info = this.getEntityTableInfo(clazz);
			sql.append(info.getSelectedFieldSQL());
			sql.append(",");
		}
		sql.deleteCharAt(sql.length() - 1);
		int i = 0;
		sql.append(" ,rownumber() over (");
		sql.append(orderBy);
		sql.append(") as rowid from ");
		for (Class<?> clazz : clazzes) {
			info = this.getEntityTableInfo(clazz);
			sql.append(info.getTableName());
			if (tablePostfix != null && this.isNotEmpty(tablePostfix[i])) {
				sql.append(tablePostfix[i]);
			}
			sql.append(" as ");
			sql.append(info.getTableName());
			sql.append(",");
			i++;
		}
		sql.deleteCharAt(sql.length() - 1);
		sql.append(" ");
		sql.append(where);
		sql.append(") temp where temp.rowid >= ");
		sql.append(begin);
		sql.append(" and temp.rowid <= ");
		sql.append(begin + size);
		return jdbcSupport.list(sql.toString(), values, rowMapper);
	}

	public <T> List<T> db2List(Class<T> clazz,
			String where, String orderBy,
			int begin, int size, Object[] values)
			throws QueryException {
		return this.db2List(clazz, null, where, orderBy, begin, size, values,
				this.getRowMapper(clazz));
	}

	public <T> List<T> db2List(Class<T> clazz,
			String where, String orderBy,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		return this.db2List(clazz, null, where, orderBy, begin, size, values,
				rowMapper);
	}

	public <T> List<T> db2List(Class<T> clazz, String tablePostfix,
			String where, String orderBy,
			int begin, int size, Object[] values)
			throws QueryException {
		return this.db2List(clazz, tablePostfix, where, orderBy, begin, size,
				values, this.getRowMapper(clazz));
	}

	public <T> List<T> db2List(Class<T> clazz, String tablePostfix,
			String where, String orderBy,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("select * from ( select ");
		sql.append(info.getSelectedFieldSQL());
		sql.append(" ,rownumber() over (");
		sql.append(orderBy);
		sql.append(") as rowid from ");
		String tableName = info.getTableName();
		sql.append(tableName);
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" as ");
		sql.append(tableName);
		sql.append(" ");
		sql.append(where);
		sql.append(") temp where temp.rowid >= ");
		sql.append(begin);
		sql.append(" and temp.rowid <= ");
		sql.append(begin + size);
		return jdbcSupport.list(sql.toString(), values, rowMapper);
	}

	public <T> List<T> db2ListMulti(Class<?>[] clazzes,
			String where, String orderBy,
			int begin, int size, Object[] values)
			throws QueryException {
		MultiTableRowMapper<T> mapper = new MultiTableRowMapper<T>();
		mapper.query = this;
		mapper.clazzes = clazzes;
		return this.db2List(clazzes, null, where, orderBy, begin, size,
				values, mapper);
	}

	public <T> List<T> db2ListMulti(Class<?>[] clazzes, String[] tablePostfix,
			String where, String orderBy,
			int begin, int size, Object[] values)
			throws QueryException {
		MultiTableRowMapper<T> mapper = new MultiTableRowMapper<T>();
		mapper.query = this;
		mapper.clazzes = clazzes;
		return this.db2List(clazzes, tablePostfix, where, orderBy, begin, size,
				values, mapper);
	}

	/**
	 * delete sql.根据条件删除.例如: delete table where field0=? and ....
	 * 
	 * @param clazz
	 *            要删除的对象类型
	 * @param sqlAfterTable
	 *            delete table 之后的语句,例如:delete table where
	 *            field0=?,sqlAfterTable为where field0=?
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int delete(Class<T> clazz, String sqlAfterTable, Object[] values)
			throws QueryException {
		return this.delete(clazz, null, sqlAfterTable, values);
	}

	/**
	 * delete sql.根据条件删除.例如: delete table where field0=? and ....
	 * 
	 * @param clazz
	 *            要删除的对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            delete table 之后的语句,例如:delete table where
	 *            field0=?,sqlAfterTable为where field0=?
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int delete(Class<T> clazz, String tablePostfix,
			String sqlAfterTable, Object[] values) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("delete from ");
		sql.append(info.getTableName());
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" ");
		if (sqlAfterTable != null) {
			sql.append(sqlAfterTable);
		}
		return this.jdbcSupport.update(sql.toString(), values);
	}

	/**
	 * delete sql,返回删除的记录数量
	 * 
	 * @param t
	 *            要删除的对象，必须有id
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int delete(T t) throws QueryException {
		return this.delete(t, null);
	}

	/**
	 * delete sql,返回删除的记录数量
	 * 
	 * @param t
	 *            要删除的对象，必须有id
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int delete(T t, String tablePostfix) throws QueryException {
		SQLMapper<T> mapper = this.getSqlMapper(t.getClass());
		return this
				.deleteById(t.getClass(), tablePostfix, mapper.getIdParam(t));
	}

	/**
	 * delete sql,根据id删除。返回删除的记录数量
	 * 
	 * @param clazz
	 *            要删除的对象的类型
	 * @param idValue
	 *            id的参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int deleteById(Class<T> clazz, Object idValue)
			throws QueryException {
		return this.deleteById(clazz, null, idValue);
	}

	/**
	 * delete sql,根据id删除。返回删除的记录数量
	 * 
	 * @param clazz
	 *            要删除的对象的类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param idValue
	 *            id的参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int deleteById(Class<T> clazz, String tablePostfix,
			Object idValue) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		return this.jdbcSupport.update(info.getDeleteSQL(tablePostfix),
				new Object[] { idValue });
	}

	@SuppressWarnings("unchecked")
	public <T> EntityTableInfo<T> getEntityTableInfo(Class<?> clazz) {
		return (EntityTableInfo<T>) this.entityTableInfoFactory
				.getEntityTableInfo(clazz);
	}

	public JdbcSupport getJdbcSupport() {
		return jdbcSupport;
	}

	@SuppressWarnings("unchecked")
	public <T> RowMapper<T> getRowMapper(Class<T> clazz) {
		return (RowMapper<T>) this.getEntityTableInfo(clazz).getRowMapper();
	}

	@SuppressWarnings("unchecked")
	public <T> SQLMapper<T> getSqlMapper(Class<?> clazz) {
		return (SQLMapper<T>) this.getEntityTableInfo(clazz).getSqlMapper();
	}

	/**
	 * insert sql
	 * 
	 * @param t
	 *            insert的对象
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> void insert(T t) throws QueryException {
		this.insert(t, null);
	}

	/**
	 * insert sql
	 * 
	 * @param t
	 *            insert的对象
	 * @param tablePostfix
	 *            表名称后缀，可与原表名组成新的表名
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> void insert(T t, String tablePostfix) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(t.getClass());
		SQLMapper<T> mapper = this.getSqlMapper(t.getClass());
		this.jdbcSupport.insert(info.getInsertSQL(tablePostfix),
				mapper.getParamsForInsert(t));
	}

	/**
	 * insert sql,返回自增数字id
	 * 
	 * @param t
	 *            insert的对象
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> Number insertForNumber(T t) throws QueryException {
		return this.insertForNumber(t, null);
	}

	/**
	 * insert sql,返回自增数字id
	 * 
	 * @param t
	 *            insert的对象
	 * @param tablePostfix
	 *            表名称后缀，可与原表名组成新的表名
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> Number insertForNumber(T t, String tablePostfix)
			throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(t.getClass());
		SQLMapper<T> mapper = this.getSqlMapper(t.getClass());
		Object obj = this.jdbcSupport.insert(info.getInsertSQL(tablePostfix),
				mapper.getParamsForInsert(t));
		return (Number) obj;
	}

	protected boolean isNotEmpty(String tablePostfix) {
		if (tablePostfix != null && tablePostfix.trim().length() > 0) {
			return true;
		}
		return false;
	}

	/**
	 * mysql的分页查询。查询中的表别名必须与表名相同
	 * 
	 * @param clazzes
	 *            查询对象类型数组
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<?>[] clazzes, String sqlAfterTable,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		return this.mysqlList(clazzes, null, sqlAfterTable, begin, size,
				values, rowMapper);
	}

	/**
	 * mysql的分页查询。查询中的表别名必须与表名相同
	 * 
	 * @param clazzes
	 *            查询对象类型数组
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<?>[] clazzes, String[] tablePostfix,
			String sqlAfterTable, int begin, int size, Object[] values,
			RowMapper<T> rowMapper) throws QueryException {
		StringBuilder sb = new StringBuilder("select ");
		EntityTableInfo<T> info;
		int i = 0;
		for (Class<?> clazz : clazzes) {
			info = this.getEntityTableInfo(clazz);
			sb.append(info.getSelectedFieldSQL());
			sb.append(",");
			i++;
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(" from ");
		i = 0;
		for (Class<?> clazz : clazzes) {
			info = this.getEntityTableInfo(clazz);
			sb.append(info.getTableName());
			if (tablePostfix != null && this.isNotEmpty(tablePostfix[i])) {
				sb.append(tablePostfix[i]);
			}
			sb.append(" as ");
			sb.append(info.getTableName());
			sb.append(",");
			i++;
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(" ");
		sb.append(sqlAfterTable);
		if (size > 0) {
			sb.append(" limit ");
			sb.append(begin);
			sb.append(",");
			sb.append(size);
		}
		return jdbcSupport.list(sb.toString(), values, rowMapper);
	}

	/**
	 * mysql的分页查询。
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<T> clazz, String sqlAfterTable,
			int begin, int size, Object[] values) throws QueryException {
		return this.mysqlList(clazz, null, sqlAfterTable, begin, size, values);
	}

	/**
	 * mysql的分页查询。
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<T> clazz, String sqlAfterTable,
			int begin, int size, Object[] values, RowMapper<T> rowMapper)
			throws QueryException {
		return this.mysqlList(clazz, null, sqlAfterTable, begin, size, values,
				rowMapper);
	}

	/**
	 * mysql的分页查询。
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<T> clazz, String tablePostfix,
			String sqlAfterTable, int begin, int size, Object[] values)
			throws QueryException {
		return this.mysqlList(clazz, tablePostfix, sqlAfterTable, begin, size,
				values, this.getRowMapper(clazz));
	}

	/**
	 * mysql的分页查询。
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name
	 * @param begin
	 * @param size
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> List<T> mysqlList(Class<T> clazz, String tablePostfix,
			String sqlAfterTable, int begin, int size, Object[] values,
			RowMapper<T> rowMapper) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("select ");
		sql.append(info.getSelectedFieldSQL());
		sql.append(" from ");
		String tableName = info.getTableName();
		sql.append(tableName);
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" as ");
		sql.append(tableName);
		sql.append(" ");
		if (sqlAfterTable != null) {
			sql.append(sqlAfterTable);
		}
		if (size > 0) {
			sql.append(" limit ");
			sql.append(begin);
			sql.append(",");
			sql.append(size);
		}
		return jdbcSupport.list(sql.toString(), values, rowMapper);
	}

	public <T> List<T> mysqlListMulti(Class<?>[] clazzes,
			String sqlAfterTable, int begin, int size, Object[] values)
			throws QueryException {
		return this.mysqlListMulti(clazzes, null, sqlAfterTable, begin, size,
				values);
	}

	public <T> List<T> mysqlListMulti(Class<?>[] clazzes,
			String[] tablePostfix,
			String sqlAfterTable, int begin, int size, Object[] values)
			throws QueryException {
		MultiTableRowMapper<T> mapper = new MultiTableRowMapper<T>();
		mapper.query = this;
		mapper.clazzes = clazzes;
		return this.mysqlList(clazzes, tablePostfix, sqlAfterTable, begin,
				size, values,
				mapper);
	}

	/**
	 * select sql 返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name desc
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T obj(Class<T> clazz, String sqlAfterTable, Object[] values)
			throws QueryException {
		return this.obj(clazz, sqlAfterTable, values, this.getRowMapper(clazz));
	}

	/**
	 * select sql 返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name desc
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T obj(Class<T> clazz, String sqlAfterTable, Object[] values,
			RowMapper<T> rowMapper) throws QueryException {
		return this.obj(clazz, null, sqlAfterTable, values);
	}

	/**
	 * select sql 返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name desc
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T obj(Class<T> clazz, String tablePostfix, String sqlAfterTable,
			Object[] values) throws QueryException {
		return this.obj(clazz, tablePostfix, sqlAfterTable, values,
				this.getRowMapper(clazz));
	}

	/**
	 * select sql 返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param sqlAfterTable
	 *            from table 之后的sql,例如select * from table where uid=? order name
	 *            desc, sqlAfterTable为where uid=? order name desc
	 * @param values
	 *            sql参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T obj(Class<T> clazz, String tablePostfix, String sqlAfterTable,
			Object[] values, RowMapper<T> rowMapper) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("select ");
		sql.append(info.getSelectedFieldSQL());
		sql.append(" from ");
		String tableName = info.getTableName();
		sql.append(tableName);
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" as ");
		sql.append(tableName);
		sql.append(" ");
		if (sqlAfterTable != null) {
			sql.append(sqlAfterTable);
		}
		List<T> list = jdbcSupport.list(sql.toString(), values, rowMapper);
		if (list.isEmpty()) {
			return null;
		}
		if (list.size() == 1) {
			return list.get(0);
		}
		throw new IncorrectResultSizeDataAccessException(1, list.size());
	}

	/**
	 * select sql 根据id查询，返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param idValue
	 *            id参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T objById(Class<T> clazz, Object idValue) throws QueryException {
		return this.objById(clazz, idValue, this.getRowMapper(clazz));
	}

	/**
	 * select sql 根据id查询，返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param idValue
	 *            id参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T objById(Class<T> clazz, Object idValue, RowMapper<T> rowMapper)
			throws QueryException {
		return this.objById(clazz, null, idValue, rowMapper);
	}

	/**
	 * select sql 根据id查询，返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param idValue
	 *            id参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T objById(Class<T> clazz, String tablePostfix, Object idValue)
			throws QueryException {
		return this.objById(clazz, tablePostfix, idValue,
				this.getRowMapper(clazz));
	}

	/**
	 * select sql 根据id查询，返回对象
	 * 
	 * @param clazz
	 *            查询对象类型
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param idValue
	 *            id参数
	 * @param rowMapper
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> T objById(Class<T> clazz, String tablePostfix, Object idValue,
			RowMapper<T> rowMapper) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		String sqlAfterTable = "where " + info.getIdColumnName() + "=?";
		return this.obj(clazz, tablePostfix, sqlAfterTable,
				new Object[] { idValue }, rowMapper);
	}

	public void setEntityTableInfoFactory(
			EntityTableInfoFactory entityTableInfoFactory) {
		this.entityTableInfoFactory = entityTableInfoFactory;
	}

	public void setJdbcSupport(JdbcSupport jdbcSupport) {
		this.jdbcSupport = jdbcSupport;
	}

	/**
	 * update sql，返回更新的记录数量。只更新选中的字段 例如: update table set field0=?,field1=?
	 * where field3=?
	 * 
	 * @param clazz
	 *            需要更新的类
	 * @param updateSqlSeg
	 *            sql片段,为update table 之后的sql。例如：set field0=?,field1=? where
	 *            field3=?
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int update(Class<T> clazz, String updateSqlSeg, Object[] values)
			throws QueryException {
		return this.update(clazz, null, updateSqlSeg, values);
	}

	/**
	 * update sql，返回更新的记录数量。只更新选中的字段 例如: update table set field0=?,field1=?
	 * where field3=?
	 * 
	 * @param clazz
	 *            需要更新的类
	 * @param tablePostfix
	 *            名称后缀，可与原表名组成新的表名
	 * @param updateSqlSeg
	 *            sql片段,为update table 之后的sql。例如：set field0=?,field1=? where
	 *            field3=?
	 * @param values
	 *            sql参数
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int update(Class<T> clazz, String tablePostfix,
			String updateSqlSeg, Object[] values) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(clazz);
		StringBuilder sql = new StringBuilder();
		sql.append("update ");
		sql.append(info.getTableName());
		if (this.isNotEmpty(tablePostfix)) {
			sql.append(tablePostfix);
		}
		sql.append(" ");
		sql.append(updateSqlSeg);
		return this.jdbcSupport.update(sql.toString(), values);
	}

	/**
	 * update sql ,返回更新的记录数量
	 * 
	 * @param t
	 *            update的对象
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int update(T t) throws QueryException {
		return this.update(t, null);
	}

	/**
	 * update sql ,返回更新的记录数量
	 * 
	 * @param t
	 *            update的对象
	 * @param tablePostfix
	 *            表名称后缀，可与原表名组成新的表名
	 * @return
	 * @throws QueryException
	 *             sql操作失败的异常
	 */
	public <T> int update(T t, String tablePostfix) throws QueryException {
		EntityTableInfo<T> info = this.getEntityTableInfo(t.getClass());
		SQLMapper<T> mapper = this.getSqlMapper(t.getClass());
		return this.jdbcSupport.update(info.getUpdateSQL(tablePostfix),
				mapper.getParamsForUpdate(t));
	}
}