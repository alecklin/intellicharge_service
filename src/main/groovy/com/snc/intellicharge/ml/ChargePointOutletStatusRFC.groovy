package com.snc.intellicharge.ml

import org.apache.spark.ml.classification.RandomForestClassificationModel
import org.apache.spark.ml.classification.RandomForestClassifier
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.springframework.beans.factory.annotation.Autowired

class ChargePointOutletStatusRFC {
//    String deviceId, outletId

    @Autowired
    SparkSession spark

    RandomForestClassificationModel model

    ChargePointOutletStatusRFC() {
    }

    void train(File libsvmFile) {
        println "ChargePointOutletStatusRFC.train; starting training from svm file: ${libsvmFile.getPath()}"
        long startTime = System.currentTimeMillis()
        // $example on$
        // Load training data
        Dataset<Row> dataFrame =
                spark.read().format("libsvm").load(libsvmFile.getPath());

        // Split the data into train and test
//        Dataset<Row>[] splits = dataFrame.randomSplit([0.95d, 0.05d] as double[], 1234L);
//        Dataset<Row> train = splits[0];
//        Dataset<Row> test = splits[1];

        // Train a RandomForest model.
        RandomForestClassifier rf = new RandomForestClassifier()
        rf.setMaxBins(128)
        rf.setMaxDepth(30)
        rf.setNumTrees(100)
        println "maxBins: ${rf.getMaxBins()}; maxDepth: ${rf.getMaxDepth()}; numTrees: ${rf.getNumTrees()}"
        model = rf.fit(dataFrame)
        long endTime = System.currentTimeMillis()
        println "ChargePointOutletStatusRFC.train; RFC Training took ${(endTime - startTime)/1000} seconds"
    }

    void predict(File queryLibSVMFile, File predictedFile) {
        println "ChargePointOutletStatusRFC.predict; queryLibSVMFile: ${queryLibSVMFile.getPath()}; predictedFile: ${predictedFile.getPath()}"
        long startTime = System.currentTimeMillis()
        Dataset<Row> queryDataFrame =
                spark.read().format("libsvm").load(queryLibSVMFile.getPath());

        // Select example rows to display.
        Dataset<Row> predictions = model.transform(queryDataFrame)
        predictions.write().format('json').save(predictedFile.getPath())
        long endTime = System.currentTimeMillis()
        println "ChargePointOutletStatusRFC.predict; RFC Prediction took ${(endTime - startTime)/1000} seconds"
//        predictions.collectAsList().collect { Row row ->
//
//            print "OUTLET: ${deviceId}_${outletId}"
//            print '; '
//            print "PREDICTION: ${row.getDouble(4)}"
//            print '; '
//            print "PROB: "
//            (row.get(3) as DenseVector).toArray().each {
//                print "$it; "
//            }
//            print "FEATURE: ${(row.get(1) as SparseVector).toString()}"
//            println ''
//
//            // for each row
//            def probs = (row.get(3) as DenseVector).toArray()
//
//            def prediction = [:]
//            prediction.deviceId = deviceId
//            prediction.outletId = outletId
//            prediction.outletFQId = "${deviceId}_${outletId}"
//            prediction.predictedClass = row.getDouble(4)
//            prediction.predictedClassLabel = ChargePointModelManager.OUTLET_STATUS_LABEL_MAP[prediction.predictedClass]
//
//            prediction.prob_unknown = 0d
//            prediction.prob_available = 0d
//            prediction.prob_in_use = 0d
//
//            prediction."prob_${prediction.predictedClassLabel}" = probs[prediction.predictedClass as Integer]
//
//            if (probs.length > 0) {
//                prediction.prob_unknown = probs[0]
//                if (probs.length > 1) {
//                    prediction.prob_available = probs[1]
//                    if (probs.length > 2) {
//                        prediction.prob_in_use = probs[2]
//                    }
//                }
//            }
//
//
//            double[] featureVals = (row.get(1) as SparseVector).toArray()
//            double dayOfWeek = featureVals[0]
//            double hourOfDay = featureVals[1]
//            double minOfHour = featureVals[2]
//
//            prediction.dayOfWeek = dayOfWeek
//            prediction.hourOfDay = hourOfDay
//            prediction.minOfHour = minOfHour
//
//            prediction
//        }
//        predictions.show()
    }
}

