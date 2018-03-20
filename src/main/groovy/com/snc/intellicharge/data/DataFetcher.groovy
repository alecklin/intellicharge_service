package com.snc.intellicharge.data

import com.snc.intellicharge.config.ConfigProps
import com.snc.intellicharge.schedule.Scheduler
import com.snc.intellicharge.util.DateUtil
import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct

@Canonical
//@InheritConstructors
@Service
class DataFetcher {
    @Autowired
    Scheduler fScheduler

    @Autowired
    DateUtil fDateUtil

    @Autowired
    ConfigProps fConfigProps

    String collectorHost

    int timeseriesDataPollingDelay
    int timeseriesDataPollingInterval

    @Autowired
    DataFetcher(
            @Value('${timeseries.data.polling.delay}') String timeseriesDataPollingDelayStr,
            @Value('${timeseries.data.polling.interval}') String timeseriesDataPollingIntervalStr
    ) {
        timeseriesDataPollingDelay = timeseriesDataPollingDelayStr as Integer
        timeseriesDataPollingInterval = timeseriesDataPollingIntervalStr as Integer
    }

    @PostConstruct
    void postConstruct() {
        collectorHost = fConfigProps.collectorHost
    }

    // fetch ChargePoint data using rsync
    void fetchTimeseriesData() {
        def proc = '/Users/chowie.lin/chargepoint/fetchtimeseriesdata.sh'.execute()
        def sout = new StringBuilder(), serr = new StringBuilder()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        int rc = proc.exitValue()
        if (rc) {
            println "Timeseries data fetch failed, rc: $rc; out: $sout; err: $serr"
        }
    }

    void fetchLatestDatapoint() {
        def proc = "/usr/bin/scp -i /Users/chowie.lin/.ssh/chowielin.pem -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ubuntu@${collectorHost}:/home/ubuntu/chargepoint/latest/* /Users/chowie.lin/chargepoint/latest".execute()
        proc.waitFor()
//                    println "Latest data retrieved rc: ${proc.exitValue()}"
    }

    void start() {
        fScheduler.scheduleTask(timeseriesDataPollingDelay, timeseriesDataPollingInterval) {
            def logDur = fDateUtil.logDuration('fetching timeseries data')
            fetchTimeseriesData()
            logDur()
        }
    }
}
