package org.springframework.data.reindexer.repository.support;

import java.util.Iterator;

import ru.rt.restream.reindexer.Namespace;
import ru.rt.restream.reindexer.Query;
import ru.rt.restream.reindexer.Reindexer;

import org.springframework.data.reindexer.repository.query.ReindexerEntityInformation;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.repository.query.parser.PartTree.OrPart;
import org.springframework.util.Assert;

/**
 * A {@link RepositoryQuery} implementation for Reindexer.
 *
 * @author Evgeniy Cheban
 */
public class ReindexerRepositoryQuery implements RepositoryQuery {

	private final QueryMethod queryMethod;

	private final Namespace<?> namespace;

	private final PartTree tree;

	/**
	 * Creates an instance.
	 *
	 * @param queryMethod the {@link QueryMethod} to use
	 * @param entityInformation the {@link ReindexerEntityInformation} to use
	 * @param reindexer the {@link Reindexer} to use                         
	 */
	public ReindexerRepositoryQuery(QueryMethod queryMethod, ReindexerEntityInformation<?, ?> entityInformation, Reindexer reindexer) {
		this.queryMethod = queryMethod;
		this.namespace = reindexer.openNamespace(entityInformation.getNamespaceName(), entityInformation.getNamespaceOptions(),
				entityInformation.getJavaType());
		this.tree = new PartTree(queryMethod.getName(), entityInformation.getJavaType());
	}

	@Override
	public Object execute(Object[] parameters) {
		Query<?> query = createQuery(parameters);
		if (this.queryMethod.isCollectionQuery()) {
			return query.toList();
		}
		if (this.queryMethod.isStreamQuery()) {
			return query.stream();
		}
		return query.findOne();
	}

	private Query<?> createQuery(Object[] parameters) {
		ParametersParameterAccessor accessor =
				new ParametersParameterAccessor(this.queryMethod.getParameters(), parameters);
		Query<?> base = null;
		Iterator<Object> iterator = accessor.iterator();
		for (OrPart node : this.tree) {
			Iterator<Part> parts = node.iterator();
			Assert.state(parts.hasNext(), () -> "No part found in PartTree " + this.tree);
			Query<?> criteria = where(parts.next(), (base != null) ? base : this.namespace.query(), iterator);
			while (parts.hasNext()) {
				criteria = where(parts.next(), criteria, iterator);
			}
			base = criteria.or();
		}
		return base;
	}

	private Query<?> where(Part part, Query<?> criteria, Iterator<Object> parameters) {
		String indexName = part.getProperty().toDotPath();
		switch (part.getType()) {
			case GREATER_THAN:
				return criteria.where(indexName, Query.Condition.GT, parameters.next());
			case GREATER_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.GE, parameters.next());
			case LESS_THAN:
				return criteria.where(indexName, Query.Condition.LT, parameters.next());
			case LESS_THAN_EQUAL:
				return criteria.where(indexName, Query.Condition.LE, parameters.next());
			case IS_NOT_NULL:
				return criteria.isNotNull(indexName);
			case IS_NULL:
				return criteria.isNull(indexName);
			case SIMPLE_PROPERTY:
				return criteria.where(indexName, Query.Condition.EQ, parameters.next());
			case NEGATING_SIMPLE_PROPERTY:
				return criteria.not().where(indexName, Query.Condition.EQ, parameters.next());
			default:
				throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}

}
