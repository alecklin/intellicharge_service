package com.snc.intellicharge.schedule

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.stereotype.Service

@Canonical
//@InheritConstructors
@Service
class Scheduler {
    // File: newtimer.groovy
    static class GroovyTimerTask extends TimerTask {
        Closure closure
        void run() {
            closure()
        }
    }

    static class TimerMethods {
        static TimerTask runEvery(Timer timer, long delay, long period, Closure codeToRun) {
            TimerTask task = new GroovyTimerTask(closure: codeToRun)
            timer.schedule task, delay, period
            task
        }
    }

    void scheduleTask(long delay, long period, Closure codeToRun) {
        use(TimerMethods) {
            def timer = new Timer()
            def task = timer.runEvery(delay, period) {
                try {
                    codeToRun()
                } catch (Exception ex) {
                    ex.printStackTrace()
                }
            }
        }
    }
}
