/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.ecubecenter.processors.sentiment;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.*;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SideEffectFree
@Tags({"sentiment","analysis","text"})
@CapabilityDescription("This processor performs a sentiment analysis on the attribute specified (or the content of the Flow File if "+
		"no attribute is provided). The result of the analysis is returned in the attributes X.sentiment.category and X.sentiment.sentences.scores, "+
		"where X is the name of the attribute to be analyzed.")
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({
	@WritesAttribute(attribute="X.sentiment.category", description="The overall sentiment category of the text. "+
					"It can be \"Very Negative\", \"Negative\", \"Neutral\", \"Positive\" and \"Very Positive\"."),
	@WritesAttribute(attribute="X.sentiment.sentences.scores", description="The detailed scores for each sentence in the text to be analyzed.")})
/**
 * This processor performs a sentiment analysis on the attribute specified (or the content of the Flow File if 
 * no attribute is provided). The result of the analysis is returned in the attributes X.sentiment.category and X.sentiment.sentences.scores,
 * where X is the name of the attribute to be analyzed
 * @author gaido@ecubecenter.it
 * 
 */
public class SentimentAnalyzer extends AbstractProcessor {

    public static final PropertyDescriptor LANGUAGE_PROPERTY = new PropertyDescriptor
            .Builder().name("Language")
            .description("The language of the content to be analyzed (as of now only \"en\", i.e. Engligh, is available).")
            .required(true)
            .defaultValue("en")
            .addValidator(new Validator(){

				@Override
				public ValidationResult validate(final String subject, final String value, final ValidationContext context) {
					return new ValidationResult.Builder()
							.subject(subject)
							.input(value)
							.valid(value != null && value.equals("en"))
							.explanation(subject + " can be only \"en\".")
							.build();
				}
            	
            })
            .build();
    
    public static final PropertyDescriptor ATTRIBUTE_TO_ANALYZE_PROPERTY = new PropertyDescriptor
            .Builder().name("Attribute to analyze")
            .description("The attribute to analyze for the sentiment analysis. If it is empty it will use the content of the flow file.")
            .required(true)
            .defaultValue("")
            .addValidator(new Validator(){

				@Override
				public ValidationResult validate(final String subject, final String value, final ValidationContext context) {
					return new ValidationResult.Builder()
							.subject(subject)
							.input(value)
							.valid(true)
							.explanation("Any value is allowed, but it should be empty or contain a valid attribute name.")
							.build();
				}
            	
            })
            .build();

    public static final Relationship SUCCESS_RELATIONSHIP = new Relationship.Builder()
            .name("SUCCESS")
            .description("Output relationship containing the result of the sentiment analysis.")
            .build();
    public static final Relationship FAILURE_RELATIONSHIP = new Relationship.Builder()
            .name("FAILURE")
            .description("Output relationship if a failure occours, e.g. the attribute specified doesn't exitst or it is empty.")
            .build();
    

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(LANGUAGE_PROPERTY);
        descriptors.add(ATTRIBUTE_TO_ANALYZE_PROPERTY);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(SUCCESS_RELATIONSHIP);
        relationships.add(FAILURE_RELATIONSHIP);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {

    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
    	final ProcessorLog log = this.getLogger();
    	final AtomicReference<String> atomicStringToAnalyze = new AtomicReference<>();
    	
        FlowFile flowFile = session.get();
        if ( flowFile == null ) {
            return;
        }
        String attributeToBeUsed = context.getProperty(ATTRIBUTE_TO_ANALYZE_PROPERTY).getValue();
        if(attributeToBeUsed == null || attributeToBeUsed.equals("")){
        	attributeToBeUsed="";
        	log.info("Start reading the flow file content in order to perform the sentiment analysis.");
        	session.read(flowFile, new InputStreamCallback(){

				@Override
				public void process(InputStream in) throws IOException {
					atomicStringToAnalyze.set(IOUtils.toString(in));
				}});
        }else{
        	log.info("Getting the content of attribute " + attributeToBeUsed + "in order to perform the sentiment analysis." );
        	atomicStringToAnalyze.set(flowFile.getAttribute(attributeToBeUsed));
        }
        String stringToAnalyze = atomicStringToAnalyze.get();
        if(stringToAnalyze == null || stringToAnalyze.equals("")){
        	log.warn("The attribut to be analyzed doesn't exist or it is empty.");
        	session.transfer(flowFile, FAILURE_RELATIONSHIP);
        	return;
        }
        
        SentimentModel model = SentimentModel.getInstance();
        
        List<double[]> sentiments = model.getSentencesSentiment(stringToAnalyze);
        flowFile=session.putAttribute(flowFile, new StringBuilder(attributeToBeUsed).append(".sentiment.category").toString(), SentimentModel.getOverallSentiment(sentiments));
        flowFile=session.putAttribute(flowFile, new StringBuilder(attributeToBeUsed).append(".sentiment.sentences.scores").toString(), stringifyListOfSentiments(sentiments));
        
        session.transfer(flowFile, SUCCESS_RELATIONSHIP);
    }
    
    
    private static String stringifyListOfSentiments(List<double[]> sentiments){
    	StringBuilder sb=new StringBuilder("[");
    	Iterator<double[]> it = sentiments.iterator();
    	while(it.hasNext()){
    		double[] sent = it.next();
    		sb.append("{\"Very Negative\":").append(sent[SentimentModel.VERY_NEGATIVE]).append(",");
    		sb.append("\"Negative\":").append(sent[SentimentModel.NEGATIVE]).append(",");
    		sb.append("\"Neutral\":").append(sent[SentimentModel.NEUTRAL]).append(",");
    		sb.append("\"Positive\":").append(sent[SentimentModel.POSITIVE]).append(",");
    		sb.append("\"Very Positive\":").append(sent[SentimentModel.VERY_POSITIVE]).append("},");
    	}
    	sb.setCharAt(sb.length()-1,']');
    	return sb.toString();
    }
}
