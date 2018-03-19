package com.snc.intellicharge.data

import com.snc.intellicharge.config.ApplicationConstants
import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

import javax.annotation.Nullable
import javax.annotation.PostConstruct

@Canonical
//@InheritConstructors
@Service
class SnowDAO {

    @Autowired
    RestTemplate restTemplate

    @Value('#{snowUserCreds.getUserCredsAsTuple()}')
    Tuple2<String, String> userCreds

    @Value('#{configProps.currentStatusSysId}')
    String currentStatusSysId

    @Value('#{configProps.predictionSysId}')
    String predictionSysId

    @Value('#{configProps.snowServerUrl}')
    String snowServerUrl

    @Value('#{configProps.snowScheduleEntryTable}')
    String snowScheduleEntryTable

    @Value('#{configProps.snowInsightsTable}')
    String snowInsightsTable

    @Value('#{configProps.snowCurrentStatsTable}')
    String snowCurrentStatsTable

    @Value('#{configProps.snowPredictionsTable}')
    String snowPredictionsTable

    @Value('#{configProps.snowAvailTableScheduleSysId}')
    String snowAvailTableScheduleSysId

    @PostConstruct
    void postConstruct() {
        println 'snowdao post'
    }

    HttpHeaders getHttpHeaders(Tuple2 creds) {
        def (String user, password) = creds
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.setAccept([MediaType.APPLICATION_JSON])
        headers.set('Authorization', "Basic ${"${user}:$password".getBytes('UTF-8').encodeBase64()}")
        headers
    }

    void createRecord(Tuple2 creds, String table, Object record) {
        HttpHeaders headers = getHttpHeaders(creds)
        HttpEntity<Object> entity = new HttpEntity<Object>(record, headers)

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity("$snowServerUrl/api/now/table/$table", entity, Object.class)
//            println "HTTP response: ${response.getStatusCodeValue()}: ${response.getBody()}"
        } catch (Exception ex) {
            println "$ex.getMessage()"
        }
    }

    void updateRecord(Tuple2 creds, String table, String sysId, Object record) {
        HttpHeaders headers = getHttpHeaders(creds)
        HttpEntity<Object> entity = new HttpEntity<Object>(record, headers)

        try {
            ResponseEntity<Object> response = restTemplate.exchange("$snowServerUrl/api/now/table/$table/$sysId", HttpMethod.PUT, entity, Object.class)
//            println "HTTP response: ${response.getStatusCodeValue()}: ${response.getBody()}"
        } catch (Exception ex) {
            println "$ex.getMessage()"
        }
    }

    void deleteRecord(Tuple2 creds, String table, String sysId) {
        HttpHeaders headers = getHttpHeaders(creds)
        HttpEntity<Void> entity = new HttpEntity<Void>(null, headers)
        restTemplate.exchange("$snowServerUrl/api/now/table/$table/$sysId", HttpMethod.DELETE, entity, Void.class)
    }

    Collection<Map> deleteAllRecords(Tuple2 creds, String table) {
        deleteAllRecords(creds, table, null)
    }

    Collection<Map> deleteAllRecords(Tuple2 creds, String table, @Nullable String query) {
        println "SnowDAO.deleteAllRecords; table: $table; query: $query"
        ResponseEntity<Object> response = getRecords(creds, table, 20000, query)
        response.getBody()['result'].each {
            Map record ->
                String sysId = record.sys_id
                // delete record
                deleteRecord(creds, table, sysId)
        }
    }

//http://localhost:8080/api/now/table/cmn_schedule_span?sysparm_query=schedule%3D0ed533277371030025a69fb24ff6a71f&sysparm_limit=10
    ResponseEntity<Object> getRecords(Tuple2 creds, String table, int limit, @Nullable String query) {
        HttpHeaders headers = getHttpHeaders(creds)
        HttpEntity<Void> entity = new HttpEntity<Void>(null, headers)
        Map<String, ?> variableMap = ['sysparm_limit': "$limit"]
        String url = "$snowServerUrl/api/now/table/$table?sysparm_limit={sysparm_limit}"

        if (query) {
            url = "${url}&sysparm_query={sysparm_query}"
            variableMap['sysparm_query'] = query
        }

        ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Object.class, variableMap)
        response
    }

    // "start_date_time": "20171020T172000Z"
    void createAvailWindow(String name, int dayOfWeek, int startHour, int startMin, int endHour, int endMin) {
        Date startDate = ApplicationConstants.DAYOFWEEK_TO_DATE_MAP[dayOfWeek].getTime().clone()
        startDate.set(hourOfDay: startHour, minute: startMin, second: 0)

        String startDateGMTStr = startDate.format(ApplicationConstants.TIME_FMT, TimeZone.getTimeZone('UTC'))

        Date endDate = ApplicationConstants.DAYOFWEEK_TO_DATE_MAP[dayOfWeek].getTime().clone()
        endDate.set(hourOfDay: endHour, minute: endMin, second: 0)

        String endDateGMTStr = endDate.format(ApplicationConstants.TIME_FMT, TimeZone.getTimeZone('UTC'))

        println "Creating available window: name: $name; startDateGMTStr: $startDateGMTStr; endDateGMTStr: $endDateGMTStr"

        createScheduleEntry(name, snowAvailTableScheduleSysId, startDateGMTStr, endDateGMTStr)
    }

    void createScheduleEntry(String name, String schedule, String startDate, String endDate) {
        createRecord(userCreds, snowScheduleEntryTable,
                [
                        name           : name,
                        schedule       : schedule,
                        start_date_time: startDate,
                        end_date_time  : endDate
                ])
    }

    void createInsightRecord(Map insight) {
        createRecord(userCreds, snowInsightsTable,
                [
                        dayofweek: insight.dayOfWeek,
                        starthourofday: insight.interval.startWindowHourOfDay,
                        startminofhour: insight.interval.startWindowMinOfHour,
                        endhourofday  : insight.interval.endWindowHourOfDay,
                        endminofhour: insight.interval.endWindowMinOfHour,
                        msg: insight.msg as String,
                        order: insight.order ?: 0,
                        type: insight.type
                ])
    }

    /*
    {
  "result": [
    {
      "msg": "On Monday, at the end of the day, charge",
      "sys_mod_count": "0",
      "sys_updated_on": "2017-10-25 20:50:26",
      "sys_tags": "",
      "type": "2",
      "endhourofday": "23",
      "sys_id": "245b56557302030025a69fb24ff6a72d",
      "starthourofday": "16",
      "sys_updated_by": "admin",
      "sys_created_on": "2017-10-25 20:50:26",
      "startminofhour": "18",
      "dayofweek": "2",
      "endminofhour": "58",
      "sys_created_by": "admin",
      "order": "0"
    }
  ]
}
     */
    Collection<Map> getInsightEntriesByDayOfWeek(int dayOfWeek) {
        getRecords(userCreds, snowInsightsTable, 20000, "dayofweek=$dayOfWeek^ORDERBYorder" as String).getBody()['result']
    }

    void updateStatsRecord(
            int init, int availCount, int availCountOld, int inuseCount, int inuseCountOld, int unknownCount, int unknownCountOld, int totalCount,
            String percentAvail,
            String percentChangeAvailFromOld,
            String percentChangeAvailOfTotal
    ) {
        try {
            // {"availcount":"","availcountold":"","inusecount":"","inusecountold":"","percentchangeavailfromold":"","percentchangeavailoftotal":"","totalcount":"","unknowncount":"","unknowncountold":""}
            updateRecord(userCreds, snowCurrentStatsTable, currentStatusSysId,
                    [
                            init                     : init,
                            availcount               : availCount,
                            availcountold            : availCountOld,
                            inusecount               : inuseCount,
                            inusecountold            : inuseCountOld,
                            unknowncount             : unknownCount,
                            unknowncountold          : unknownCountOld,
                            totalcount               : totalCount,
                            percentavail             : percentAvail,
                            percentchangeavailfromold: percentChangeAvailFromOld,
                            percentchangeavailoftotal: percentChangeAvailOfTotal
                    ])
        } catch (Exception ex) {
            println "updateStatsRecord error: ${ex.getMessage()}"
        }
    }

    /*
    {
  "result": {
    "predictiontype": "none",
    "sys_id": "5d60c8837342030025a69fb24ff6a7d3",
    "datestr": "12:56PM",
    "durationstr": "10 minutes",
  }
}
     */
    void updatePredictionRecord(
            String predictionType, String durationStr, String dateStr
    ) {
        updateRecord(userCreds, snowPredictionsTable, predictionSysId,
                [
                        predictiontype: predictionType,
                        durationstr: durationStr,
                        datestr: dateStr
                ])
    }
}
