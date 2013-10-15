package org.aksw.sparqlmap.core.db;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.aksw.sparqlmap.core.config.syntax.r2rml.ColumnHelper;
import org.aksw.sparqlmap.core.mapper.translate.DataTypeHelper;
import org.aksw.sparqlmap.core.mapper.translate.ImplementationException;
import org.apache.commons.codec.binary.Hex;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIException;
import org.apache.jena.iri.IRIFactory;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.profiler.Profiler;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.hp.hpl.jena.datatypes.BaseDatatype;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.core.ResultBinding;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import com.hp.hpl.jena.sparql.engine.binding.BindingFactory;
import com.hp.hpl.jena.sparql.engine.binding.BindingMap;
import com.hp.hpl.jena.vocabulary.RDFS;

public class SQLResultSetWrapper implements com.hp.hpl.jena.query.ResultSet {
	

	DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd");
	DateTimeFormatter timeFormatter = DateTimeFormat.forPattern("HH:mm:ss.SSS");
	DateTimeFormatter datetimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
	
	String iriPattern = " ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
	
	String baseUri = null;
	DecimalFormat doubleFormatter = new DecimalFormat("0.0##########################E0");
		static org.slf4j.Logger log = org.slf4j.LoggerFactory
			.getLogger(SQLResultSetWrapper.class);

	private ResultSet rs;

	private List<String> vars = new ArrayList<String>();
	private List<String> colNames = new ArrayList<String>();

	private Connection conn;

	private boolean didNext = false;

	private boolean hasNext  = false;

	private DataTypeHelper dth;
	
	
	private Stopwatch sw;
	Multimap<String, Long>  profiler;

	public SQLResultSetWrapper(ResultSet rs, Connection conn, DataTypeHelper dth, String baseUri)
			throws SQLException {
		this.conn = conn;
		this.rs = rs;
		this.dth = dth;
		this.baseUri = baseUri;
		initVars();
		sw = new Stopwatch().start();
	}
	
	public void setProfiler(Multimap<String, Long> profiler) {
		this.profiler = profiler;
	}



	
	private Multimap< String, String> var2ResourceCols = TreeMultimap.create();

	/**
	 * for finding all the columns, we rely on that there is a type col for each
	 * line
	 * 
	 * @throws SQLException
	 */
	public void initVars() throws SQLException {

		for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
			String colname = rs.getMetaData().getColumnName(i);
			colNames.add(colname);

			if (colname.endsWith(ColumnHelper.COL_NAME_RDFTYPE)) {
				vars.add(colname.substring(0, colname.length()
						- ColumnHelper.COL_NAME_RDFTYPE.length()));
			}
			
			if(colname.contains(ColumnHelper.COL_NAME_RESOURCE_COL_SEGMENT)){
				//we extract the var name
				String var = ColumnHelper.colnameBelongsToVar(colname);
				var2ResourceCols.put(var,colname);
			}

		}
		
		
	}

	@Override
	public void remove() {
		throw new ImplementationException("thou shalt not remove!");

	}

	@Override
	public boolean hasNext() {

		if (!didNext) {
			try {
				hasNext = rs.next();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				log.error("Error:", e);
				hasNext = false;
			}
			didNext = true;
		}

		if (!hasNext) {
			close();
		}
		return hasNext;

	}

	@Override
	public QuerySolution next() {
		return nextSolution();
	}


	@Override
	public QuerySolution nextSolution() {
		return new ResultBinding(null, nextBinding());
	}

	int deUnionPointer = 0;
	Binding deUnionBinding = null;
	@Override
	public Binding nextBinding() {
		BindingMap binding = null;
		try {
			//if(deUnionPointer==0||deUnionPointer>deUnionsCount){
				if (!didNext) {
					rs.next();
				}
				didNext = false;
	
				binding = BindingFactory.create();
	
				for (String var : vars) {
					Node node= null;
					// create the binding here
					// first check for type
					if(rs.getObject(var + ColumnHelper.COL_NAME_RDFTYPE)!=null){
						
						Integer type = rs.getInt(var + ColumnHelper.COL_NAME_RDFTYPE);
						if(type.equals(0)){ // funny as getInt for null-values returns 0. We should have checked before.
							throw new ImplementationException("Unidentifiable rdf type encountered.");
						} else 	if (type.equals(ColumnHelper.COL_VAL_TYPE_RESOURCE) || type.equals(ColumnHelper.COL_VAL_TYPE_BLANK)) {
							node = createResource(var, type);			
						} else if (type == ColumnHelper.COL_VAL_TYPE_LITERAL) {
							node = createLiteral(var);
						} 
						if(node!=null){
							binding.add(Var.alloc(var), node);
						}
					}
				//}
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			log.error("Error:", e);
		}
		
		//detect deunion columns, just check for the first on
		
//		if(!deunionVars.isEmpty()){
//			//if deuinontuple is bound on is bound
//			for(;this.deUnionPointer<=this.deUnionsCount; ){
//				//get all varibles that we need to bind here
//				List<String> varstobind = new ArrayList<String>();
//				for(String duvar : deunionVars){
//					if(duvar.endsWith("-du"+String.format("%02d", deUnionPointer))){
//						varstobind.add(duvar);
//					}
//				}
//				
//				
//				
//				
//				
//			}
//		}
		
		
		
		return binding;
	}

	private Node createLiteral(String var) throws SQLException {
		Node node = null;
		String litType = rs.getString(var + ColumnHelper.COL_NAME_LITERAL_TYPE);
		RDFDatatype dt = null;
		if(litType!=null&&!litType.isEmpty()&&!litType.equals(RDFS.Literal.getURI())){
			dt = new BaseDatatype(litType);
		}
		
		String lang =  rs.getString(var + ColumnHelper.COL_NAME_LITERAL_LANG);
		
		if(lang!=null && lang.equals(RDFS.Literal.getURI())){
			lang = null;
		}

		String literalValue;
		
		if(XSDDatatype.XSDdecimal.getURI().equals(litType)){
			literalValue = rs.getBigDecimal(var + ColumnHelper.COL_NAME_LITERAL_NUMERIC).toString();
		
		}else if( XSDDatatype.XSDdouble.getURI().equals(litType)){
			literalValue = doubleFormatter.format(rs.getDouble(var + ColumnHelper.COL_NAME_LITERAL_NUMERIC));
		
		}else if( XSDDatatype.XSDint.getURI().equals(litType)||XSDDatatype.XSDinteger.getURI().equals(litType)){
			literalValue =Integer.toString(rs.getInt((var + ColumnHelper.COL_NAME_LITERAL_NUMERIC)));
		
		}else if(XSDDatatype.XSDstring.getURI().equals( litType)|| litType ==null){
			literalValue = rs.getString(var + ColumnHelper.COL_NAME_LITERAL_STRING);
		
		}else if(XSDDatatype.XSDdateTime.getURI().equals(litType)){
			literalValue = datetimeFormatter.print( (rs.getTimestamp(var+ColumnHelper.COL_NAME_LITERAL_DATE)).getTime());
		
		}else if(XSDDatatype.XSDdate.getURI().equals(litType) ){
			literalValue = dateFormatter.print(rs.getTimestamp(var+ColumnHelper.COL_NAME_LITERAL_DATE).getTime());
			
		}else if( XSDDatatype.XSDtime.getURI().equals(litType)){
			literalValue = timeFormatter.print(rs.getTimestamp(var+ColumnHelper.COL_NAME_LITERAL_DATE).getTime());
		
		}else if(XSDDatatype.XSDboolean.getURI().equals(litType)){
			literalValue = Boolean.toString(rs.getBoolean(var+ColumnHelper.COL_NAME_LITERAL_BOOL));
		
		}else if(XSDDatatype.XSDhexBinary.getURI().equals(litType)){
			String hex= Hex.encodeHexString(rs.getBytes(var+ColumnHelper.COL_NAME_LITERAL_BINARY));
			literalValue = new String(hex);
		
		}else{
			if(colNames.contains(var + ColumnHelper.COL_NAME_LITERAL_STRING)&&rs.getString(var + ColumnHelper.COL_NAME_LITERAL_STRING)!=null){
				literalValue = rs.getString(var + ColumnHelper.COL_NAME_LITERAL_STRING);
			}else if(colNames.contains(var + ColumnHelper.COL_NAME_LITERAL_DATE)&& rs.getString(var + ColumnHelper.COL_NAME_LITERAL_DATE)!=null){
				literalValue = rs.getString(var + ColumnHelper.COL_NAME_LITERAL_DATE);
			}else if(colNames.contains(var + ColumnHelper.COL_NAME_LITERAL_NUMERIC)&& rs.getString(var + ColumnHelper.COL_NAME_LITERAL_NUMERIC)!=null){
				literalValue = rs.getString(var + ColumnHelper.COL_NAME_LITERAL_NUMERIC);
			}else{
				//throw new ImplementationException("Should not get here, check datatypes");
				literalValue=null;
			}
		}
		
		if(literalValue !=null){
			node = Node.createLiteral(literalValue,	lang, dt);
		}else{
			node = null; 
		}
		
		return node;

	}

	private Node createResource(String var, Integer type) throws SQLException {
		Node node;
		StringBuffer uri = new StringBuffer();
		int i = 0;
		for(String colname : this.var2ResourceCols.get(var)){
			if(i++%2==0){
				//fix string
				String segment =rs.getString(colname);
				if(segment !=null){
					uri.append(segment);
				}
			}else{
				//column derived valued
				String segment =rs.getString(colname);
				if(segment !=null){
					try {
						uri.append(URLEncoder.encode(segment, "US-ASCII").replaceAll("\\+", "%20"));
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						log.error("Error:",e);
					}
				}
			}
		}

		if(uri.length()==0){
			node = null; 
		}else{
			if(type.equals(ColumnHelper.COL_VAL_TYPE_RESOURCE)){
				if(baseUri!=null){
					try{
						node = Node.createURI(uri.toString());
					}catch(IRIException e){
						try {
							node = Node.createURI(uri.toString());
						} catch (IRIException e1) {
							log.warn("Trying to create invalid IRIs, using :" + uri.toString());
							node = null;
						}
					}
				}else{
					node = Node.createURI(uri.toString());
				}
			}else{
				node = Node.createAnon(new AnonId(uri.toString()));
			}
			
		}
		return node;
	}

	@Override
	public int getRowNumber() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<String> getResultVars() {
		return vars;
	}

	@Override
	public Model getResourceModel() {
		log.warn("getREsourceModel not implemented");
		return null;
	}

	public ResultSet getRs() {
		return rs;
	}

	public void close() {
		if(profiler!=null&&sw.isRunning()){
			sw.stop();
			profiler.put("3 closed after ",sw.elapsedTime(TimeUnit.MICROSECONDS));
		}
		
		try {
			if (rs.isClosed() == false) {
				rs.close();
				conn.close();
			}
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			log.error("Error:", e);
		}
	}
	
	
//	static String percentEncode(String toencode)
//			{
//
//
//		byte[] bytes = encodeBytes(source.getBytes("UTF-8"));
//		return new String(bytes, "");
//	}
//
//	private static byte[] encodeBytes(byte[] source, Type type) {
//		Assert.notNull(source, "'source' must not be null");
//        Assert.notNull(type, "'type' must not be null");
//
//        ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length);
//        for (int i = 0; i < source.length; i++) {
//            int b = source[i];
//            if (b < 0) {
//                b += 256;
//            }
//            if (type.isAllowed(b)) {
//                bos.write(b);
//            }
//            else {
//                bos.write('%');
//
//                char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
//                char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
//
//                bos.write(hex1);
//                bos.write(hex2);
//            }
//        }
//        return bos.toByteArray();
//    }


}