package com.snc.intellicharge.util

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.stereotype.Component

@Canonical
@InheritConstructors
@Component
class DateUtil {
    Date getDateFromYearMonthDay(Date date, int year, int month, int day) {
        date.copyWith(
                year: year,
                month: month - 1,
                dayOfMonth: day
        )
    }

    Closure logDuration(String msg) {
        Date startDate = new Date()
        println "Started $msg: $startDate"
        return {
            Date endDate = new Date()
            use(groovy.time.TimeCategory) {
                println "Finished $msg: ${endDate}; duration: ${endDate - startDate}"
            }
        }
    }
}
