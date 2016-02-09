

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.mit.csail.asgard.syntax.CRFToken;
import edu.mit.csail.asgard.syntax.Features;
import edu.mit.csail.asgard.syntax.Sentence;
import edu.mit.csail.asgard.syntax.SentenceTagger;

public class GetAttributesCRF {
	static boolean useFST = false;
	
	public static void main(String[] args) throws IOException {
		SentenceTagger sentenceTagger = Eval.initialize();
		Sentence sentence = new Sentence();
		//String text = "I had 4 ounces of blueberry yogurt, a blueberry bagel with butter, and an energy drink.";
		//String text = "I had a bowl of Kellogg's frosted flakes followed by a banana";
		//String text = "I had a bowl of Kellogg's frosted mini wheats followed by a banana and a glass of milk";
		//String text = "I had organic guacamole from Shaws and a bag of chips";
		//String text = "I had a plate of spaghetti and meatballs and a side salad with feta cheese";
		//String text = "I had a huge stack of pancakes smothered in butter and syrup";
		String text = "I had two dozen eggs and some toast with Smucker's strawberry jam on top";
		sentence.originalText = text;
		sentence.isNutrition = true;
		Features.nlparser_pos_english.compute(sentence, text);
		sentenceTagger.addCRFClasses(sentence);
		NLPData segmentation = new NLPData(sentence);
		segmentation.parse = sentence.parse;
		segmentation.deps = sentence.deps;
		//ArrayList<String> CRFout = CRF.getOutputAll(segmentation.tokens, segmentation.labels, null, "IOE");
		
		ArrayList<CRFToken> foodItems = new ArrayList<>();
		for(CRFToken token : sentence.tokens){
        	if (token.crfClass!=null) {
        		// check if food and add to food list
        		if (token.crfClass.toString().contains("Food")) {
        			foodItems.add(token);
        			ArrayList<Segment> attrList = new ArrayList<Segment>();
        		} 
        	}
        }
		Map<String, ArrayList<Segment>> segmentDeps = getAttributeDeps(sentence, segmentation, foodItems, "IOE");
		for (String food: segmentDeps.keySet()){
			System.out.println("Food: "+food);
			for (Segment seg : segmentDeps.get(food)){
				System.out.println("attribute: "+seg.start+" "+seg.end+" "+seg.label);
			}
		}
	}

	/**
     * Get all food-attribute associations using CRF++.
     * Returns Map<String (token), ArrayList<Segment>> (attribute segments)>
	 * @param sentence 
	 * @throws IOException 
     */
	static Map<String, ArrayList<Segment>> getAttributeDeps(Sentence sentence, NLPData segmentation, ArrayList<CRFToken> foodItems, String labelRep) throws IOException {		
		
		Map<String, ArrayList<Segment>> segmentDeps = Tag.initializeSegmentDeps(foodItems);
		
		ArrayList<String> CRFout = new ArrayList<String>();
		ArrayList<Segment> segs = new ArrayList<Segment>();
		
		// get CRF++ output in either FST format or a list of all BIO/IOE labels

		System.out.println("segmentation tokens: "+segmentation.tokens);
		CRFout = CRF.getOutputAll(segmentation.tokens, segmentation.labels, null, labelRep);
		// TODO: manually ensure foods are not labeled O
		CRFout = assignFoodsLabels(CRFout, segmentation.labels);
	
		System.out.println("segmentation labels: "+segmentation.labels);
		String[] CRFoutList = new String[CRFout.size()];
		CRFoutList = CRFout.toArray(CRFoutList);

		// turn CRF++ output into attributes
		
		segmentDeps = getAttributesFromCRF(CRFoutList, segmentDeps, segmentation);
		
		return segmentDeps;
	}

	private static ArrayList<String> assignFoodsLabels(ArrayList<String> cRFout, List<String> labels) {
		int i = 0;
		for (String IOElabel : cRFout){
			// change every "O" that was assigned to a food to "I"
			if (labels.get(i).equals("food") & IOElabel.equals("O")){
				cRFout.set(i, "I");
			}
			i++;
		}
		return cRFout;
	}

	// assumes labels are in IOE format
	// TODO: generalize to IOB and IOBES formats
	private static Map<String, ArrayList<Segment>> getAttributesFromCRF(
			String[] CRFout, Map<String, ArrayList<Segment>> segmentDeps, NLPData segmentation) {
				// use CRF output to assign attributes to foods in a segment
				segmentDeps = new HashMap<>();
				ArrayList<Segment> segMatches = new ArrayList<Segment>();

				String food = "";
				String label = "";
				int index = 0; // current token index
				while(index < CRFout.length){
					label = CRFout[index];
					System.out.println(index+" "+label);
					if (label.equals("E")){
						int currIndex = index;
						String currLabel = segmentation.labels.get(index);
						System.out.println("currLabel "+currLabel);
						Segment segMatch = newSegMatch(currLabel, index);
						
						// add new food
						if (currLabel.equals("food")){
							index = addFood(label, CRFout, index, currLabel, food, segmentation,  segmentDeps, segMatch, segMatches);
						} else if (!currLabel.equals("other")){
							// add new attribute segment
							index = updateSegMatches(label, CRFout, index, currLabel, segmentation, segMatch, segMatches);
						} 
						
						// if current token not food, add previously saved food
						if (!currLabel.equals("food")){
							if (!food.equals("")){
								// add segMatches to segmentDeps for saved food
								System.out.println("add food to segmentDeps "+food);
								segmentDeps.put(food, segMatches);
								index++;
							} 
							/*
							else if (segMatches.size()>0){
								System.out.println("no matching food");
								// if no matching food, use previous token
								food = segmentation.tokens.get(currIndex)+currIndex;
								System.out.println("add food to segmentDeps "+food);
								segmentDeps.put(food, segMatches);
								if (index==currIndex){
									index++;
								}
							} 
							*/
							else {
								index++;
							}
						}
						// re-initialize segMatches and food
						segMatches = new ArrayList<Segment>();
						food="";
					} else if (label.equals("I")) {
						// add corresponding segment to list
						String currLabel = segmentation.labels.get(index);
						Segment segMatch = newSegMatch(currLabel, index);
						// skip if label is other
						if (segmentation.labels.get(index).equals("other")){
							System.out.println("label is other");
							index++;
							continue;
							
						} else if (currLabel.equals("food")){
							index = addFood(label, CRFout, index, currLabel, food, segmentation, segmentDeps, segMatch, segMatches);
							
						} else {
							index = updateSegMatches(label, CRFout, index, currLabel, segmentation, segMatch, segMatches);
						}
					} else {
						index++;
					}
				}
				// add final segMatches to segmentDeps
				if (!food.equals("")){
					System.out.println("add food to segmentDeps "+food);
					segmentDeps.put(food, segMatches);
				}
				return segmentDeps;
	}
	
	private static Segment newSegMatch(String currLabel, int index) {
		// create new segment
		Segment segMatch = new Segment();
		segMatch.start = index;
		segMatch.end = index+1;
		segMatch.label = currLabel.substring(0, 1).toUpperCase()+currLabel.substring(1, currLabel.length());	
		return segMatch;
	}

	public static int updateSegMatches(String label, String[] CRFout, int index, String currLabel, NLPData segmentation, Segment segMatch, ArrayList<Segment> segMatches){

		// get entire attribute segment, add to segMatches, update index
		System.out.println("label is "+currLabel);
		String prevLabel = currLabel;
		// if current IOE label is not E, then don't allow to skip past an E
		while (currLabel!=null && (label.equals("E") || !CRFout[index].equals("E")) && currLabel.equals(prevLabel)){
			index ++;
			if (index < segmentation.labels.size()){
				currLabel = segmentation.labels.get(index);
			} else {
				currLabel = null;
			}
			segMatch.end = index;
		}
		segMatches.add(segMatch);
		System.out.println("attr segment: "+segMatch.label+" "+segMatch.start+" "+segMatch.end);
		return index;
	
	}
	
	public static int addFood(String label, String[] CRFout, int index, String currLabel, String food, NLPData segmentation, Map<String, ArrayList<Segment>> segmentDeps, Segment segMatch, ArrayList<Segment> segMatches){
		// get entire food segment, update index
		food = "";
		// if current IOE label is not E, then don't allow to skip past an E
		while (currLabel!=null && (label.equals("E") || !CRFout[index].equals("E")) && currLabel.equals("food")){
			food += " "+segmentation.tokens.get(index);
			index ++;
			if (index < segmentation.labels.size()){
				currLabel = segmentation.labels.get(index);
			} else {
				currLabel = null;
			}
			segMatch.end = index;
		}
		// remove initial space
		food = food.substring(1);
		food += segMatch.start;							
		System.out.println("add food to segmentDeps "+food);
		segmentDeps.put(food, segMatches);
		return index;
	}

}
