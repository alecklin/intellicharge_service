package com.snc.intellicharge.ml

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.apache.spark.ml.classification.NaiveBayes
import org.apache.spark.ml.classification.NaiveBayesModel
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.linalg.DenseVector
import org.apache.spark.ml.linalg.SparseVector
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import scala.collection.Seq

@Slf4j
@Canonical
//@InheritConstructors
class ChargePointOutletStatusClassifier {
    SparkSession spark
    NaiveBayesModel model

    ChargePointOutletStatusClassifier() {
        this.spark = SparkSession

                .builder()

                .appName("SparkWithSpring")
                .master("local")

                .getOrCreate();

        System.out.println("Spark Version: " + spark.version());
    }

    void train(File libsvmFile) {

        // $example on$
        // Load training data
        Dataset<Row> dataFrame =
                spark.read().format("libsvm").load(libsvmFile.getPath());
        // Split the data into train and test
//        Dataset<Row>[] splits = dataFrame.randomSplit([0.95d, 0.05d] as double[], 1234L);
//        Dataset<Row> train = splits[0];
//        Dataset<Row> test = splits[1];

        // create the trainer and set its parameters
        NaiveBayes nb = new NaiveBayes();
        nb.setSmoothing(0.5d)

        // train the model
        model = nb.fit(dataFrame);
        println "-------- SMOOTHING: ${model.getSmoothing()}"

        // compute accuracy on the test set
/*
        MulticlassClassificationEvaluator evaluator = new MulticlassClassificationEvaluator()
                .setLabelCol("label")
                .setPredictionCol("prediction")
                .setMetricName("accuracy");
        double accuracy = evaluator.evaluate(predictions);
        System.out.println("Test set accuracy = " + accuracy);
        // $example off$
*/
        //spark.stop();

    }

    void predict(File queryLibSVMFile) {
        Dataset<Row> queryDataFrame =
                spark.read().format("libsvm").load(queryLibSVMFile.getPath());

        // Select example rows to display.
        Dataset<Row> predictions = model.transform(queryDataFrame);
        predictions.collectAsList().each { Row row ->
            // for each row

            print "PREDICTION: ${row.getDouble(4)}"
            print '; '
            print "PROB: "
            (row.get(3) as DenseVector).toArray().each {
                print "$it; "
            }
            print "FEATURE: ${(row.get(1) as SparseVector).toString()}"
            println ''
        }
        predictions.show()
    }
}
