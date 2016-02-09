//package edu.mit.csail.sls.nut;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.mit.csail.asgard.syntax.CRFSegment;
import edu.mit.csail.asgard.syntax.CRFToken;
import edu.mit.csail.asgard.syntax.Sentence;


public class NLPData {
	@JsonProperty
	public String text;
	@JsonProperty
	public
	ArrayList<String> tokens;
	@JsonProperty
	public List<String> labels;
	@JsonProperty
	public String[] tags;
	@JsonProperty
	public ArrayList<Segment> segments;
	@JsonProperty
	public String parse;
	@JsonProperty
	public Object[] deps;
	@JsonProperty
	public ArrayList<String> foods;
	@JsonProperty
	public
	Map<String, ArrayList<Segment>> attributes;
	
	NLPData(Sentence sentence){
		//crfTokens = sentence.tokens;
		labels = new ArrayList<String>();
		for (CRFToken token : sentence.tokens) {
			//System.out.println("Token: "+token+" "+token.crfClass.getName());
			labels.add(token.crfClass.getName().toLowerCase());
		}
		tags = sentence.tags;
		parse = sentence.parse;
		text = sentence.getRawString(" ");
		String[] words = sentence.originalText.split(" ");
		deps = sentence.deps;
		//attributes = sentence.attributes;
		tokens = new ArrayList<String>();
		segments = new ArrayList<Segment>();
		Segment prevSeg = null;
		for(CRFSegment crfSegment : sentence.segments){
			Segment segment = new Segment();
			segment.label = crfSegment.crfClass.getName();
			segment.start = tokens.size();

			for(CRFToken crfToken : crfSegment.tokens){
				/*
				// if token contains apostrophe or starts with %, add to prevToken
				if (crfToken.text.contains("\'") || crfToken.text.charAt(0)=='%') {
					String oldToken = tokens.get(tokens.size()-1);
					String newToken = oldToken+crfToken.text;
					tokens.remove(tokens.size()-1);
					tokens.add(newToken);
				}else {
				*/
					tokens.add(crfToken.text);
				//}
				
			}
			segment.end = tokens.size();
			if (segment.end > segment.start) {
				segments.add(segment);
			}
			prevSeg = segment;
		}
	}
}
