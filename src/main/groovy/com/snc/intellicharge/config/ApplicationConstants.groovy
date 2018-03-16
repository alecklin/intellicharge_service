package com.snc.intellicharge.config

class ApplicationConstants {
    static Map<Integer, Calendar> DAYOFWEEK_TO_DATE_MAP = [:]
    static String TIME_FMT = /yyyyMMdd'T'HHmmss'Z'/

    static {
        [1: 12, 2: 13, 3: 14, 4: 15, 5: 16, 6: 17, 7: 18].each { dayOfWeek, day ->
            DAYOFWEEK_TO_DATE_MAP[dayOfWeek] = Calendar.getInstance(TimeZone.getTimeZone('America/Los_Angeles'))
            DAYOFWEEK_TO_DATE_MAP[dayOfWeek].set(2017, 10, day)
        }
    }

}
