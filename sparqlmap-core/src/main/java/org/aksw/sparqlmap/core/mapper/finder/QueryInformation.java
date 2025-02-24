package org.aksw.sparqlmap.core.mapper.finder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.algebra.Op;
import com.hp.hpl.jena.sparql.algebra.op.OpDistinct;
import com.hp.hpl.jena.sparql.algebra.op.OpOrder;
import com.hp.hpl.jena.sparql.algebra.op.OpProject;
import com.hp.hpl.jena.sparql.algebra.op.OpReduced;
import com.hp.hpl.jena.sparql.algebra.op.OpSlice;
import com.hp.hpl.jena.sparql.expr.Expr;



/**
 * Contains some information about the query, which is handy for the query translation services.
 * 
 * @author joerg
 *
 */

public class QueryInformation {

	private Op query;
	
	private OpSlice slice;
	private OpOrder order;
	private OpProject project;
	private OpDistinct distinct;
	private OpReduced reduced;
	
	private Map<Triple, Map<String,Collection<Expr>>> filtersforvariables = new HashMap<Triple, Map<String,Collection<Expr>>>();

	private boolean projectionPush ;

	public Op getQuery() {
		return query;
	}

	public void setQuery(Op query) {
		this.query = query;
	}

	public OpSlice getSlice() {
		return slice;
	}

	public void setSlice(OpSlice slice) {
		this.slice = slice;
	}

	public OpOrder getOrder() {
		return order;
	}

	public void setOrder(OpOrder order) {
		this.order = order;
	}

	public OpProject getProject() {
		return project;
	}

	public void setProject(OpProject project) {
		this.project = project;
	}

	public OpDistinct getDistinct() {
		return distinct;
	}

	public void setDistinct(OpDistinct distinct) {
		this.distinct = distinct;
	}

	public OpReduced getReduced() {
		return reduced;
	}

	public void setReduced(OpReduced reduced) {
		this.reduced = reduced;
	}

	public Map<Triple, Map<String, Collection<Expr>>> getFiltersforvariables() {
		return filtersforvariables;
	}

	public void setFiltersforvariables(
			Map<Triple, Map<String, Collection<Expr>>> filtersforvariables) {
		this.filtersforvariables = filtersforvariables;
	}

	public void setProjectionPushable(boolean projectionPush) {
		this.projectionPush = projectionPush;		
	}
	
	public boolean isProjectionPush() {
		return projectionPush;
	}
	
	
	
	
	


	

}
