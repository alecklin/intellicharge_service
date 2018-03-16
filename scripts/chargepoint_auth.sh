#!/bin/bash

#curl -b cookies.txt -c cookies.txt \
#-X GET https://www.chargepoint.com/

curl -s -b cookies.txt -c cookies.txt \
-X POST https://na.chargepoint.com/users/validate \
-H ":authority: na.chargepoint.com" \
-H ":method: POST" \
-H ":path: /users/validate" \
-H ":scheme: https" \
-H "accept: */*" \
-H "accept-encoding:" \
-H "accept-language: en-US,en;q=0.8" \
-H "content-type: application/x-www-form-urlencoded; charset=UTF-8" \
-H "origin: https://na.chargepoint.com" \
-H "referer: https://na.chargepoint.com/home" \
-H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36" \
-H "x-requested-with: XMLHttpRequest" \
--data-urlencode "user_name=user" \
--data-urlencode "user_password=password" \
--data-urlencode "auth_code=" \
--data-urlencode "recaptcha_response_field=" \
--data-urlencode "timezone_offset=420" \
--data-urlencode "timezone=PDT" \
--data-urlencode "timezone_name=" > /dev/null
