package com.snc.intellicharge.util

import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Canonical
//@InheritConstructors
@Component
class SnowUserCreds {

    @Value('#{configProps.snowCredsUsername}')
    String snowCredsUsername

    @Value('#{configProps.snowCredsPassword}')
    String snowCredsPassword

    Tuple2<String, String> getUserCredsAsTuple() {
        new Tuple2(snowCredsUsername, snowCredsPassword)
    }
}
