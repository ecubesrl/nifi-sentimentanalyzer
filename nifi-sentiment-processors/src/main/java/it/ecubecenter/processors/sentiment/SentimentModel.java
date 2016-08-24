package it.ecubecenter.processors.sentiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;


public class SentimentModel {
private static SentimentModel instance = null;
	
	public final static int VERY_NEGATIVE=0;
	public final static int NEGATIVE=1;
	public final static int NEUTRAL=2;
	public final static int POSITIVE=3;
	public final static int VERY_POSITIVE=4;
	
	
	private StanfordCoreNLP tokenizer;
	private StanfordCoreNLP pipeline;
	
	public static synchronized SentimentModel getInstance(){
		if(instance == null){
			instance = new SentimentModel();
		}
		return instance;
	}
	
	private SentimentModel() {
		Properties pipelineProps = new Properties();
		Properties tokenizerProps = new Properties();
	    
	    pipelineProps.setProperty("annotators", "parse, sentiment");
	    pipelineProps.setProperty("enforceRequirements", "false");
	    tokenizerProps.setProperty("annotators", "tokenize, ssplit");
	    
	    tokenizer =  new StanfordCoreNLP(tokenizerProps);
	    pipeline = new StanfordCoreNLP(pipelineProps);
	}
	
	public List<double[]> getSentencesSentiment(String tweet){
		ArrayList<double[]> res = new ArrayList<>();
		Annotation annotation = tokenizer.process(tweet);
	    pipeline.annotate(annotation);
	     
	    for(CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)){
	    	SimpleMatrix prediction = RNNCoreAnnotations.getPredictions(
	    			sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class));
	    	double[] predArray = new double[5];
	    	predArray[VERY_NEGATIVE]=prediction.get(VERY_NEGATIVE);
	    	predArray[NEGATIVE]=prediction.get(NEGATIVE);
	    	predArray[NEUTRAL]=prediction.get(NEUTRAL);
	    	predArray[POSITIVE]=prediction.get(POSITIVE);
	    	predArray[VERY_POSITIVE]=prediction.get(VERY_POSITIVE);
	    	res.add(predArray);
	    }
	    
	    return res;
	}
	
	public static double[] getOverallSentimentScores(List<double[]> predictions){
		double[] overall = new double[5];
		
		for (double[] p : predictions){
			overall[VERY_NEGATIVE]+=p[VERY_NEGATIVE];
			overall[NEGATIVE]+=p[NEGATIVE];
			overall[NEUTRAL]+=p[NEUTRAL];
			overall[POSITIVE]+=p[POSITIVE];
			overall[VERY_POSITIVE]+=p[VERY_POSITIVE];
		}
		overall[VERY_NEGATIVE]/=predictions.size();
		overall[NEGATIVE]/=predictions.size();
		overall[NEUTRAL]/=predictions.size();
		overall[POSITIVE]/=predictions.size();
		overall[VERY_POSITIVE]/=predictions.size();
		return overall;
	}
	
	public static String getOverallSentiment(List<double[]> predictions){
		double[] overall = getOverallSentimentScores(predictions);
		int maxIndex = 0;
		for (int i = 0; i < overall.length; i++) {
			if(overall[i]>overall[maxIndex]){
				maxIndex=i;
			}
		}
		return convertSentimentIndexToString(maxIndex);
	}
	
	public static String convertSentimentIndexToString(int sentiment){
		switch(sentiment){
			case VERY_NEGATIVE:
				return "Very Negative";
			case NEGATIVE:
				return "Negative";
			case NEUTRAL:
				return "Neutral";
			case POSITIVE:
				return "Positive";
			case VERY_POSITIVE:
				return "Very Positive";
		}
		throw new IllegalArgumentException("Value " + sentiment + " is not a valid santiment index.");
	}
}
