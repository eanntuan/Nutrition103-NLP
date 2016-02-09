

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.jxpath.ri.compiler.Path;
import org.apache.commons.lang.StringUtils;

public class CRF {

	public static void main(String[] args) throws IOException {
		ArrayList<String> tokens = new ArrayList<String>();
		List<String> labels = new ArrayList<String>();
		/*
		tokens.add("I");
		tokens.add("ate");
		tokens.add("cereal");
		labels.add("other");
		labels.add("other");
		labels.add("food");
		*/
		tokens.add("I");
		tokens.add("had");
		tokens.add("a");
		tokens.add("bowl");
		tokens.add("of");
		tokens.add("Kellogg's");
		tokens.add("frosted");
		tokens.add("flakes");
		tokens.add("followed");
		tokens.add("by");
		tokens.add("a");
		tokens.add("banana");
		labels.add("other");
		labels.add("other");
		labels.add("quantity");
		labels.add("quantity");
		labels.add("other");
		labels.add("brand");
		labels.add("brand");
		labels.add("description");
		labels.add("food");
		labels.add("other");
		labels.add("quantity");
		labels.add("food");
		
		CRF.getOutputAll(tokens, labels, null, "IOE");
			
	}
	
	/**
     * Use CRF++ to predict IOB/IOE food chunk labels on given tokens and labels.
     * Writes output list in the same format as the FST.
	 * @throws IOException 
     */
	public static ArrayList<String> getOutputFST(ArrayList<String> tokens, List<String> labels, PrintWriter writerConflicts, String labelRep) throws IOException{
		// use timestamp to make get unique prefix of single_diary.txt
		DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Date time = new java.util.Date();
		String strTime = df.format(time).replaceAll(" ", "_").replaceAll("/", "_").toLowerCase();
		
		// write tokens and labels to single_diary.txt
		File dir = new File("/scratch/CRF++");
		File temp = File.createTempFile(strTime, null, dir);
		PrintWriter writer = new PrintWriter(temp, "UTF-8");
		//PrintWriter writer = new PrintWriter("/scratch/CRF++/"+strTime+"single_diary.txt", "UTF-8"); //temp, "UTF-8");

		for (int i = 0; i < tokens.size(); i++){
			writer.println(tokens.get(i)+"\t"+labels.get(i));
		}
		writer.close();
		
		// call crf_test on food diary to get BIO food chunk labels
		Process p = Runtime.getRuntime().exec("crf_test -m CRF_model_IOE "+temp, null, dir);

		// get output from standard output; convert to FST output format
		String line;
		ArrayList<String> output_FSTformat = new ArrayList<String>();	
		BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String currLabel = "";
		if (writerConflicts!=null){
			writerConflicts.write("\n\n");
		}
		if (labelRep.equals("IOB")) {
	        while ((line = input.readLine()) != null) {
	        	System.out.println(line);
	        	if (writerConflicts!=null){
	            	writerConflicts.write("\n"+line);
	    		}
	        	String[] fields = line.split("\t");
	        	// skip empty lines
	        	if (line.length()==0){
	        		continue;
	        	}
	        	// add '#' if more than one B label
	        	if (output_FSTformat.size()>0 && fields[2].equals("B")){
	        		output_FSTformat.add("#");
	        	}
	        	// add labels of non-Other tokens to output array, avoiding repeats (except for foods)
	    		String newLabel = fields[1].substring(0, 1).toUpperCase();
	        	if (!newLabel.equals("O") && (!newLabel.equals(currLabel) || newLabel.equals("F")) && !fields[2].equals("O")){
	        		output_FSTformat.add(newLabel);
	        	}
	        	currLabel = newLabel;
	        }
		} else if (labelRep.equals("IOE")){
			while ((line = input.readLine()) != null) {
	        	System.out.println(line);
	        	if (writerConflicts!=null){
	            	writerConflicts.write("\n"+line);
	    		}
	        	String[] fields = line.split("\t");
	        	// skip empty lines
	        	if (line.length()==0){
	        		continue;
	        	}
	        	// add labels of non-Other tokens to output array, avoiding repeats (except for foods)
	    		String newLabel = fields[1].substring(0, 1).toUpperCase();
	        	if (!newLabel.equals("O") && (!newLabel.equals(currLabel) || newLabel.equals("F")) && !fields[2].equals("O")){
	        		output_FSTformat.add(newLabel);
	        	}
	        	// add '#' if E label
	        	if (fields[2].equals("E")){
	        		output_FSTformat.add("#");
	        	}
	        	currLabel = newLabel;
	        }
			// remove last label if #
			if (output_FSTformat.get(output_FSTformat.size()-1).equals("#")){
				output_FSTformat.remove(output_FSTformat.size()-1);
			}
		}
        input.close();
        
        // delete single_diary.txt file after use
        try {
        	//File file = new File("/scratch/CRF++/"+strTime+"single_diary.txt");
            //file.delete();
        	temp.delete();

        } catch (Exception e){
            // if any error occurs
            e.printStackTrace();
         }
        System.out.println(output_FSTformat);
        if (writerConflicts!=null){
        	writerConflicts.write("\n"+output_FSTformat);
		}
		return output_FSTformat;
	
	}

	/**
     * Use CRF++ to predict IOB/IOE food chunk labels on given tokens and labels.
     * Writes output list of all the IOB/IOE labels.
	 * @throws IOException 
     */
	public static ArrayList<String> getOutputAll(ArrayList<String> tokens,
			List<String> labels, Object object, String labelRep) throws IOException {
				// use timestamp to make get unique prefix of single_diary.txt
				DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
				Date time = new java.util.Date();
				String strTime = df.format(time).replaceAll(" ", "_").replaceAll("/", "_").toLowerCase();
				
				// write tokens and labels to single_diary.txt
				File dir = new File("/scratch/CRF++");
				System.out.println("file");
				System.out.println(dir);
				File temp = File.createTempFile(strTime, null, dir); // uniqure filename
				PrintWriter writer = new PrintWriter(temp, "UTF-8");
				//PrintWriter writer = new PrintWriter("/scratch/CRF++/"+strTime+"single_diary.txt", "UTF-8"); //temp, "UTF-8");

				for (int i = 0; i < tokens.size(); i++){
					writer.println(tokens.get(i)+"\t"+labels.get(i));
				}
				writer.close();
				
				// call crf_test on food diary to get BIO food chunk labels
				//Process p = Runtime.getRuntime().exec("crf_test -m CRF_model_IOE "+strTime+"single_diary.txt", null, dir);
				Process p = Runtime.getRuntime().exec("/scratch/CRF++/crf_test -m CRF_model_IOE "+temp, null, dir);

				// get output from standard output; convert to FST output format
				String line;
				ArrayList<String> output_FSTformat = new ArrayList<String>();	
				BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			    while ((line = input.readLine()) != null) {
			    	System.out.println(line);
			        String[] fields = line.split("\t");
			        // skip empty lines
			        if (line.length()==0){
			        	continue;
			        }
			        
			        // add labels of non-Other tokens to output array, avoiding repeats (except for foods)
			    	String newLabel = fields[2];
			        output_FSTformat.add(newLabel);
			    }
		        input.close();
		        
		        // delete single_diary.txt file after use
		        try {
		        	//File file = new File("/scratch/CRF++/"+strTime+"single_diary.txt");
		            //file.delete();
		        	temp.delete();

		        } catch (Exception e){
		            // if any error occurs
		            e.printStackTrace();
		         }
		        System.out.println("CRF++ output: "+output_FSTformat);
				return output_FSTformat;
	}

}
