package fr.inria.GeneWikiShexJava;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import com.github.jsonldjava.utils.JsonUtils;

import fr.univLille.cristal.shex.graph.RDF4JGraph;
import fr.univLille.cristal.shex.graph.RDFGraph;
import fr.univLille.cristal.shex.schema.Label;
import fr.univLille.cristal.shex.schema.ShexSchema;
import fr.univLille.cristal.shex.schema.parsing.GenParser;
import fr.univLille.cristal.shex.validation.RecursiveValidation;
import fr.univLille.cristal.shex.validation.ValidationAlgorithm;


/**
 * Hello world!
 *
 */
public class Main 
{	
	public static void usage() {
		System.out.println("Usage:  Main.java [command] [options]");
		System.out.println("Available command: list, validate, help");
		System.out.println();
		System.out.println("Options for list:");
		System.out.println(" --schema: list all the available schema");
		System.out.println(" --entity [schema]: list the available entity for a schema");
		System.out.println();
		System.out.println("Required options for validate:");
		System.out.println(" --schema [schema]");
		System.out.println(" --entity [entity-id]");
		System.exit(0);
	}
	
	public static void parseArgs(String[] args) {
		if (args.length==0) usage();
		if (args[0].equals("list")) {
			mode = "list";
			int i =1;
			while (i<args.length) {
				if (args[i].equals("--schema")) {
					mode+= "-schema";
				} else if (args[i].equals("--entity")) {
					i+=1;
					schema = args[i];
				} else {
					usage();
				}
				i+=1;
			}			
		} else if (args[0].equals("validate")) {
			mode = "validate";
			int i =1;
			while (i<args.length) {
				if (args[i].equals("--schema")) {
					i+=1;
					if (i==args.length) usage();
					schema= args[i];
				} else if (args[i].equals("--entity")) {
					i+=1;
					if (i==args.length) usage();
					entity = args[i];
				}  else {
					usage();
				}
				i+=1;
			}
			if (schema==null || entity==null) usage();			
		} else {
			usage();
		}
	}
	
	public static String mode=null;
	public static String schema=null;
	public static String entity=null;
		
    public static void main( String[] args ) throws Exception
    {
    	parseArgs(args);  

    	URL base_url = new URL("https://raw.githubusercontent.com/jdusart/Genewiki-ShEx/master/manifest.json");
       	InputStream is = base_url.openStream();

    	Object schemaObject = JsonUtils.fromInputStream(is,Charset.defaultCharset().name());
    	List<Map<String,Object>> list = (List) schemaObject;
    	
    	if (mode.equals("list-schema")) {
    		for (Map<String,Object> item:list)
    			System.out.println(item.get("key"));
    		System.exit(0);
    	}
    	
    	Map<String,Object> selected=null;
    	for (Map<String,Object> item:list)
    		if(item.get("key").equals(schema))
    			selected = item;
    	
    	if (selected==null) {
    		System.err.println("Unknown schema specified: "+schema);
    		System.exit(1);
    	}
    	
    	if (mode.equals("list")) {
    		System.out.println("List of entity: ");
        	String queryString = (String) selected.get("queryMap");
    		queryString = queryString.split("'''")[1];
        	ListEntities(queryString);
    		System.exit(0);      	
    	}
    	
    	
    	
    	System.out.println("Schema label: "+selected.get("schemaLabel"));
    	System.out.println("Data label: "+selected.get("dataLabel"));

    	Path dest_schema = Paths.get("schema.shex");
    	System.out.println("Downloading schema and saving it in: "+dest_schema);
    	URL schemaURL = new URL((String) selected.get("schemaURL"));
    	ReadableByteChannel rbc = Channels.newChannel(schemaURL.openStream());
    	FileOutputStream fos = new FileOutputStream(dest_schema.toString());
    	fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

    	String entityURL = "https://www.wikidata.org/wiki/Special:EntityData/"+entity+".ttl";
    	Path dest_data = Paths.get(entityURL.split("/")[5]);
    	System.out.println("Downloading data and saving it in: "+ dest_data);
    	URL dataURL = new URL(entityURL);
    	rbc = Channels.newChannel(dataURL.openStream());
    	fos = new FileOutputStream(dest_data.toString());
    	fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    	
    	ShexSchema schema = GenParser.parseSchema(dest_schema);
		Model data = Rio.parse(new FileInputStream(dest_data.toFile()), "http://www.wikidata.org/", RDFFormat.TURTLE);
    	RDFGraph dataGraph = new RDF4JGraph(data);
		
    	IRI focusNode = SimpleValueFactory.getInstance().createIRI("http://www.wikidata.org/entity/"+entity); //to change with what you want 
		Label shapeLabel = new Label(SimpleValueFactory.getInstance().createIRI((String) selected.get("shapeLabel"))); //to change with what you want 

		for (Label label:schema.getRules().keySet())
			System.out.println(label+": "+schema.getRules().get(label));
		for (Label label:schema.getTripleMap().keySet())
			System.out.println(label+": "+schema.getTripleMap().get(label));
		
		System.out.println("Recursive validation:");
		ValidationAlgorithm validation = new RecursiveValidation(schema, dataGraph);
		validation.validate(focusNode, shapeLabel);
		//check the result
		System.out.println("Does "+focusNode+" has shape "+shapeLabel+"? "+validation.getTyping().contains(focusNode, shapeLabel));

    	//perform validation
    }
    
    
    public static void ListEntities(String queryString) {
       	Repository db = new SPARQLRepository("https://query.wikidata.org/sparql");
    	db.initialize();
    	try (RepositoryConnection conn = db.getConnection()) { 
      		TupleQuery query = conn.prepareTupleQuery(queryString);
    		try (TupleQueryResult result = query.evaluate()) {
    			while (result.hasNext()) {
    				BindingSet solution = result.next();
    				String[] id = solution.getBinding("item").getValue().stringValue().split("/");
    				System.out.println(solution.getBinding("item").getValue()+"  >>ID>>  "+id[id.length-1]);
    			}
    		}
    	}
    	finally {
    		db.shutDown();
    	}
    }
}
