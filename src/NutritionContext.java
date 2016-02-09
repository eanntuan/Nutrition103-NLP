

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.commons.configuration.Configuration;

import edu.mit.csail.asgard.syntax.Feature;
import edu.mit.csail.asgard.syntax.Features;
import edu.mit.csail.asgard.syntax.SentenceSegment;
import edu.mit.csail.asgard.syntax.SentenceTagger;
import edu.mit.csail.asgard.util.Configure;

public class NutritionContext {
	static final String[] namePath = {"Nutrition", "Asgard", ""};
	static final String domain = "Nutrition103-NLP";
	//static final String confFile = "conf/eng-fix-SL-NG";
	static final String confFile = "conf/semlab";

	static ServletContext servletContext;
	static Configuration configuration;
	static File crfBase;
	static Feature segmenter;
	public static SentenceTagger sentenceTagger;
	static ScheduledExecutorService executor;
	
	static String nutritionixAppID = null;
	static String nutritionixAppKey = null;
	
	static InputStream getResourceAsStream(String path){
		//System.out.println("path: " + path);
		return servletContext.getResourceAsStream(path);
	}

	static void initialize(ServletContextEvent servletContextEvent){
		servletContext = servletContextEvent.getServletContext();
    	Configure.setServletContext(servletContext);

    	ArrayList<String> names = new ArrayList<String>(namePath.length+2);
    	names.add(Configure.getContextName());
    	names.add(domain);
    	for(String name : namePath)
    		names.add(name);
    	configuration = Configure.getConfiguration(names);
		configuration.setProperty("domain", domain);
		
		nutritionixAppID = configuration.getString("/Nutritionix/@appID");
		nutritionixAppKey = configuration.getString("/Nutritionix/@appKey");
		executor = Executors.newScheduledThreadPool(10);
		
		System.err.format("ID=%s KEY=%s%n", nutritionixAppID, nutritionixAppKey);
		
		crfBase = new File("/WEB-INF/CRF/samples");
		iitb.CRF.Util
		.setInputStreamFactory(new iitb.CRF.Util.InputStreamFactory() {

			@Override
			public InputStream fileInputStream(File relfile)
					throws IOException {
				return servletContext.getResourceAsStream(relfile.getPath());
			}
		});

		try {
			Features.initializeFeatureComputers(configuration.getString("/tagger/@url"), true);
			segmenter = Features.whitespace_tokenizer;
			sentenceTagger = new SentenceSegment(crfBase.getPath(), new File(crfBase, confFile).toString());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static String getNutritionixAppID() {
		return nutritionixAppID;
	}

	public static String getNutritionixAppKey() {
		return nutritionixAppKey;
	}
}
