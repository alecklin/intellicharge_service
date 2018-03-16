package com.snc.intellicharge.config

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Canonical
@InheritConstructors
@Component
class ConfigProps {

    @Value('${snow.server.url}')
    String snowServerUrl

    @Value('${snow.creds.username}')
    String snowCredsUsername

    @Value('${snow.creds.password}')
    String snowCredsPassword

    @Value('${snow.avail.table.schedule.sysid}')
    String snowAvailTableScheduleSysId

    @Value('${snow.scheduleentry.table}')
    String snowScheduleEntryTable

    @Value('${snow.insights.table}')
    String snowInsightsTable

    @Value('${snow.currentstats.table}')
    String snowCurrentStatsTable

    @Value('${snow.predictions.table}')
    String snowPredictionsTable

    @Value('${snow.table.currentstats.sysid}')
    String currentStatusSysId

    @Value('${snow.table.prediction.sysid}')
    String predictionSysId

    @Value('${ml.cache.expire.duration.days}')
    int mlCacheExpireDurationDays

    @Value('${ml.train.data.duration.days}')
    int mlTrainDataDurationDays

    @Value('${collector.host}')
    String collectorHost

    @Value('${notification.avail.upgrade.min}')
    int notificationAvailUpgradeMin

    @Value('${notification.slack.channel.default}')
    String notificationSlackChannelDefault

    @Value('${notification.slack.channel.upgrade-threshold}')
    String notificationSlackChannelUpgradeThreshold

    @Value('${notification.slack.channel.avail.upgrade}')
    String notificationSlackChannelAvailUpgrade

    @Value('${notification.slack.channel.avail.downgrade}')
    String notificationSlackChannelAvailDowngrade

    @Value('${notification.slack.channel.forecast}')
    String notificationSlackChannelForecast
}
