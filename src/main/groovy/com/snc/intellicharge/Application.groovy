package com.snc.intellicharge

import com.snc.intellicharge.data.DataFetcher
import com.snc.intellicharge.data.DataScientist
import com.snc.intellicharge.insight.RealtimeInsightGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application {

    @Autowired
    RealtimeInsightGenerator fRealtimeInsightGenerator

    @Autowired
    DataFetcher fDataFetcher

    @Autowired
    DataScientist fDataScientist

    static void main(String[] args) {
        SpringApplication.run(Application.class, args)
    }

    @Bean
    CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return {
            fDataFetcher.start()
            fRealtimeInsightGenerator.start()
            fRealtimeInsightGenerator.startMl()
        }


//            RandomForestClassifier rf = new RandomForestClassifier()
//            println "maxBins: ${rf.getMaxBins()}; maxDepth: ${rf.getMaxDepth()}; numTrees: ${rf.getNumTrees()}"

/*
            println 'Let\'s inspect the beans provided by Spring Boot:'

            String[] beanNames = ctx.getBeanDefinitionNames()
            Arrays.sort(beanNames)
            for (String beanName : beanNames) {
                println beanName
            }
*/
//            new ChargePointOutletStatusClassifier().classify(null)

        /*
        SparkSession spark = SparkSession

                .builder()

                .appName("SparkWithSpring")
                .master("local")

                .getOrCreate();

        System.out.println("Spark Version: " + spark.version());

        // $example on$
        // Load training data
        Dataset<Row> dataFrame =
                spark.read().format("libsvm").load("data/mllib/sample_libsvm_data.txt");
        // Split the data into train and test
        Dataset<Row>[] splits = dataFrame.randomSplit([0.6d, 0.4d] as double[], 1234L);
        Dataset<Row> train = splits[0];
        Dataset<Row> test = splits[1];

        // create the trainer and set its parameters
        NaiveBayes nb = new NaiveBayes();

        // train the model
        NaiveBayesModel model = nb.fit(train);

        // Select example rows to display.
        Dataset<Row> predictions = model.transform(test);
        predictions.show();

        // compute accuracy on the test set
        MulticlassClassificationEvaluator evaluator = new MulticlassClassificationEvaluator()
                .setLabelCol("label")
                .setPredictionCol("prediction")
                .setMetricName("accuracy");
        double accuracy = evaluator.evaluate(predictions);
        System.out.println("Test set accuracy = " + accuracy);
        // $example off$

        spark.stop();
        */

    }
}
