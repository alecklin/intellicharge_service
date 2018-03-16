#!/bin/bash

## declare an array variable
declare -a STATION_IDS=(
"122601"
"121475"
"121501"
"123203"
"121505"
"121509"
"121551"
"121503"
"121513"
"121407"
"121485"
"121403"
"121495"
"123179"
"121467"
"121469"
)

LAST_AUTH_TIME_SEC=0

set +e

## now loop through the above array
while [ true ]; do

DATE=`date +%Y-%m-%d`
mkdir -p "$DATE"

CURRENT_TIME_SEC=$(date +%s)

let TIME_SINCE_AUTH=CURRENT_TIME_SEC-LAST_AUTH_TIME_SEC

if [ "$TIME_SINCE_AUTH" -gt 1800 ];
then
  # Login to ChargePoint
  bash chargepoint_auth.sh
  LAST_AUTH_TIME_SEC=$(date +%s)
fi

for STATION_ID in "${STATION_IDS[@]}"
do
  STATION_DIR=$DATE/$STATION_ID
  mkdir -p "$STATION_DIR"

  # or do whatever with individual element of the array
  TIMESTAMP=`date '+%H:%M:%S'`
  HTTP_RESPONSE=$(bash chargepoint_stationinfo.sh "$STATION_ID")
  
  # extract the status
  HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

  if [ "$HTTP_STATUS" = "200" ];
  then
    # extract the body
    HTTP_BODY=$(echo $HTTP_RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')

    echo "$HTTP_BODY" | json_pp | zip -9q > "$STATION_DIR/${TIMESTAMP}.zip"     

    cp -f "$STATION_DIR/${TIMESTAMP}.zip" "latest/${STATION_ID}.zip.new"
    mv -f "latest/${STATION_ID}.zip.new" "latest/${STATION_ID}.zip"
  fi

done

sleep 60

done

# You can access them using echo "${arr[0]}", "${arr[1]}" also


