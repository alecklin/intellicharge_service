package com.snc.intellicharge.config

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import in.ashwanthkumar.slack.webhook.Slack
import org.apache.spark.sql.SparkSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
@Canonical
@InheritConstructors
class ApplicationConfig {

    @Value('${slack.incoming.webhook.url}')
    String slackIncomingWebhookUrl

    @Bean
    SparkSession getSparkSession() {
        SparkSession spark = SparkSession

                .builder()

                .appName("SparkWithSpring")
                .master("local")

                .getOrCreate()

        System.out.println("Spark Version: " + spark.version())
        spark
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build()
    }

    @Bean Slack getSlack() {
        return new Slack(slackIncomingWebhookUrl)
    }
}
