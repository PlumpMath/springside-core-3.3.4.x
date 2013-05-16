/**
 * Copyright (c) 2005-2010 springside.org.cn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * 
 * $Id: HibernateDao.java 1205 2010-09-09 15:12:17Z calvinxiu $
 */
package org.springside.modules.orm.hibernate;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EmbeddedId;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
import org.hibernate.impl.CriteriaImpl;
import org.hibernate.transform.ResultTransformer;
import org.springframework.util.Assert;
import org.springside.modules.orm.IgnoreCase;
import org.springside.modules.orm.Page;
import org.springside.modules.orm.PropertyFilter;
import org.springside.modules.orm.PropertyFilter.MatchType;
import org.springside.modules.utils.reflection.ReflectionUtils;

/**
 * 封装SpringSide扩展功能的Hibernat DAO泛型基类.
 * 
 * 扩展功能包括分页查询,按属性过滤条件列表查询.
 * 可在Service层直接使用,也可以扩展泛型DAO子类使用,见两个构造函数的注释.
 * 
 * @param <T> DAO操作的对象类型
 * @param <PK> 主键类型
 * 
 * @author calvin
 */
public class HibernateDao<T, PK extends Serializable> extends SimpleHibernateDao<T, PK> {

	/**
	 * 用于Dao层子类的构造函数.
	 * 通过子类的泛型定义取得对象类型Class.
	 * eg.
	 * public class UserDao extends HibernateDao<User, Long>{
	 * }
	 */
	public HibernateDao() {
		super();
	}

	/**
	 * 用于省略Dao层, Service层直接使用通用HibernateDao的构造函数.
	 * 在构造函数中定义对象类型Class.
	 * eg.
	 * HibernateDao<User, Long> userDao = new HibernateDao<User, Long>(sessionFactory, User.class);
	 */
	public HibernateDao(final SessionFactory sessionFactory, final Class<T> entityClass) {
		super(sessionFactory, entityClass);
	}

	//-- 分页查询函数 --//

	/**
	 * 分页获取全部对象.
	 */
	public Page<T> getAll(final Page<T> page) {
		return findPage(page);
	}

	/**
	 * 按HQL分页查询.
	 * 
	 * @param page 分页参数. 注意不支持其中的orderBy参数.
	 * @param hql hql语句.
	 * @param values 数量可变的查询参数,按顺序绑定.
	 * 
	 * @return 分页查询结果, 附带结果列表及所有查询输入参数.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Page<T> findPage(final Page<T> page, final String hql, final Object... values) {
		Assert.notNull(page, "page不能为空");

		Query q = createQuery(hql, values);

		if (page.isAutoCount()) {
			long totalCount = countHqlResult(hql, values);
			page.setTotalCount(totalCount);
		}

		setPageParameterToQuery(q, page);

		List result = q.list();
		page.setResult(result);
		return page;
	}

	/**
	 * 按HQL分页查询.
	 * 
	 * @param page 分页参数. 注意不支持其中的orderBy参数.
	 * @param hql hql语句.
	 * @param values 数量可变的查询参数,按顺序绑定.
	 * 
	 * @return 分页查询结果, 附带结果列表及所有查询输入参数.
	 */
	@SuppressWarnings("unchecked")
	public Page<Object> findPageForObject(Page<Object> page, final String hql, final Object... values) {
		Assert.notNull(page, "page不能为空");

		Query q = createQuery(hql, values);

		if (page.isAutoCount()) {
			long totalCount = countHqlResult(hql, values);
			page.setTotalCount(totalCount);
		}

		setPageParameterToQueryForObject(q, page);

		List<Object> result = q.list();
		page.setResult(result);
		return page;
	}

	/**
	 * 设置分页参数到Query对象,辅助函数.
	 */
	private void setPageParameterToQueryForObject(Query q, Page<Object> page) {

		Assert.isTrue(page.getPageSize() > 0, "Page Size must larger than zero");

		//hibernate的firstResult的序号从0开始
		q.setFirstResult(page.getFirst() - 1);
		q.setMaxResults(page.getPageSize());
	}

	/**
	 * 按HQL分页查询.
	 * 
	 * @param page 分页参数. 注意不支持其中的orderBy参数.
	 * @param hql hql语句.
	 * @param values 命名参数,按名称绑定.
	 * 
	 * @return 分页查询结果, 附带结果列表及所有查询输入参数.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Page<T> findPage(final Page<T> page, final String hql, final Map<String, ?> values) {
		Assert.notNull(page, "page不能为空");

		Query q = createQuery(hql, values);

		if (page.isAutoCount()) {
			long totalCount = countHqlResult(hql, values);
			page.setTotalCount(totalCount);
		}

		setPageParameterToQuery(q, page);

		List result = q.list();
		page.setResult(result);
		return page;
	}

	/**
	 * 按Criteria分页查询.
	 * 
	 * @param page 分页参数.
	 * @param criterions 数量可变的Criterion.
	 * 
	 * @return 分页查询结果.附带结果列表及所有查询输入参数.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Page<T> findPage(final Page<T> page, final Criterion... criterions) {
		Assert.notNull(page, "page不能为空");

		/**
		 *	保存查询条件中对象别名，以免重复添加导致Hibernate报异常.
		 */
		Set<String> aliases = new HashSet<String>();

		Criteria c = createCriteria(criterions);

		if (page.isAutoCount()) {
			long totalCount = countCriteriaResult(c);
			page.setTotalCount(totalCount);
		}

		setPageParameterToCriteria(c, page, aliases);

		List result = c.list();
		page.setResult(result);
		return page;
	}

	/**
	 * 设置分页参数到Query对象,辅助函数.
	 */
	protected Query setPageParameterToQuery(final Query q, final Page<T> page) {

		Assert.isTrue(page.getPageSize() > 0, "Page Size must larger than zero");

		//hibernate的firstResult的序号从0开始
		q.setFirstResult(page.getFirst() - 1);
		q.setMaxResults(page.getPageSize());
		return q;
	}

	/**
	 * 设置分页参数到Criteria对象,辅助函数.
	 */
	protected Criteria setPageParameterToCriteria(final Criteria c, final Page<T> page, final Set<String> aliases) {

		Assert.isTrue(page.getPageSize() > 0, "Page Size must larger than zero");

		//hibernate的firstResult的序号从0开始
		c.setFirstResult(page.getFirst() - 1);
		c.setMaxResults(page.getPageSize());

		if (page.isOrderBySetted()) {
			String[] orderByArray = StringUtils.split(page.getOrderBy(), ',');

			for (int i = 0; i < orderByArray.length; i++) {
				if (orderByArray[i].contains(".")) {
					String alias = StringUtils.substringBefore(orderByArray[i], ".");
					if (!aliases.contains(alias)) {
						c.createAlias(alias, alias);
						aliases.add(alias);
					}
				}
				String[] split = StringUtils.split(orderByArray[i]);
				if (split.length == 1) {
					c.addOrder(Order.desc(split[0]));
				} else {
					if (split[1].equals(Page.ASC)) {
						c.addOrder(Order.asc(split[0]));
					} else {
						c.addOrder(Order.desc(split[0]));
					}
				}
			}
		}
		return c;
	}

	/**
	 * 执行count查询获得本次Hql查询所能获得的对象总数.
	 * 
	 * 本函数只能自动处理简单的hql语句,复杂的hql查询请另行编写count语句查询.
	 */
	protected long countHqlResult(final String hql, final Object... values) {
		String countHql = prepareCountHql(hql);

		try {
			Long count = findUnique(countHql, values);
			return count;
		} catch (Exception e) {
			throw new RuntimeException("hql can't be auto count, hql is:" + countHql, e);
		}
	}

	/**
	 * 执行count查询获得本次Hql查询所能获得的对象总数.
	 * 
	 * 本函数只能自动处理简单的hql语句,复杂的hql查询请另行编写count语句查询.
	 */
	protected long countHqlResult(final String hql, final Map<String, ?> values) {
		String countHql = prepareCountHql(hql);

		try {
			Long count = findUnique(countHql, values);
			return count;
		} catch (Exception e) {
			throw new RuntimeException("hql can't be auto count, hql is:" + countHql, e);
		}
	}

	private String prepareCountHql(String orgHql) {
		String fromHql = orgHql;
		//select子句与order by子句会影响count查询,进行简单的排除.
		fromHql = "from " + StringUtils.substringAfter(fromHql, "from");
		fromHql = StringUtils.substringBefore(fromHql, "order by");

		String countHql = "";
		if (orgHql.indexOf("distinct") != -1) {
			countHql = "select count(distinct o) " + fromHql;
		} else {
			countHql = "select count(o) " + fromHql;
		}
		return countHql;
	}

	/**
	 * 执行count查询获得本次Criteria查询所能获得的对象总数.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected long countCriteriaResult(final Criteria c) {
		CriteriaImpl impl = (CriteriaImpl) c;

		// 先把Projection、ResultTransformer、OrderBy取出来,清空三者后再执行Count操作
		Projection projection = impl.getProjection();
		ResultTransformer transformer = impl.getResultTransformer();

		List<CriteriaImpl.OrderEntry> orderEntries = null;
		try {
			orderEntries = (List) ReflectionUtils.getFieldValue(impl, "orderEntries");
			ReflectionUtils.setFieldValue(impl, "orderEntries", new ArrayList());
		} catch (Exception e) {
			logger.error("不可能抛出的异常:{}", e.getMessage());
		}

		// 执行Count查询
		Long totalCountObject = (Long) c.setProjection(Projections.rowCount()).uniqueResult();
		long totalCount = (totalCountObject != null) ? totalCountObject : 0;

		// 将之前的Projection,ResultTransformer和OrderBy条件重新设回去
		c.setProjection(projection);

		if (projection == null) {
			c.setResultTransformer(CriteriaSpecification.ROOT_ENTITY);
		}
		if (transformer != null) {
			c.setResultTransformer(transformer);
		}
		try {
			ReflectionUtils.setFieldValue(impl, "orderEntries", orderEntries);
		} catch (Exception e) {
			logger.error("不可能抛出的异常:{}", e.getMessage());
		}

		return totalCount;
	}

	//-- 属性过滤条件(PropertyFilter)查询函数 --//

	/**
	 * 按属性查找对象列表,支持多种匹配方式.
	 * 
	 * @param matchType 匹配方式,目前支持的取值见PropertyFilter的MatcheType enum.
	 */
	public List<T> findBy(final String propertyName, final Object value, final MatchType matchType) {
		Criterion criterion = buildCriterion(propertyName, value, matchType);
		return find(criterion);
	}

	/**
	 * 按属性过滤条件列表查找对象列表.
	 */
	public List<T> find(List<PropertyFilter> filters) {
		Criterion[] criterions = buildCriterionByPropertyFilter(filters);
		return find(criterions);
	}

	/**
	 * 按属性过滤条件列表分页查找对象.
	 */
	/*public Page<T> findPage(final Page<T> page, final List<PropertyFilter> filters) {
		Criterion[] criterions = buildCriterionByPropertyFilter(filters);
		return findPage(page, criterions);
	}*/

	/**
	 * 按属性过滤条件列表分页查找对象.
	 * 添加了关联查询和关联排序功能 add by henryyan.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Page<T> findPage(final Page<T> page, final List<PropertyFilter> filters) throws Exception {
		Assert.notNull(page, "page不能为空");

		/**
		 *	保存查询条件中对象别名，以免重复添加导致Hibernate报异常.
		 */
		Set<String> aliases = new HashSet<String>();

		DetachedCriteria dc = buildPropertyFilterDetachedCriteria(filters, aliases);

		Criteria c = dc.getExecutableCriteria(getSession());
		if (page.isAutoCount()) {
			long totalCount = countCriteriaResult(c);
			page.setTotalCount(totalCount);
		}
		setPageParameterToCriteria(c, page, aliases);

		List result = c.list();
		page.setResult(result);
		return page;
	}

	protected DetachedCriteria buildPropertyFilterDetachedCriteria(final List<PropertyFilter> filters, final Set<String> aliases) {
		DetachedCriteria dc = DetachedCriteria.forClass(entityClass);

		for (PropertyFilter filter : filters) {
			if (!filter.hasMultiProperties()) {
				if (filter.getPropertyName().contains(".")) {
					String alias = StringUtils.substringBefore(filter.getPropertyName(), ".");
					String newAlias = alias.substring(0, 1).toUpperCase() + alias.substring(1);
					Method method = ReflectionUtils.getAccessibleMethod(entityClass, "get" + newAlias);
					EmbeddedId embeddedId = method.getAnnotation(EmbeddedId.class);

					// 忽略联合主键
					if (embeddedId != null) {
						continue;
					}
					if (!aliases.contains(alias)) {
						dc.createAlias(alias, alias, CriteriaSpecification.LEFT_JOIN); //默认用left join
						aliases.add(alias);
					}
				}
			} else {
				//含有or的情况
				for (String propertyName : filter.getPropertyNames()) {
					if (propertyName.contains(".")) {
						String alias = StringUtils.substringBefore(filter.getPropertyName(), ".");
						String newAlias = alias.substring(0, 1).toUpperCase() + alias.substring(1);
						Method method = ReflectionUtils.getAccessibleMethod(entityClass, "get" + newAlias);

						// 忽略联合主键
						EmbeddedId embeddedId = method.getAnnotation(EmbeddedId.class);
						if (embeddedId != null) {
							continue;
						}
						if (!aliases.contains(alias)) {
							dc.createAlias(alias, alias, CriteriaSpecification.LEFT_JOIN);
							aliases.add(alias);
						}
					}
				}
			}
		}
		Criterion[] criterions = buildCriterionByPropertyFilter(filters);
		for (Criterion criterion : criterions) {
			dc.add(criterion);
		}
		return dc;
	}

	/**
	 * 按属性条件参数创建Criterion,辅助函数.
	 */
	protected Criterion buildCriterion(final String propertyName, final Object propertyValue, final MatchType matchType) {
		Assert.hasText(propertyName, "propertyName不能为空");
		Criterion criterion = null;
		SimpleExpression se = null;
		//根据MatchType构造criterion
		switch (matchType) {
		case EQ:
			se = Restrictions.eq(propertyName, propertyValue);
			criterion = ignoreCase(se, propertyName, propertyValue);
			break;
		case NE:
			criterion = Restrictions.ne(propertyName, propertyValue);
			break;
		case LIKE:
			if (propertyValue instanceof String) {
				se = Restrictions.like(propertyName, (String) propertyValue, MatchMode.ANYWHERE);
				criterion = ignoreCase(se, propertyName, propertyValue);
			} else {
				logger.warn("属性{}使用LIKE查询，为非字符型，实际类型为：{}，自动使用EQ查询", propertyName, propertyValue.getClass().getName());
				se = Restrictions.eq(propertyName, propertyValue);
				criterion = ignoreCase(se, propertyName, propertyValue);
			}
			break;
		case LE:
			criterion = Restrictions.le(propertyName, propertyValue);
			break;
		case LT:
			criterion = Restrictions.lt(propertyName, propertyValue);
			break;
		case GE:
			criterion = Restrictions.ge(propertyName, propertyValue);
			break;
		case GT:
			criterion = Restrictions.gt(propertyName, propertyValue);
			break;
		case ISN:
			criterion = Restrictions.isNull(propertyName);
			break;
		case NN:
			criterion = Restrictions.isNotNull(propertyName);
			break;
		case IN:
			criterion = Restrictions.in(propertyName, (Object[]) propertyValue);
			break;
		}
		return criterion;
	}

	/**
	 * 根据注解IgnoreCase设置是否忽略大小写
	 * @param propertyName
	 * @param propertyValue
	 * @return
	 */
	private SimpleExpression ignoreCase(SimpleExpression se, final String propertyName, final Object propertyValue) {
		try {
			Class<?> clazz = entityClass;
			String[] props = propertyName.split("\\.");
			for (int i = 0; i < props.length - 1; i++) {
				String methodName = "get" + props[i].substring(0, 1).toUpperCase() + props[i].substring(1);
				Method method = clazz.getMethod(methodName);
				if (method != null) {
					clazz = method.getReturnType();
				}
			}
			String methodName = "get" + props[props.length - 1].substring(0, 1).toUpperCase()
					+ props[props.length - 1].substring(1);
			Method method = clazz.getMethod(methodName);
			if (method.isAnnotationPresent(IgnoreCase.class)) {
				se = se.ignoreCase();
			}
		} catch (SecurityException e) {
			throw new HibernateException(e);
		} catch (NoSuchMethodException e) {
			throw new HibernateException(e);
		}
		return se;
	}

	/**
	 * 按属性条件列表创建Criterion数组,辅助函数.
	 */
	protected Criterion[] buildCriterionByPropertyFilter(final List<PropertyFilter> filters) {
		List<Criterion> criterionList = new ArrayList<Criterion>();
		for (PropertyFilter filter : filters) {
			if (!filter.hasMultiProperties()) { //只有一个属性需要比较的情况.
				Criterion criterion = buildCriterion(filter.getPropertyName(), filter.getMatchValue(), filter.getMatchType());
				criterionList.add(criterion);
			} else {//包含多个属性需要比较的情况,进行or处理.
				Disjunction disjunction = Restrictions.disjunction();
				for (String param : filter.getPropertyNames()) {
					Criterion criterion = buildCriterion(param, filter.getMatchValue(), filter.getMatchType());
					disjunction.add(criterion);
				}
				criterionList.add(disjunction);
			}
		}
		return criterionList.toArray(new Criterion[criterionList.size()]);
	}
}
