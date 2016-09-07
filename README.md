# Apache NiFi Sentiment Analyzer processor

The repo contains the implementation of a custom NiFi processor performing the sentiment analysis. 
It uses the Stanford CoreNLP library (http://nlp.stanford.edu/sentiment/index.html).
This means that the only available language for the sentiment analysis, at the moment, is English and the quality
of the results is the one of the library.

## Installing the processor

The processor requires Java 1.8. You can download directly the built *.nar* file
or you can download the code and compile it (this requires Maven and Git to be installed).

The following subsection assumes that you want to build the processor from the code on your own.
If you have downloaded the *.nar* file from the realease section of the repo, you can skip it.

### Downloading and building the processor

The first step needed is to install Maven:

```
curl -o /etc/yum.repos.d/epel-apache-maven.repo https://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo
yum -y install apache-maven
```

This dowloads the maven repository definition and install it via `yum`. The `-y` option
automatically gives permissions.

Then, you have to download the code from the master branch of the repo and compile it

```
cd /opt
git clone https://github.com/ecubesrl/nifi-sentimentanalyzer.git
cd /opt/nifi-sentimentanalyzer/
mvn clean package
```

At the end of maven compilation you find the generated *.nar* in `/opt/nifi-sentimentanalyzer/nifi-sentiment-nar/target/`.

### Adding the processor to NiFi (or HDF)

Once you have the *.nar* file, you have to add it to the `lib` folder of you NiFi installation. It
is in the NiFi *HOME* folder. For instance, in a HDP distribution, NiFi is actually called HDF
and the folder is `/opt/HDF-1.2.0.1-1/nifi/lib/`. In this case, you need to do:

```
cp /opt/nifi-sentimentanalyzer/nifi-sentiment-nar/target/nifisentiment-nar-*.nar /opt/HDF-1.2.0.1-1/nifi/lib/
```

And you have to restart NiFi:

```
/opt/HDF-1.2.0.1-1/nifi/bin/nifi.sh start
```

In NiFi log file you should see the new processor in the lost of the loaded ones and you you should find the processor
in NiFi management web UI, as you can see in the figure below.

![Sentiment Analyzer processor](https://github.com/ecubesrl/nifi-sentimentanalyzer/raw/master/images/NiFi_insert_sentiment_processors.PNG "Sentiment Analyzer processor")

## Using the processor

The processor is rather easy to be configured. It has only two properties: **Language** and **Attribute to analyze**. 
But only the latter is actually configurable, since the only available language at the moment is English ("en").
*Attribute to analyze* can contain the name of the attribute of the incoming flow file to be analyzed or it can be left 
empty (in this case the content of the flow file will be analyzed). 
The example in the figure will perform the sentiment analysis on the content of the attribute **twitter.msg**. 

![Configuring the Sentiment Analyzer processor](https://github.com/ecubesrl/nifi-sentimentanalyzer/raw/master/images/NiFi_properties_sentiment_processors.PNG "Configuring the Sentiment Analyzer processor")

The result of the sentiment analysis is available in two new attributes which are added to the outgoing flow file. 
One is `X.sentiment.category` - where `X` is the name of the input attribute - and contains the label of 
the overall sentiment (which can be "Very Negative", "Negative", "Neutral", "Positive" and "Very Positive").
The other is `X.sentiment.sentences.scores` which contains a JSON string with the detailed scores for each 
sentence in the text to be analyzed.
