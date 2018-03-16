#!/bin/bash
curl --write-out "HTTPSTATUS:%{http_code}" -s -b cookies.txt -c cookies.txt \
-X POST https://na.chargepoint.com/dashboard/get_station_info_json \
-H ":authority: na.chargepoint.com" \
-H ":method: POST" \
-H ":scheme: https" \
-H "accept: */*" \
-H "accept-encoding:" \
-H "accept-language: en-US,en;q=0.8" \
-H "content-type: application/x-www-form-urlencoded; charset=UTF-8" \
-H "origin: https://na.chargepoint.com" \
-H "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36" \
-H "x-requested-with: XMLHttpRequest" \
-H ":path: /dashboard/get_station_info_json" \
-H "referer: https://na.chargepoint.com/dashboard_driver" \
--data-urlencode "deviceId=$1"
