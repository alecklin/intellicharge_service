package com.snc.intellicharge.controller

import com.snc.intellicharge.data.ChargePointDataFileToLibSVM
import com.snc.intellicharge.data.DataScientist
import com.snc.intellicharge.data.SnowDAO
import com.snc.intellicharge.insight.RealtimeInsightGenerator
import com.snc.intellicharge.ml.ChargePointModelManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class MLController {
    @Autowired
    ChargePointModelManager fChargePointModelManager

    @Autowired
    ChargePointDataFileToLibSVM fChargePointDataFileToLibSVM

    @Autowired
    RealtimeInsightGenerator fRealtimeInsightGenerator

    @Autowired
    SnowDAO fSnowDAO

    @Autowired
    DataScientist fDataScientist

    @RequestMapping('/')
    String index() {
        return 'Greetings from Spring Boot!'
    }

    @RequestMapping(value = 'testdata', method = RequestMethod.POST)
    void postTestData() {
        def data = [day_of_week: 'Monday', status: 'avail']
        fSnowDAO.createRecord(new Tuple2('admin', 'admin'), 'chowie2table', data)
    }

    @RequestMapping(value = 'deletedata', method = RequestMethod.POST)
    void deleteData() {
        fSnowDAO.deleteAllRecords(new Tuple2('admin', 'admin'), 'chargepointstatuscount', null)
    }

    @RequestMapping(value = 'scheduleentries', method = RequestMethod.GET)
    void getScheduleEntries() {
        fSnowDAO.getRecords(new Tuple2('admin', 'admin'), 'cmn_schedule_span', 20000, 'schedule=0ed533277371030025a69fb24ff6a71f').getBody()['result'].each {
            Map r ->
                println "name: ${r.name}; sys_id: ${r.sys_id}; schedule: ${r.schedule}; start_date_time: ${r.start_date_time}; end_date_time: ${r.end_date_time}"
        }
    }

    @RequestMapping(value = 'scheduleentry', method = RequestMethod.POST)
    void createScheduleEntry() {
        fSnowDAO.createAvailWindow('chargepoint window', 1, 7, 3, 7, 9)
//        createScheduleEntry('chargepoint schedule entry 4', '0ed533277371030025a69fb24ff6a71f', '20171019T162000Z', '20171019T162700Z')
    }

    @RequestMapping(value = 'updatestats', method = RequestMethod.POST)
    void updateStats() {
        fSnowDAO.updateStatsRecord(1, 2, 3, 4, 5, 6, 7,
                '-3%',
                '-1%', '+2%')
    }

    @RequestMapping(value = 'updateprediction', method = RequestMethod.POST)
    void updatePrediction() {
        fSnowDAO.updatePredictionRecord('none', '30 minutes', '12:56PM')
    }

    @RequestMapping('/convertjsontocountavailsvm')
    void convertJsonToCountAvailSVM() {
        fDataScientist.convertJsonToCountAvailSVM()
    }

    @RequestMapping('/convertjsontocountavailsvm2')
    void convertJsonToCountAvailSVM2() {
        fDataScientist.convertJsonToCountAvailSVM2()
    }

    @RequestMapping('/convertjsontocountavailsvm3')
    void convertJsonToCountAvailSVM3() {
        fDataScientist.convertJsonToCountAvailSVM3()
    }

    @RequestMapping('/trainrf')
    void trainRf() {
        fChargePointModelManager.trainRf()
    }

    @RequestMapping('/predictavail')
    void predictAvail() {
        fRealtimeInsightGenerator.predictAvail()
    }

    @RequestMapping(value = '/storepredictions', method = RequestMethod.POST)
    void storePredictions() {
        fRealtimeInsightGenerator.storePredictions()
    }

    @RequestMapping(value = '/doml', method = RequestMethod.POST)
    void doMl() {
        File mlCacheCreateTimeFile = new File('/Users/chowie.lin/chargepoint/mlCacheCreateTime')
        mlCacheCreateTimeFile.delete()
        fRealtimeInsightGenerator.doMl(false)
    }
}