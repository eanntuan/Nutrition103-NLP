

import iitb.Segment.Segment.F1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import junit.framework.Assert;

import org.apache.commons.configuration.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;

import edu.mit.csail.asgard.syntax.CRFToken;
import edu.mit.csail.asgard.syntax.Feature;
import edu.mit.csail.asgard.syntax.Features;
import edu.mit.csail.asgard.syntax.ParentNode;
import edu.mit.csail.asgard.syntax.ParseNode;
import edu.mit.csail.asgard.syntax.Sentence;
import edu.mit.csail.asgard.syntax.SentenceSegment;
import edu.mit.csail.asgard.syntax.SentenceTagger;
import edu.mit.csail.asgard.util.Configure;

/*
 * Evaluates various methods for associating food items with attributes.
 * 
 * Specify the approach using an argument: Simple|Dependency|FST|CRF
 * 
 * Specify which data file to evaluate using second argument
 * (e.g. /afs/csail.mit.edu/u/k/korpusik/nutrition/results.test)
 * 
 * Specify which file to write mistakes to using third argument
 * (e.g. /afs/csail.mit.edu/u/k/korpusik/nutrition/fst_segmentations.txt)
 */

public class Eval {
	
	// create file to write predicted segments and mistakes to
	static PrintWriter writer;
	
	public static SentenceTagger initialize() throws FileNotFoundException, UnsupportedEncodingException{
		final String[] namePath = {"Nutrition", "Asgard", ""};
		final String domain = "Nutrition";
		//static final String confFile = "conf/eng-fix-SL-NG";
		final String confFile = "conf/semlab";

		ServletContext servletContext;
		Configuration configuration;
		File crfBase;
		Feature segmenter;
		SentenceTagger sentenceTagger = null;
		
		String nutritionixAppID = null;
		String nutritionixAppKey = null;		
		
		// run what gets run for a web servlet (NutritionContext.initialize)
		ArrayList<String> names = new ArrayList<String>(namePath.length+2);
		    	
		names.add(domain);
		for(String name : namePath)
			names.add(name);
		    configuration = Configure.getConfiguration(names);
			configuration.setProperty("domain", domain);
				
			nutritionixAppID = configuration.getString("/Nutritionix/@appID");
			nutritionixAppKey = configuration.getString("/Nutritionix/@appKey");
				
			System.err.format("ID=%s KEY=%s%n", nutritionixAppID, nutritionixAppKey);
				
			crfBase = new File("/afs/csail.mit.edu/u/k/korpusik/workspace/Nutrition/WebContent/WEB-INF/CRF/samples");
				
			try {
				Features.initializeFeatureComputers(configuration.getString("/tagger/@url"), true);
				segmenter = Features.whitespace_tokenizer;
				sentenceTagger = new SentenceSegment(crfBase.getPath(), new File(crfBase, confFile).toString());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		return sentenceTagger;
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		SentenceTagger sentenceTagger = initialize();
		
		// set type
		String path = "src/edu/mit/csail/sls/nut/results.test"; // data file to evaluate on
		String segment_type = "CRF"; // by default, use CRF approach
		String labelRep = "IOE";
		String tag_type = "crfsuite";
		if (args.length > 0){
			segment_type = args[0]; // if arg specifies approach type, use that
			if (args.length > 1){
				path = args[1]; // specify path for data to evaluate
				if (args.length > 2) {
					System.out.println("set printwriter to file "+args[2]);
					writer = new PrintWriter(args[2], "UTF-8");
				}
								
			}

		}
		if (args.length > 3 || (!segment_type.equals("Simple") && !segment_type.equals("Dependency") && !segment_type.equals("FST") && !segment_type.equals("CRF"))) {
			System.out.println("Usage: java Eval Simple|Dependency|FST|CRF intput-data-file output-file-path");
			return;
		}
		
		// initialize F1 object
		//int nlabels = 5;
		int nlabels = 0;
    	boolean excludeFromOverallF1[] = {false, false, false, false, true, false};
    	List<String> labelNames = new ArrayList<String>();
    	String[] labels = {"Food", "Brand", "Quantity", "Description", "Other"};
    	for (String label : labels){
    		labelNames.add(label);
    	}
    	F1 f1 = new F1(nlabels, excludeFromOverallF1);
		
		// load JSON object of labeled food-attribute data from file
		Charset encoding = StandardCharsets.UTF_8;
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		String jsonstr = new String(encoded, encoding);
		
		JSONObject json = new JSONObject(jsonstr);
        Iterator<?> keys = json.keys();

        String text = "";
        JSONObject val = null;
        int j = 0;
        int totalAttrs = 0;
        int totalPredicted = 0;
    	
    	// loop through each query in test data file
    	while( keys.hasNext() ){
            text = (String)keys.next();
            
            // populate map of Turker-labeled food-attribute pairs from JSON
    		Map <String, ArrayList<Segment>> goldStandard = new HashMap<>();
            
            // get gold standard from labeled data
            val = (JSONObject) json.get(text);
            System.out.println("original text: "+text);
            writer.println("\nText: "+text);
            String[] words = text.split("\\s+");
            Iterator<?> foodIndices = val.keys();

            String index = "";
            JSONObject attrs = null;
            while( foodIndices.hasNext() ){
            	
            	ArrayList<Segment> attrList = new ArrayList<Segment>();
                index = (String)foodIndices.next();
                if (index.equals("labelIndex") || index.equals("reverseLabelIndex")){
            		continue;
            	}
                attrs = (JSONObject) val.get(index);
                Iterator<?> attributes = attrs.keys();
                while( attributes.hasNext() ){
                    Segment seg = new Segment();
                	seg.label = (String)attributes.next();
                	JSONArray attrIndices = (JSONArray) attrs.get(seg.label);
                	seg.label = seg.label.substring(0, 1).toUpperCase() + seg.label.substring(1);
                	int start = -1;
                	int end = -1;
                	//add start and end to seg
                	for (int i = 0; i < attrIndices.length(); i++) {
                		  if (start ==-1){
                			  start = (int) attrIndices.get(i);
                			  end = start+1;
                		  } else if ((int) attrIndices.get(i)==end){
                			  end ++;
                		  } else {
                			  // if indices are not adjacent, start new seg
                			  seg.start = start;
                			  seg.end = end;
                			  attrList.add(seg);
                			  //System.out.println(seg.start+" "+seg.end+" "+Arrays.asList(words).subList(seg.start, seg.end));
                			  String label = seg.label;
                			  seg = new Segment();
                			  seg.label = label;
                			  start = (int) attrIndices.get(i);
                			  end = start+1;
                		  }
                	}
                	//System.out.println(seg.label+attrIndices);
                	// add seg if it exists
                	if (attrIndices.length()>0){	
                		seg.start = start;
                		seg.end = end;
                    	// don't add segments that are the word
                		if (Integer.parseInt(index)!=seg.start){
                			if (Integer.parseInt(index)==seg.end-1) {
                				seg.end = seg.end-1;
                			}
                    		attrList.add(seg);
                		} 
                    	//System.out.println(seg.start+" "+seg.end+" "+Arrays.asList(words).subList(seg.start, seg.end));
                	}
                }
                int i = Integer.parseInt(index);
				goldStandard.put(words[i]+i, attrList); 
                //System.out.println("food: "+words[i]);
            }
            
            // run CRF and get food-attribute dependencies
    		NLPData segmentation = Tag.runCRF(writer, text, segment_type, sentenceTagger, true, labelRep, tag_type);
    		for(Segment segment : segmentation.segments){
    			// only continue if this is an attribute segment
    			if (segment.label.contains("Food") || segment.label.contains("Other")) {
    				continue;
    			}
    			totalAttrs ++;
    		}
    		
            // print predicted attributes and write to file
    		if (writer!=null){
    			writer.println("\nSegmentation: ");
    		}
            for(String food : segmentation.attributes.keySet()){
            	System.out.println("\nFood: "+food);
            	if (writer!=null){
            		writer.println("\nFood: "+food);
            	}
            	ArrayList<Segment> predictedAttrs = segmentation.attributes.get(food);
            	for(Segment attrSeg : predictedAttrs){
            		System.out.println(attrSeg.label+" "+attrSeg.start+" "+attrSeg.end);
            		String value = attrSeg.label;
            		for(int k=attrSeg.start; k < attrSeg.end; k++){
            			value=value+" "+segmentation.tokens.get(k);
            		}
            		if (writer!=null){
            			writer.println(value);
            		}
            	}
            	totalPredicted += predictedAttrs.size();
            	f1.totalAuto[0] += predictedAttrs.size();
            }
    	    
            // calculate the number of predicted attrs that match AMT attrs
            for(String food : goldStandard.keySet()){
            	ArrayList<Segment> actualAttrs = goldStandard.get(food);
            	ArrayList<Segment> predictedAttrs = segmentation.attributes.get(food);
            	if (predictedAttrs!=null){
                	//f1.totalAuto[0] += predictedAttrs.size();
            	}
            	System.out.println("\nGold Standard Food: "+food);
            	for(Segment actualSeg : actualAttrs){
            		boolean match = false;
            		// print mistakes (i.e. no attributes) and write to file
            		if (predictedAttrs==null){
            			if (writer!=null){
            				writer.println("\nRecall Mistake: ");
            				writer.println("Food: "+food);
            				writer.println("No attributes for food");
            			}
            			System.out.println("no predicted attrs!");
            			f1.totalMan[0] += 1;
            			continue;
            		}   
            		// use indices to construct gold standard attr string
            		String actualStr = "";
            		for (String s : Arrays.copyOfRange(words, actualSeg.start, actualSeg.end)){
            			actualStr += s + " ";
            		}
            		System.out.println("\nactual "+actualSeg.label+" "+actualSeg.start+" "+actualSeg.end+" "+actualStr);

            		// compare predicted and AMT labeled attributes
            		for(Segment predictedSeg : predictedAttrs){
            			// use indices to construct predicted attr string
            			String predictedStr = "";
            			for(int k=predictedSeg.start; k < predictedSeg.end; k++){
            				predictedStr += segmentation.tokens.get(k) + " ";
                		}
            			System.out.println("predicted "+predictedSeg.label+" "+predictedSeg.start+" "+predictedSeg.end+" "+predictedStr);
            			// if labels and strings match, increment match count
            			if(actualSeg.label.equals(predictedSeg.label) && predictedStr.equals(actualStr)){
                			System.out.println("Match!");
            				f1.totalCorrect[0]++;
            				match = true;
            				break;
                		} else if (!actualAttrs.contains(predictedSeg)){
            				// print out precision mistakes too
                			writer.println("\nPrecision Mistake: ");
            				writer.println("Food: "+food);
            				writer.println("Incorrect attribute: "+predictedSeg.label+": "+predictedStr);
                		}
                	}
            		// print mistake (i.e. missing attribute) & write to file
            		if (!match){
            			if (writer!=null){
            				writer.println("\nRecall Mistake: ");
            				writer.println("Food: "+food);
            				writer.println("Missed attribute: "+actualSeg.label+": "+actualStr);
            			}
            			System.out.println("no match!");
            		}
            		
        			f1.totalMan[0] += 1;
            	}
            }
            
    	    j++;
    	    
    	    /*
    	    if (j>1){
    	    	break;
    	    }
    	    */
    	    
    	    
        }
    	if (writer!=null){
    		writer.close();
    	}
    	
	    // compute and print F1 score
    	f1.compute();
		f1.printResults(labelNames);
		System.out.println("Total number of attributes: "+totalAttrs);
		System.out.println("Sum of predicted attr values: "+totalPredicted);
	}

}
