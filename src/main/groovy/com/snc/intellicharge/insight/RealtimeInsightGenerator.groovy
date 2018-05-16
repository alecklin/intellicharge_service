package com.snc.intellicharge.insight

import com.snc.intellicharge.config.ConfigProps
import com.snc.intellicharge.data.DataFetcher
import com.snc.intellicharge.data.DataScientist
import com.snc.intellicharge.data.SnowDAO
import com.snc.intellicharge.ml.ChargePointModelManager
import com.snc.intellicharge.schedule.Scheduler
import com.snc.intellicharge.util.DateUtil
import groovy.json.JsonSlurper
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import in.ashwanthkumar.slack.webhook.Slack
import in.ashwanthkumar.slack.webhook.SlackAttachment
import in.ashwanthkumar.slack.webhook.SlackMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.text.DecimalFormat

@Canonical
//@InheritConstructors
@Service
class RealtimeInsightGenerator {

    @Autowired
    Slack fSlack

    @Autowired
    SnowDAO fSnowDAO

    @Autowired
    ChargePointModelManager fChargePointModelManager

    @Autowired
    DataFetcher fDataFetcher

    @Autowired
    DataScientist fDataScientist

    @Autowired
    Scheduler fScheduler

    @Autowired
    DateUtil fDateUtil

    @Autowired
    ConfigProps fConfigProps

    int predictionLookaheadTime
    int predictionLookaheadNotifyInterval
    int pollingInterval
    int mlIntervalDelay
    int mlInterval
    List<Integer> unavailVelocities = new ArrayList<>()

    String lastIntervalPredictedSysId
    Date lastPredictionLookaheadNotifyDate

    public RealtimeInsightGenerator() {

    }

    @Autowired
    RealtimeInsightGenerator(@Value('${prediction.lookahead.time}') String predictionWarningTimesStr,
                             @Value('${prediction.lookahead.notify.interval}') String predictionLookaheadNotifyIntervalStr,
                             @Value('${polling.interval}') String pollingIntervalStr,
                             @Value('${ml.interval.delay}') String mlIntervalDelayStr,
                             @Value('${ml.interval}') String mlIntervalStr,
                             @Value('${insight.predict.allunvail.velocities}') String insightPredictUnavailVelocitiesStr

                             ) {
        predictionLookaheadTime = predictionWarningTimesStr as Integer
        predictionLookaheadNotifyInterval = predictionLookaheadNotifyIntervalStr as Integer
        pollingInterval = pollingIntervalStr as Integer
        mlIntervalDelay = mlIntervalDelayStr as Integer
        mlInterval = mlIntervalStr as Integer
        String[] unavailVelocitiesStrs = insightPredictUnavailVelocitiesStr.split(',')
        unavailVelocitiesStrs.each {
            unavailVelocities.add(Integer.parseInt(it))
        }
    }

    void predictAvail() {
        File libsvmFile = new File('/Users/chowie.lin/chargepoint/timestampinput')
        libsvmFile.delete()

        File predictedOutDir = new File('/Users/chowie.lin/chargepoint/predictedtimestampavail')
        println "RealtimeInsightGenerator.predictAvail; libsvmFile: ${libsvmFile.getPath()}; predictedOutDir: ${predictedOutDir.getPath()}"

        libsvmFile.withWriter('UTF-8') {
            Writer libsvmWriter ->
                7.times {
                    rawDayOfWeek ->
                        int dayOfWeek = rawDayOfWeek + 1
                        24.times {
                            rawHourOfDay ->
                                int hourOfDay = rawHourOfDay + 1
                                (60 / fChargePointModelManager.minToCondenseBy).times {
                                    rawMinOfHour ->
                                        int minOfHour = rawMinOfHour + 1
                                        String libsvmLine = "0 1:${dayOfWeek} 2:${hourOfDay} 3:${minOfHour}"
                                        libsvmWriter.write("${libsvmLine}\n")
                                }
                        }

                }
        }
        predictedOutDir.deleteDir()
        fChargePointModelManager.availCountClassifier.predict(libsvmFile, predictedOutDir)
    }

    void storePredictions() {
        println 'RealtimeInsightGenerator.storePredictions started'
        long startTime = System.currentTimeMillis()

        def startWindowRecord = null
        int windowIntervalCount = 0
        int windowWeekday = 0
        def previousRecord = null
        Map<Integer, Collection<Map>> dayOfWeekToWindowIntervalsMap = [:]
        7.times {
            dayOfWeekToWindowIntervalsMap[it + 1] = []
        }

        // delete all old predictions first
        fSnowDAO.deleteAllRecords(new Tuple2('admin', 'admin'), 'chargepointstatuscount')

        // delete all old avail windows
        fSnowDAO.deleteAllRecords(new Tuple2('admin', 'admin'), 'cmn_schedule_span', 'schedule=0ed533277371030025a69fb24ff6a71f')

        File predictedOutDir = new File('/Users/chowie.lin/chargepoint/predictedtimestampavail')

        predictedOutDir.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                name.endsWith('.json')
            }
        }).each { File predictionFile ->
            int yearCount = 0;
            double currentDayOfWeek = 0
            int countInDay = 0
            JsonSlurper jsonSlurper = new JsonSlurper()
            predictionFile.withReader('UTF-8') {
                Reader reader ->
                    reader.eachLine {
                        String line ->
                            Object predictionObj = jsonSlurper.parseText(line)
                            double prediction = predictionObj.prediction
                            def (double weekday, double hour, double min) = predictionObj.features.values as double[]
                            double realHour = hour - 1.0d
                            double realMin = min - 1.0d
                            int predictionInt = prediction as int
//                            println "availCountPrediction: $predictionInt; time: $weekday:$realHour:$realMin"

                            // this allows viewing the entire week in a timeline
                            int weekYear = 1000 + yearCount++
                            String timestamp = "${weekYear}-05-05 09:00:00"

                            // this allows view an entire day in a timeline
                            if (currentDayOfWeek == 0.0d || currentDayOfWeek != weekday) {
                                currentDayOfWeek = weekday
                                countInDay = 0
                            }
                            int dayYear = 1000 + countInDay++
                            String dayTimestamp = "${dayYear}-05-05 09:00:00"

                            def record = [timestamp: timestamp, daytimestamp: dayTimestamp, dayofweek: weekday, hourofday: realHour, minofhour: realMin,
                                          countavailable: predictionInt]

//                            println "Inserting record: $record"

                            // insert in SNOW
                            fSnowDAO.createRecord(new Tuple2('admin', 'admin'), 'chargepointstatuscount', record)

                            // see if avail window need to be created
                            if (startWindowRecord) {
                                boolean weekdayMismatch = (weekday as int) != windowWeekday
                                // check if window is to be closed
                                if (weekdayMismatch || predictionInt <= fChargePointModelManager.minAvailCountForWindowEnd) {
                                    if (windowIntervalCount >= fChargePointModelManager.minTimeIntervalCountForWindow) {
                                        def endRecord
                                        if (weekdayMismatch) {
                                            endRecord = previousRecord
                                        } else {
                                            endRecord = record
                                        }
                                        // create window
                                        String windowName = "IntelliCharge available window: ${endRecord.dayofweek as int};${startWindowRecord.hourofday as int}:${(startWindowRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy}-${endRecord.hourofday as int}:${(endRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy}"

                                        int dayOfWeek = endRecord.dayofweek as int
                                        int startWindowHourOfDay = startWindowRecord.hourofday as int
                                        int startWindowMinOfHour = (startWindowRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy
                                        int endWindowHourOfDay = endRecord.hourofday as int
                                        int endWindowMinOfHour = (endRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy

                                        fSnowDAO.createAvailWindow(windowName,
                                                dayOfWeek,
                                                startWindowHourOfDay,
                                                startWindowMinOfHour,
                                                endWindowHourOfDay,
                                                endWindowMinOfHour
                                        )
                                        dayOfWeekToWindowIntervalsMap[dayOfWeek] << [
                                                startWindowHourOfDay: startWindowHourOfDay,
                                                startWindowMinOfHour: startWindowMinOfHour,
                                                endWindowHourOfDay  : endWindowHourOfDay,
                                                endWindowMinOfHour  : endWindowMinOfHour
                                        ]
                                    }
                                    // reset tracking
                                    startWindowRecord = null

                                    // start new interval in the case of weekday boundary change
                                    if (weekdayMismatch && predictionInt >= fChargePointModelManager.minAvailCountForWindowStart) {
                                        startWindowRecord = record

                                        // reset interval count
                                        windowIntervalCount = 0

                                        // record weekday boundary
                                        windowWeekday = weekday as int
                                    }
                                } else {
                                    windowIntervalCount++
                                }
                            } else {
                                // see if an avail window needs to be started
                                if (predictionInt >= fChargePointModelManager.minAvailCountForWindowStart) {
                                    startWindowRecord = record

                                    // reset interval count
                                    windowIntervalCount = 0

                                    // record weekday boundary
                                    windowWeekday = weekday as int
                                }
                            }
                            // track prior record for window
                            previousRecord = record
                    }

                    if (startWindowRecord) {

                        // check if window is to be closed
                        if (windowIntervalCount >= fChargePointModelManager.minTimeIntervalCountForWindow) {
                            def endRecord

                            endRecord = previousRecord
                            // create window
                            String windowName = "IntelliCharge available window: ${endRecord.dayofweek as int};${startWindowRecord.hourofday as int}:${(startWindowRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy}-${endRecord.hourofday as int}:${(endRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy}"

                            int dayOfWeek = endRecord.dayofweek as int
                            int startWindowHourOfDay = startWindowRecord.hourofday as int
                            int startWindowMinOfHour = (startWindowRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy
                            int endWindowHourOfDay = endRecord.hourofday as int
                            int endWindowMinOfHour = (endRecord.minofhour as int) * fChargePointModelManager.minToCondenseBy

                            fSnowDAO.createAvailWindow(windowName,
                                    dayOfWeek,
                                    startWindowHourOfDay,
                                    startWindowMinOfHour,
                                    endWindowHourOfDay,
                                    endWindowMinOfHour
                            )
                            dayOfWeekToWindowIntervalsMap[dayOfWeek] << [
                                    startWindowHourOfDay: startWindowHourOfDay,
                                    startWindowMinOfHour: startWindowMinOfHour,
                                    endWindowHourOfDay  : endWindowHourOfDay,
                                    endWindowMinOfHour  : endWindowMinOfHour
                            ]

                        }
                        // reset tracking
                        startWindowRecord = null

                    }
            }
        }

        Map<Integer, String> dayOfWeekToStrMap = [
                1: 'Sunday',
                2: 'Monday',
                3: 'Tuesday',
                4: 'Wednesday',
                5: 'Thursday',
                6: 'Friday',
                7: 'Saturday'
        ]

        // delete all old insights
        fSnowDAO.deleteAllRecords(new Tuple2('admin', 'admin'), 'sn_intellicharge_chargepointinsights', null)

        // for each day of week, for each avail window, generate descriptive text
        7.times {
            int dayOfWeek = it + 1
            String dayOfWeekStr = dayOfWeekToStrMap[dayOfWeek]

            Map morningInsight = [:]
            Map afternoonInsight = [:]
            List<Map> noonInsights = []
            Map allDayInsight = [:]

            Collection<Map> intervals = dayOfWeekToWindowIntervalsMap[dayOfWeek]

            intervals.eachWithIndex { Map interval, int idx ->
                if (intervals.size() == 1) {
                    // only one interval means avail all day long
                    allDayInsight = [
                            type     : 3,
                            dayOfWeek: dayOfWeek,
                            interval : interval,
                            msg      : "On $dayOfWeekStr, charge ports are available the entire day"
                    ]
                } else if (idx == 0) {
                    // more than one interval
                    // first interval
                    // morning insight, 1st interval
                    // On Monday, all charge ports are taken up by 8:20am
                    int endWindowHourOfDay = interval.endWindowHourOfDay
                    int endWindowMinOfHour = interval.endWindowMinOfHour

                    Date firstIntervalEndDate = new Date()
                    firstIntervalEndDate.set(hourOfDay: endWindowHourOfDay, minute: endWindowMinOfHour)

                    Date maxMorningEndTime = new Date(firstIntervalEndDate.getTime())
                    maxMorningEndTime.set(hourOfDay: fChargePointModelManager.morningMaxEndTimeHour, minute: fChargePointModelManager.morningMaxEndTimeMin)

                    if (firstIntervalEndDate <= maxMorningEndTime) {
                        morningInsight = [
                                type     : 0,
                                dayOfWeek: dayOfWeek,
                                interval : interval,
                                msg      : "On $dayOfWeekStr, all charge ports are taken up by ${firstIntervalEndDate.format('hh:mma')}"
                        ]
                    }
                } else if (idx == intervals.size() - 1) {
                    // afternoon insight, last interval
                    int startWindowHourOfDay = interval.startWindowHourOfDay
                    int startWindowMinOfHour = interval.startWindowMinOfHour
                    int endWindowHourOfDay = interval.endWindowHourOfDay
                    int endWindowMinOfHour = interval.endWindowMinOfHour

                    Date lastIntervalStartDate = new Date()
                    lastIntervalStartDate.set(hourOfDay: startWindowHourOfDay, minute: startWindowMinOfHour)

                    Date lastIntervalEndDate = new Date()
                    lastIntervalEndDate.set(hourOfDay: endWindowHourOfDay, minute: endWindowMinOfHour)

                    Date minAfternoonStartTime = new Date(lastIntervalStartDate.getTime())
                    minAfternoonStartTime.set(hourOfDay: fChargePointModelManager.afternoonMinStartTimeHour, minute: fChargePointModelManager.afternoonMinStartTimeMin)

                    //if (lastIntervalStartDate >= minAfternoonStartTime) {
                    afternoonInsight = [
                            type     : 2,
                            dayOfWeek: dayOfWeek,
                            interval : interval,
                            msg      : "On $dayOfWeekStr, at the end of the day, charge ports start to be available at ${lastIntervalStartDate.format('hh:mma')}"
                    ]
//                    } else {
//                        noonInsights << [
//                                type: 1,
//                                dayOfWeek: dayOfWeek,
//                                interval: interval,
//                                msg: "On $dayOfWeekStr, some charge ports are available between ${lastIntervalStartDate.format('hh:mma')} and ${lastIntervalEndDate.format('hh:mma')}"
//                        ]
//                    }
                } else {
                    // noon insight (min 30 min duration)
                    // On Monday, some charge ports free up at 12:00pm and you have until 1:00pm to get one
                    int startWindowHourOfDay = interval.startWindowHourOfDay
                    int startWindowMinOfHour = interval.startWindowMinOfHour
                    int endWindowHourOfDay = interval.endWindowHourOfDay
                    int endWindowMinOfHour = interval.endWindowMinOfHour

                    Date intervalStartDate = new Date()
                    intervalStartDate.set(hourOfDay: startWindowHourOfDay, minute: startWindowMinOfHour)

                    Date intervalEndDate = new Date()
                    intervalEndDate.set(hourOfDay: endWindowHourOfDay, minute: endWindowMinOfHour)

                    // interval duration
                    TimeDuration intervalDuration = TimeCategory.minus(intervalEndDate, intervalStartDate)

                    int noonMaxStartTimeHour = fChargePointModelManager.noonMaxStartTimeHour
                    int noonMaxStartTimeMin = fChargePointModelManager.noonMaxStartTimeMin
                    int noonMinDurationMinutes = fChargePointModelManager.noonMinDurationMinutes

                    TimeDuration noonMinDuration = new TimeDuration(0, noonMinDurationMinutes, 0, 0)

                    Date maxNoonStartTime = new Date(intervalStartDate.getTime())
                    maxNoonStartTime.set(hourOfDay: noonMaxStartTimeHour, minute: noonMaxStartTimeMin)

                    if (intervalStartDate <= maxNoonStartTime && intervalDuration >= noonMinDuration) {
                        noonInsights << [
                                type     : 1,
                                dayOfWeek: dayOfWeek,
                                interval : interval,
                                msg      : "On $dayOfWeekStr, some charge ports are available between ${intervalStartDate.format('hh:mma')} and ${intervalEndDate.format('hh:mma')}"
                        ]
                    }
                }
            }
            if (morningInsight) {
                println "Morning insight: ${morningInsight.msg}"
                fSnowDAO.createInsightRecord(morningInsight)
            }
            noonInsights.eachWithIndex { Map insight, int i ->
                println "Noon insight ${i + 1}: ${insight.msg}"
                insight.order = i + 1
                fSnowDAO.createInsightRecord(insight)
            }
            if (afternoonInsight) {
                println "Afternoon insight: ${afternoonInsight.msg}"
                fSnowDAO.createInsightRecord(afternoonInsight)
            }
            if (allDayInsight) {
                println "All-day insight: ${allDayInsight.msg}"
                fSnowDAO.createInsightRecord(allDayInsight)
            }
        }
        long endTime = System.currentTimeMillis()
        println "RealtimeInsightGenerator.storePredictions finished and took ${(endTime - startTime) / 1000} seconds"
    }

    /*
    {
    "attachments": [
        {
            "fallback": "Required plain-text summary of the attachment.",
            "color": "#ff0000",
            "pretext": "Optional text that appears above the attachment block",
            "author_name": "Bobby Tables",
            "author_link": "http://flickr.com/bobby/",
            "author_icon": "http://flickr.com/icons/bobby.jpg",
            "title": "Slack API Documentation",
            "title_link": "https://api.slack.com/",
            "text": "Optional text that appears within the attachment",
            "fields": [
                {
                    "title": "Priority",
                    "value": "High",
                    "short": false
                }
            ],
            "image_url": "http://my-website.com/path/to/image.jpg",
            "thumb_url": "http://example.com/path/to/thumb.png",
            "footer": "Slack API",
            "footer_icon": "https://platform.slack-edge.com/img/default_application_icon.png",
            "ts": 123456789
        }
    ]
}
     */

    void sendSlackMsg(SlackMessage slackMsg) {
        fSlack
                .push(slackMsg)
    }

    void sendSlackAttachment(SlackAttachment slackAttachment) {
        sendSlackAttachment(slackAttachment, null)
    }

    void sendSlackAttachment(SlackAttachment slackAttachment, String channel) {
        if (channel) {
            fSlack.sendToChannel(channel)
        } else {
            fSlack.sendToChannel(fConfigProps.notificationSlackChannelDefault)
        }
        fSlack.push(slackAttachment)
    }

    String percentChange(int oldValue, int newValue) {
        percentChange(oldValue, newValue, oldValue)
    }

    String percentChange(int oldValue, int newValue, int total) {
        DecimalFormat df = new DecimalFormat("+#.##;-#.##")
        if (total == 0) {
            return ''
        }
        int delta = newValue - oldValue
        double deltaPercent = ((delta as double) / (total as double)) * 100d
        "${df.format(deltaPercent)}%"
    }

    int availCountOld = Integer.MIN_VALUE
    int inuseCountOld = 0
    int unknownCountOld = 0
    Date oldDate
    List<Double> velocities = new Stack<>()

    void generateInsightsFromRealtimeDatapoint() {
        fDataFetcher.fetchLatestDatapoint()

        def (Date date, int availCount, int inuseCount, int unknownCount, int totalCount) = fDataScientist.fetchStatusCountsFromLatestDatapoint()

        // real-time stats
        generateRealtimeInsights(oldDate, date, availCountOld, inuseCountOld, unknownCountOld, availCount, inuseCount, unknownCount, totalCount)

        availCountOld = availCount
        inuseCountOld = inuseCount
        unknownCountOld = unknownCount
        oldDate = date

        // See if current timestamp falls within an available interval or not to warn based upon prediction
        // get current date
        //instantiates a calendar using the current time in the specified timezone
        Calendar currCal = Calendar.getInstance(TimeZone.getTimeZone('America/Los_Angeles'))

        // testing
//                    currCal.set(Calendar.HOUR_OF_DAY, 15)
//                    currCal.set(Calendar.MINUTE, 50)
        // end testing

        generatePredictionInsights(availCount, totalCount, currCal, predictionLookaheadTime)
//                    generatePredictionInsights(0, totalCount, currCal, predictionLookaheadTime)
    }

    void start() {
        fScheduler.scheduleTask(0, pollingInterval) {
            def logDur = fDateUtil.logDuration('generating insights from realtime datapoint')
            generateInsightsFromRealtimeDatapoint()
            logDur()
        }
    }

    void startMl() {
        fScheduler.scheduleTask(mlIntervalDelay, mlInterval) {
            def logDur = fDateUtil.logDuration('performing ML')
            doMl(true)
            logDur()
        }
    }

    void doMl(boolean checkWorkDone) {
        println "RealtimeInsightGenerator.doMl; Started updating predictions into SNOW"
        boolean didWork = fDataScientist.convertJsonToCountAvailSVM()
        println "RealtimeInsightGenerator.doMl didWork: $didWork; checkWorkDone: $checkWorkDone"
        if (!checkWorkDone || checkWorkDone && didWork) {
            fDataScientist.convertJsonToCountAvailSVM2()
            fDataScientist.convertJsonToCountAvailSVM3()
            fChargePointModelManager.trainRf()
            predictAvail()
            storePredictions()
            println "RealtimeInsightGenerator.doMl; Finished updating predictions into SNOW"
        } else {
            println "RealtimeInsightGenerator.doMl; No need to update predictions into SNOW"
        }
    }

    static void main(String[] args) {
        use(groovy.time.TimeCategory) {
            RealtimeInsightGenerator realtimeInsightGenerator = new RealtimeInsightGenerator()
            Date currDate = new Date()
            double velocity = realtimeInsightGenerator.computeVelocity(currDate - 1.minute, currDate, 10, 9)
            println "velocity: $velocity/min"

            realtimeInsightGenerator.velocities << velocity

            velocity = realtimeInsightGenerator.computeVelocity(currDate - 1.minute, currDate, 9, 7)
            println "velocity: $velocity/min"
            realtimeInsightGenerator.velocities << velocity

            velocity = realtimeInsightGenerator.computeVelocity(currDate - 1.minute, currDate, 7, 4)
            println "velocity: $velocity/min"
            realtimeInsightGenerator.velocities << velocity

            realtimeInsightGenerator.unavailVelocities = [1, 3, 5]

            def msg = realtimeInsightGenerator.predictAllGoneByVelocities(10)
            String predictMsg = msg.join('\n')
            println predictMsg
        }
    }

    double computeVelocity(Date oldDate, Date newDate, int availCountOld, int availCount) {
        // compute velocity
        double velocity = 0
        use(groovy.time.TimeCategory) {
            if (oldDate && newDate) {
                def duration = newDate - oldDate
                def durationInMin = duration.toMilliseconds() / 1000 / 60
                velocity = availCountOld - availCount / durationInMin
            }
        }
        velocity
    }

    List<String> predictAllGoneByVelocities(int availCount) {
        def predictMsgs = []
        use(groovy.time.TimeCategory) {
            // use velocities to predict
            unavailVelocities.each {
                if (it > velocities.size()) {
                    return
                }
                int count = 0
                double velocityAvg = 0
                for (int idx = velocities.size() - 1; idx >= 0; idx--) {
                    double currVelocity = velocities.get(idx)
                    if (count == it) {
                        break
                    }
                    count++
                    velocityAvg += currVelocity
                }
                velocityAvg /= count
                println "velocityAvg: $velocityAvg"
                if (velocityAvg > 0) {
                    int minToGone = Math.round(availCount / velocityAvg)
                    if (minToGone > 0) {
                        Date dateGone = new Date() + minToGone.minutes
                        predictMsgs << "By ${dateGone.format('hh:mm a', TimeZone.getTimeZone("America/Los_Angeles"))} ${new Date()} $availCount ports predicted all gone in $minToGone minutes at a rate of $velocityAvg/min over last $count velocities"
                    }
                }
            }
        }
        predictMsgs
    }

    void generateRealtimeInsights(Date oldDate, Date date, int availCountOld, int inuseCountOld, int unknownCountOld,
                                  int availCount, int inuseCount, int unknownCount, int totalCount) {
        double percentAvail = ((availCount as double) / (totalCount as double)) * 100d
        DecimalFormat df = new DecimalFormat("#.##")
        String percentAvailStr = "${df.format(percentAvail)}%"
        String percentChangeOfTotal = percentChange(availCountOld, availCount, totalCount)
//                    println "Status counts: AVAIL: $availCount; IN_USE: $inuseCount; UNKNOWN: $unknownCount"


        String dateString = date.format('hh:mm:ss a', TimeZone.getTimeZone("America/Los_Angeles"))
        double velocity = computeVelocity(oldDate, date, availCountOld, availCount)
        List<String> gonePredictMsgs = []

        // if velocity is negative, then reset
        if (velocity <= 0) {
            this.velocities = []
        } else {
            // positive velocity means ports are decreasing
            this.velocities << velocity
            int numToKeep = unavailVelocities.last()
            int numToDrop = this.velocities.size() - numToKeep
            if (numToDrop > 0) {
                this.velocities = this.velocities.drop(numToDrop)
            }
            gonePredictMsgs = predictAllGoneByVelocities(availCount)
        }


        if (availCountOld != Integer.MIN_VALUE) {
            if (availCount > 0) {
                if (availCountOld == 0) {
                    println "AVAIL count became non-zero from $availCountOld -> $availCount"
                    SlackAttachment slackAttachment1 = new SlackAttachment()
                    slackAttachment1
                            .color('009900')
                            .preText("$dateString: Available count became non-zero from $availCountOld -> $availCount, a change of ${availCount - availCountOld} (${percentChange(availCountOld, availCount)}) (${percentChangeOfTotal} of ${totalCount})")
                            .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                            .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                            .addField(new SlackAttachment.Field('In Use Count', "$inuseCount", false))
                            .addField(new SlackAttachment.Field('Unknown Count', "$unknownCount", false))

                    sendSlackAttachment(slackAttachment1)
                    sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelAvailUpgrade)

                    if (availCount >= fConfigProps.notificationAvailUpgradeMin) {
                        sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelUpgradeThreshold)
                    }

                    fSnowDAO.updateStatsRecord(0, availCount, availCountOld, inuseCount, inuseCountOld, unknownCount, unknownCountOld, totalCount, percentAvailStr,
                            percentChange(availCountOld, availCount), percentChangeOfTotal)
                } else if (availCount > availCountOld) {
                    println "AVAIL count increased from $availCountOld -> $availCount, a change of +${availCount - availCountOld}"

                    SlackAttachment slackAttachment1 = new SlackAttachment()
                    slackAttachment1
                            .color('009900')
                            .preText("$dateString: Available count increased from $availCountOld -> $availCount, a change of +${availCount - availCountOld} (${percentChange(availCountOld, availCount)}) (${percentChangeOfTotal} of ${totalCount})")
                            .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                            .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                            .addField(new SlackAttachment.Field('In Use Count', "$inuseCount", false))
                            .addField(new SlackAttachment.Field('Unknown Count', "$unknownCount", false))

                    sendSlackAttachment(slackAttachment1)
                    sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelAvailUpgrade)

                    if (availCount >= fConfigProps.notificationAvailUpgradeMin) {
                        sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelUpgradeThreshold)
                    }

                    fSnowDAO.updateStatsRecord(0, availCount, availCountOld, inuseCount, inuseCountOld, unknownCount, unknownCountOld, totalCount, percentAvailStr,
                            percentChange(availCountOld, availCount), percentChangeOfTotal)
                } else if (availCount < availCountOld) {
                    println "Available count decreased from $availCountOld -> $availCount, a change of ${availCountOld - availCount} (${percentChange(availCountOld, availCount)})"

                    SlackAttachment slackAttachment1 = new SlackAttachment()
                    slackAttachment1
                            .color('ffcc00')
                            .preText("$dateString: Available count decreased from $availCountOld -> $availCount, a change of -${availCountOld - availCount} (${percentChange(availCountOld, availCount)}) (${percentChangeOfTotal} of ${totalCount})")
                            .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                            .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                            .addField(new SlackAttachment.Field('In Use Count', "$inuseCount", false))
                            .addField(new SlackAttachment.Field('Unknown Count', "$unknownCount", false))

                    if (gonePredictMsgs) {
                        String predictMsg = gonePredictMsgs.join('\n')
                        slackAttachment1.addField(new SlackAttachment.Field('Predict', "$predictMsg", false))
                    }

                    sendSlackAttachment(slackAttachment1)
                    sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelAvailDowngrade)

                    fSnowDAO.updateStatsRecord(0, availCount, availCountOld, inuseCount, inuseCountOld, unknownCount, unknownCountOld, totalCount, percentAvailStr,
                            percentChange(availCountOld, availCount), percentChangeOfTotal)
                }
            } else if (availCount == 0 && availCountOld > 0) {
                println "AVAIL count went to zero from $availCountOld"
                SlackAttachment slackAttachment1 = new SlackAttachment()
                slackAttachment1
                        .color('990000')
                        .preText("$dateString: Available count went to zero from $availCountOld, a change of -${availCountOld - availCount} (${percentChange(availCountOld, availCount)}) (${percentChangeOfTotal} of ${totalCount})")
                        .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                        .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                        .addField(new SlackAttachment.Field('In Use Count', "$inuseCount", false))
                        .addField(new SlackAttachment.Field('Unknown Count', "$unknownCount", false))

                sendSlackAttachment(slackAttachment1)
                sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelAvailDowngrade)

                fSnowDAO.updateStatsRecord(0, availCount, availCountOld, inuseCount, inuseCountOld, unknownCount, unknownCountOld, totalCount, percentAvailStr,
                        percentChange(availCountOld, availCount), percentChangeOfTotal)
            }
        } else {
            println "Initial status counts: AVAIL: $availCount; IN_USE: $inuseCount; UNKNOWN: $unknownCount"

            SlackAttachment slackAttachment1 = new SlackAttachment()
            slackAttachment1
                    .color('0099ff')
                    .preText("$dateString: Initial charge port status counts")
                    .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                    .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                    .addField(new SlackAttachment.Field('In Use Count', "$inuseCount", false))
                    .addField(new SlackAttachment.Field('Unknown Count', "$unknownCount", false))

            sendSlackAttachment(slackAttachment1)

            fSnowDAO.updateStatsRecord(1, availCount, availCountOld, inuseCount, inuseCountOld, unknownCount, unknownCountOld, totalCount, percentAvailStr,
                    percentChange(availCountOld, availCount), percentChangeOfTotal)

        }
    }

    void generatePredictionInsights(int availCount, int totalCount, Calendar relativeToCal, int predictionLookaheadTime) {
        Date relativeToDate = relativeToCal.getTime()
        int dayOfWeek = relativeToCal.get(Calendar.DAY_OF_WEEK)

        // skip weekends
        if (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY) {
            return
        }

        // get avail intervals for current day of the week
        Collection<Map> insightsByWeek = fSnowDAO.getInsightEntriesByDayOfWeek(dayOfWeek)

        // add normalize start, end dates relative to calendar passed in
        insightsByWeek = normalizeInsightEntriesToDate(insightsByWeek, relativeToDate)

        Map[] insightsByWeekArr = insightsByWeek as Map[]
        Arrays.sort(insightsByWeekArr, { Map interval1, Map interval2 ->
            interval1.startDate.compareTo(interval2.startDate)
        })

        // if availcount > 0 and in interval, report predicted date that availcount == 0 (end date)
        // if availcount == 0 and not in interval, report predicted date that availcount > 0 (start date)
        boolean inInterval
        Map intervalIdentified

        insightsByWeekArr.find { Map interval ->
            if (relativeToDate < interval.startDate) {
                // before start of interval
                inInterval = false
                intervalIdentified = interval
                return true
            } else if (relativeToDate >= interval.startDate && relativeToDate <= interval.endDate) {
                // in interval
                inInterval = true
                intervalIdentified = interval
                return true
            } else if (relativeToDate > interval.endDate) {
                // after end of interval, so find next one
                return false
            }
            return false
        }

        // if any interval see if in or before start date
        use(groovy.time.TimeCategory) {
            if (intervalIdentified) {
                if (lastIntervalPredictedSysId != intervalIdentified.sys_id
                        || (lastPredictionLookaheadNotifyDate + predictionLookaheadNotifyInterval.minutes) < new Date()) {

                    if (!inInterval) {
                        // before interval

                        // see if lookahead time >= interval startDate
                        use(TimeCategory) {
                            Date forecastDate = relativeToDate + predictionLookaheadTime.minutes
                            if (forecastDate >= intervalIdentified.startDate) {
                                if (availCount == 0) {
                                    use(groovy.time.TimeCategory) {
                                        def duration = intervalIdentified.startDate - relativeToDate
                                        def durationOfAvail = intervalIdentified.endDate - intervalIdentified.startDate
                                        String predictMsg = "It is forecasted that charge ports will be available in $duration @ ${intervalIdentified.startDate.format('hh:mma')} for $durationOfAvail"
                                        println "$predictMsg"

                                        double percentAvail = ((availCount as double) / (totalCount as double)) * 100d
                                        DecimalFormat df = new DecimalFormat("#.##")
                                        String percentAvailStr = "${df.format(percentAvail)}%"

                                        SlackAttachment slackAttachment1 = new SlackAttachment()
                                        slackAttachment1
                                                .color('009900')
                                                .preText(predictMsg)
                                                .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                                                .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                                        sendSlackAttachment(slackAttachment1)
                                        sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelForecast)

                                        fSnowDAO.updatePredictionRecord('available', duration as String, intervalIdentified.startDate.format('hh:mma'))

                                        lastIntervalPredictedSysId = intervalIdentified.sys_id
                                        lastPredictionLookaheadNotifyDate = new Date()
                                    }
                                }
                            }
                        }
                    } else {
                        // in interval

                        // end date must not be end of day
                        if (intervalIdentified.endDate < getNormalizedEndOfDayDate(relativeToDate)) {

                            // see if lookahead time >= interval endDate
                            use(TimeCategory) {
                                Date forecastDate = relativeToDate + predictionLookaheadTime.minutes
                                if (forecastDate >= intervalIdentified.endDate) {
                                    if (availCount > 0) {
                                        use(groovy.time.TimeCategory) {
                                            def duration = intervalIdentified.endDate - relativeToDate
                                            String predictMsg = "It is forecasted that all charge ports will be unavailable in $duration @ ${intervalIdentified.endDate.format('hh:mma')}"
                                            println "$predictMsg"

                                            double percentAvail = ((availCount as double) / (totalCount as double)) * 100d
                                            DecimalFormat df = new DecimalFormat("#.##")
                                            String percentAvailStr = "${df.format(percentAvail)}%"

                                            SlackAttachment slackAttachment1 = new SlackAttachment()
                                            slackAttachment1
                                                    .color('990000')
                                                    .preText(predictMsg)
                                                    .title('IntelliCharge', 'http://localhost:8080/$sn_intellicharge.do')
                                                    .addField(new SlackAttachment.Field('Available Count', "$availCount of $totalCount ($percentAvailStr)", false))
                                            sendSlackAttachment(slackAttachment1)
                                            sendSlackAttachment(slackAttachment1, fConfigProps.notificationSlackChannelForecast)

                                            fSnowDAO.updatePredictionRecord('unavailable', duration as String, intervalIdentified.endDate.format('hh:mma'))

                                            lastIntervalPredictedSysId = intervalIdentified.sys_id
                                            lastPredictionLookaheadNotifyDate = new Date()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

//        sendPredictionWarningsViaSlack(availCount, totalCount)
    }

    Date getNormalizedEndOfDayDate(Date date) {
        getDateFromDayDate(date, 23, 58)
    }

    Collection<Map> normalizeInsightEntriesToDate(Collection<Map> insightEntries, Date date) {
        insightEntries.each {
            Map interval ->
                int starthourofday = interval.starthourofday as Integer
                int startminofhour = interval.startminofhour as Integer
                int endhourofday = interval.endhourofday as Integer
                int endminofhour = interval.endminofhour as Integer

                // interval start date
                Date intervalStartDate = getDateFromDayDate(date, starthourofday, startminofhour)

                // interval end date
                Date intervalEndDate = getDateFromDayDate(date, endhourofday, endminofhour)

                interval.startDate = intervalStartDate
                interval.endDate = intervalEndDate
        }
    }

    Date getDateFromDayDate(Date dayDate, int hourOfDay, int minute) {
        Date date = new Date(dayDate.getTime())
        date.set(hourOfDay: hourOfDay, minute: minute)
        date
    }
}
