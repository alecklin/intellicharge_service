package com.snc.intellicharge.ml

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.stereotype.Service

@Canonical
//@InheritConstructors
@Service
class ChargePointModelManager {

    @Autowired
    SparkSession spark

    private @Autowired AutowireCapableBeanFactory beanFactory;

    @Value('${minToCondenseBy:2}')
    int minToCondenseBy

    @Value('${minAvailCountForWindowStart:2}')
    int minAvailCountForWindowStart

    @Value('${minAvailCountForWindowEnd:2}')
    int minAvailCountForWindowEnd

    @Value('${minTimeIntervalCountForWindow:2}')
    int minTimeIntervalCountForWindow

    /*
    insight.morning.maxEndTime=9:30
insight.noon.maxStartTime=11:00
insight.noon.minDurationMinutes=20
insight.afternoon.minStartTime=15:30
     */

    int morningMaxEndTimeHour
    int morningMaxEndTimeMin
    int noonMaxStartTimeHour
    int noonMaxStartTimeMin
    int noonMinDurationMinutes
    int afternoonMinStartTimeHour
    int afternoonMinStartTimeMin

    @Value('${insight.morning.maxEndTime}')
    void setMorningMaxEndTime(String morningMaxEndTime) {
        def (int hour, min) = morningMaxEndTime.split(':').collect({
            Integer.parseInt(it)
        })
        morningMaxEndTimeHour = hour
        morningMaxEndTimeMin = min
    }

    @Value('${insight.noon.maxStartTime}')
    void setNoonMaxStartTime(String noonMaxStartTime) {
        def (int hour, min) = noonMaxStartTime.split(':').collect({
            Integer.parseInt(it)
        })
        noonMaxStartTimeHour = hour
        noonMaxStartTimeMin = min
    }

    @Value('${insight.noon.minDurationMinutes}')
    void setNoonMinDurationMinutes(String noonMinDurationMinutesStr) {
        noonMinDurationMinutes = Integer.parseInt(noonMinDurationMinutesStr)
    }

    @Value('${insight.afternoon.minStartTime}')
    void setAfternoonMinStartTime(String afternoonMinStartTime) {
        def (int hour, min) = afternoonMinStartTime.split(':').collect({
            Integer.parseInt(it)
        })
        afternoonMinStartTimeHour = hour
        afternoonMinStartTimeMin = min
    }

    ChargePointOutletStatusRFC availCountClassifier

    // 121403	121467	121475	121495	121503	121509	121551	123179 121407 121469	121485	121501	121505	121513	122601	123203
    Collection<String> managedDeviceIds = ['121403', '121467', '121475', '121495', '121503', '121509', '121551', '123179', '121407', '121469', '121485', '121501', '121505', '121513', '122601', '123203']
//    Collection<String> managedDeviceIds = ['121403', '121467']
    Collection<String> outletIds = ['outlet_1', 'outlet_2']
//    Collection<String> outletIds = ['outlet_2']
    static Map<Double, String> OUTLET_STATUS_LABEL_MAP = [0d: 'unknown', 1d: 'available', 2d: 'in_use']
    Map<String, Collection<ChargePointOutletStatusRFC>> deviceIdToModelMap = [:]
    Map<String, ChargePointOutletStatusRFC> outletFQIdModelMap = [:]

    void registerModel(String deviceId, String outletId, ChargePointOutletStatusRFC classifier) {
        String outletFQId = "${deviceId}_${outletId}"
        def classifiers = deviceIdToModelMap[deviceId]
        if (!classifiers) {
            classifiers = []
            deviceIdToModelMap[deviceId] = []
        }
        classifiers << classifier
        outletFQIdModelMap[outletFQId] = classifier
    }

    Dataset<Row> loadSVMJson(File svmFile) {
        spark.read().format("json").load(svmFile.getPath())
    }

    ChargePointOutletStatusRFC getChargePointOutletStatusRFC() {
        ChargePointOutletStatusRFC chargePointOutletStatusRFC = new ChargePointOutletStatusRFC()
        beanFactory.autowireBean(chargePointOutletStatusRFC)
        chargePointOutletStatusRFC
    }

    void trainRf() {
        File timestampAvailFile = new File('/Users/chowie.lin/chargepoint/timestampavail')
        ChargePointOutletStatusRFC chargePointOutletStatusRFC = getChargePointOutletStatusRFC()
        availCountClassifier = chargePointOutletStatusRFC
        println "ChargePointModelManager.trainRf; training data file: ${timestampAvailFile.getPath()}"
        chargePointOutletStatusRFC.train(timestampAvailFile)
    }
}
