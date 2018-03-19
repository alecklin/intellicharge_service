package com.snc.intellicharge.data

import com.snc.intellicharge.config.ConfigProps
import com.snc.intellicharge.ml.ChargePointModelManager
import com.snc.intellicharge.util.DateUtil
import groovy.json.JsonSlurper
import groovy.time.TimeCategory
import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.text.SimpleDateFormat

@Slf4j
@Canonical
//@InheritConstructors
@Service
class ChargePointDataFileToLibSVM {

    static Map<String, Integer> OUTLET_STATUS_MAP = ['unknown': 0, 'available': 1, 'in_use': 2]
    static Map<String, Integer> OUTLET_NAME_MAP = ['outlet_1': 1, 'outlet_2': 2]
    static Map<String, Integer> FEATURE_INDEX_MAP = [deviceId: 1, outletId: 2, monthOfYear: 6, dayOfMonth: 7, dayofWeek: 1, hourOfDay: 2, minOfHour: 3, holiday: 9]

    static def FILENAME_ASC_COMP = {File f1, File f2 ->
        f1.getName().compareTo(f2.getName())
    }

    @Autowired
    ConfigProps fConfigProps

    @Autowired
    ChargePointModelManager fChargePointModelManager

    @Autowired
    DateUtil fDateUtil

    void convertZipToSVMStatusCountByTimestampFile(boolean newMode, File timestampFile, File outputDir,
                                                   Map<Object, Writer> outletFQIdToWriterMap,
                                                   Map<Object, Integer> outletFQHourToLastUniqueCondensedTimestampMap) {

//        println """ChargePointDataFileToLibSVM.convertZipToSVMStatusCountByTimestampFile;
//newMode: $newMode;
//timestampFile: ${timestampFile.getPath()};
//outputDir: ${outputDir.getPath()}"""
        try {
            new ZipFile(timestampFile).withCloseable { ZipFile zf ->
                zf.getEntries().findAll { ZipArchiveEntry entry -> !entry.directory }.each {
                    ZipArchiveEntry zaf ->
//                                println zaf.name
                        zf.getInputStream(zaf).withCloseable { InputStream zfIs ->
                            InputStreamReader zfIsr = new InputStreamReader(zfIs, 'UTF-8')
                            def json = new JsonSlurper().parse(zfIsr)
                            String deviceIdStr = json[0].summaries[0].device_id

                            String timeStr = json[0].time
                            SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.SSS')
                            sdf.setTimeZone(TimeZone.getTimeZone('GMT'))
                            Date time = sdf.parse(timeStr)
                            Calendar tCalendar = Calendar.getInstance();
                            tCalendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
                            tCalendar.setTime(time)
                            int month = tCalendar.get(Calendar.MONTH)
                            int dayOfMonth = tCalendar.get(Calendar.DAY_OF_MONTH)
                            int dayOfWeek = tCalendar.get(Calendar.DAY_OF_WEEK)
                            int hourOfDay = tCalendar.get(Calendar.HOUR_OF_DAY)
                            int minOfHour = tCalendar.get(Calendar.MINUTE)
                            int condensedMinOfHour = minOfHour / fChargePointModelManager.minToCondenseBy
                            boolean isHoliday = false

                            String outlet1Status = json[0].summaries[0].port_status.outlet_1.status
                            String outlet2Status = json[0].summaries[0].port_status.outlet_2.status


                            //println "device_id: ${deviceIdStr}; time: ${timeStr}; time_ms: ${time.getTime()}; outlet_1_status: ${outlet1Status}; outlet_2_status: ${outlet2Status}"
                            //println "month: $month; dayOfMonth: $dayOfMonth; dayofWeek: $dayOfWeek; hourOfDay: $hourOfDay; minOfHour: $minOfHour"

// loop each outlet id
                            fChargePointModelManager.outletIds.each { String outletId ->
                                def outletFqId = [deviceId: deviceIdStr, outletId: outletId]
                                Writer outletFqIdWriter = outletFQIdToWriterMap[outletFqId]
                                if (!outletFqIdWriter) {
                                    File outletRecordFile = new File(outputDir, "${deviceIdStr}_${outletId}")
                                    if (newMode) {
                                        outletRecordFile.delete()
                                    }
                                    outletFqIdWriter = outletRecordFile.newWriter('UTF-8', !newMode)
                                    outletFQIdToWriterMap[outletFqId] = outletFqIdWriter
                                }

                                // for device and outlet, track unique condensed timestamp
                                def outletFqUniqueHour = [month: month, dayOfMonth: dayOfMonth, dayOfWeek: dayOfWeek, hourOfDay: hourOfDay] << outletFqId

                                // check if condensed minute chunk already written
                                Integer lastMinChunkSeen = outletFQHourToLastUniqueCondensedTimestampMap[outletFqUniqueHour]
                                if (lastMinChunkSeen != condensedMinOfHour) {

                                    // track last min chunk seen for given day
                                    outletFQHourToLastUniqueCondensedTimestampMap[outletFqUniqueHour] = condensedMinOfHour

                                    // write outlet record
                                    String outletStatus = json[0].summaries[0].port_status[outletId].status
                                    def availRecord = "$outletStatus $month $dayOfMonth $dayOfWeek $hourOfDay ${condensedMinOfHour}"
                                    outletFqIdWriter.write("$availRecord\n")
                                } else {
                                    //println "Skipping record: $lastMinChunkSeen == $condensedMinOfHour"
                                }
                            }
                        }
                }
            }
        } catch (Exception ex) {
            println "${timestampFile.getPath()} is corrupt: ${ex.getMessage()}"
//            ex.printStackTrace()
        }
    }

    boolean convertToSVMStatusCountByTimestampFile(String dataDir, File outputDir, Set deviceDirIgnores) {
        // date dirs e.g. 2017-10-31
        Set<String> dateDirSet = new HashSet<>()
        File dateDirSetFile = new File('/Users/chowie.lin/chargepoint/dateDirSet')

        // for each device and outlet, track last seen condensed min of hour
        Map<Object, Integer> outletFQHourToLastUniqueCondensedTimestampMap = [:]

        // load outletFQHourToLastUniqueCondensedTimestampMap from disk if exists
        File outletFQHourToLastUniqueCondensedTimestampMapFile = new File('/Users/chowie.lin/chargepoint/outletFQHourToLastUniqueCondensedTimestampMap')

        // newMode true means to start from scratch, else load dateDirSet and outletFQHourToLastUniqueCondensedTimestampMap from disk
        boolean newMode = true

        // regenerate every x days as it builds up
        File mlCacheCreateTimeFile = new File('/Users/chowie.lin/chargepoint/mlCacheCreateTime')
        use (TimeCategory) {
            if (mlCacheCreateTimeFile.isFile()) {
                println "convertToSVMStatusCountByTimestampFile; ${mlCacheCreateTimeFile.getPath()} exists"
                Date createDate = new Date(mlCacheCreateTimeFile.lastModified())
                Date currentDate = new Date()
                newMode = currentDate - createDate > fConfigProps.mlCacheExpireDurationDays.days
                if (newMode) {
                    println "convertToSVMStatusCountByTimestampFile; ${mlCacheCreateTimeFile.getPath()} expired, recreating"
                    mlCacheCreateTimeFile.delete()
                    mlCacheCreateTimeFile.createNewFile()
                }
                println "convertToSVMStatusCountByTimestampFile; newMode: $newMode; currentDate: $currentDate; createDate: $createDate; days threshold: ${fConfigProps.mlCacheExpireDurationDays.days}"
            } else {
                println "convertToSVMStatusCountByTimestampFile ${mlCacheCreateTimeFile.getPath()} doesn't exist, creating it"
                // create time file to mark cache creation date
                mlCacheCreateTimeFile.createNewFile()
                newMode = true
            }
        }

        println "convertToSVMStatusCountByTimestampFile; newMode: $newMode"

        // if newMode == false, load from disk
        if (!newMode) {
            if (dateDirSetFile.isFile()) {
                new ObjectInputStream(new FileInputStream(dateDirSetFile)).withCloseable { ois ->
                    dateDirSet = ois.readObject()
                }
            } else {
                newMode = false
            }

            if (outletFQHourToLastUniqueCondensedTimestampMapFile.isFile()) {
                new ObjectInputStream(new FileInputStream(outletFQHourToLastUniqueCondensedTimestampMapFile)).withCloseable { ois ->
                    outletFQHourToLastUniqueCondensedTimestampMap = ois.readObject()
                }
            } else {
                newMode = false
            }
        }

        // if newMode, then delete all dirs and start from scratch
        if (newMode) {
            outputDir.deleteDir()
            outputDir.mkdir()

            dateDirSet = new HashSet<>()

            // for each device and outlet, track last seen condensed min of hour
            outletFQHourToLastUniqueCondensedTimestampMap = [:]
        }
        File dataDirFile = new File(dataDir)

        // deviceId_outletId = Writer
        Map<Object, Writer> outletFQIdToWriterMap = [:]

        File[] dateDirs = dataDirFile.listFiles(new FileFilter() {
            Date currentDate = new Date()

            @Override
            boolean accept(File pathname) {
                use(TimeCategory) {
                    String dateStr = pathname.getName()
                    if (pathname.getName().startsWith('201')) {
                        Date dateDirDate = Date.parse('yyyy-MM-dd', dateStr, TimeZone.getTimeZone('America/Los_Angeles'))
                        if ((currentDate - dateDirDate) < fConfigProps.mlTrainDataDurationDays.days) {
                            boolean alreadyProcessedDate = dateDirSet.contains(dateStr)
                            println "ChargePointDataFileToLibSVM.convertToSVMStatusCountByTimestampFile dateDirs already processed?: $dateDirDate: $alreadyProcessedDate"
                            return !alreadyProcessedDate
                        } else {
                            println "ChargePointDataFileToLibSVM.convertToSVMStatusCountByTimestampFile dateDirs date: $dateDirDate not within ${fConfigProps.mlTrainDataDurationDays.days} days of ML data lookback threshold"
                        }
                    }
                    false
                }
            }
        })

        Arrays.sort(dateDirs, FILENAME_ASC_COMP)

        if (dateDirs.size() == 1) {
            println 'convertToSVMStatusCountByTimestampFile; dateDirs size was 1 so not doing any work'
            return false
        }

        // loop through date dirs e.g. 2017-10-06
        dateDirs.eachWithIndex { File dateDir, int dateDirIdx ->

            // do not process last date dir as it may not be complete for that day
            if (dateDirIdx + 1 < dateDirs.length) {
                dateDirSet.add(dateDir.getName())

                File[] deviceDirs = dateDir.listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(File dir, String name) {
                        !deviceDirIgnores.contains(name)
                    }
                })
                Arrays.sort(deviceDirs, FILENAME_ASC_COMP)

                // loop through device dirs e.g. 121403
                deviceDirs.each {
                    File deviceDir ->
                        File[] timestampFiles = deviceDir.listFiles(new FilenameFilter() {
                            @Override
                            boolean accept(File dir, String name) {
                                true
//                            name == '00:00:54.zip'
                            }
                        })
                        Arrays.sort(timestampFiles, FILENAME_ASC_COMP)

                        // loop through timestamp zips e.g. 23:10:00.zip
                        timestampFiles.each {
                            File timestampFile ->
                                convertZipToSVMStatusCountByTimestampFile(newMode, timestampFile, outputDir, outletFQIdToWriterMap, outletFQHourToLastUniqueCondensedTimestampMap)
                        }
                }
            }
        }

        // serialize outletFQHourToLastUniqueCondensedTimestampMap
        // first delete old file
        outletFQHourToLastUniqueCondensedTimestampMapFile.delete()

        // serialize to disk
        new ObjectOutputStream(new FileOutputStream(outletFQHourToLastUniqueCondensedTimestampMapFile)).withCloseable { oos ->
            oos.writeObject(outletFQHourToLastUniqueCondensedTimestampMap)
            println 'serialized outletFQHourToLastUniqueCondensedTimestampMap'
        }

        // serialize dateDirSet
        dateDirSetFile.delete()
        new ObjectOutputStream(new FileOutputStream(dateDirSetFile)).withCloseable { oos ->
            oos.writeObject(dateDirSet)
            println 'serialized dateDirSet'
        }

        // close all outlet File writers
        outletFQIdToWriterMap.each {
            k, Writer writer ->
                try {
                    writer.close()
                    println "closed writer for outlet id: $k"
                } catch (Exception ex) {
                    ex.printStackTrace()
                }
        }
        return true
    }

    // one file per timestamp with avail totals
    // source files: e.g. 121407_outlet_1
    // content e.g.: in_use 9 6 6 16 0
    void convertToOutletAvailFileToTimestampAvailCountFiles(File outletFilesDir, File outputDir) {
        println "convertToOutletAvailFileToTimestampAvailCountFiles; outletFilesDir: ${outletFilesDir.getPath()}; outputDir: ${outputDir.getPath()}"
        // delete old timestamp files
        outputDir.deleteDir()
        outputDir.mkdir()

        File[] outletFqFiles = outletFilesDir.listFiles()
        Arrays.sort(outletFqFiles, FILENAME_ASC_COMP)

        // loop through outlet files
        outletFqFiles.each { File outletFqFile ->
            def (String deviceId, String outletId) = outletFqFile.getName().split('_', 2)
            outletFqFile.withReader('UTF-8') { Reader reader ->
                reader.each { String line ->
                    def (String statusStr, String monthStr, String dayOfMonthStr, String dayOfWeekStr, String hourStr, String condensedMinStr) = line.split(' ')
                    monthStr = String.format("%02d", Integer.parseInt(monthStr))
                    dayOfMonthStr = String.format("%02d", Integer.parseInt(dayOfMonthStr))
                    dayOfWeekStr = String.format("%02d", Integer.parseInt(dayOfWeekStr))
                    hourStr = String.format("%02d", Integer.parseInt(hourStr))
                    condensedMinStr = String.format("%02d", Integer.parseInt(condensedMinStr))
                    File timestampFile = new File(outputDir, "${monthStr}_${dayOfMonthStr}_${dayOfWeekStr}_${hourStr}_${condensedMinStr}")
                    timestampFile.createNewFile()

                    // append line to timestamp file only if status available
                    if (statusStr == 'available') {
                        timestampFile.append("${deviceId}_${outletId}\n")
                    }
                }
            }
        }
    }

    void convertTimestampAvailCountFilesToSVM(File timestampFilesDir, File outputSVMFile) {
        println "convertTimestampAvailCountFilesToSVM timestampFilesDir: ${timestampFilesDir.getPath()}; outputSVMFile: ${outputSVMFile.getPath()}"
        File[] timestampFiles = timestampFilesDir.listFiles()
        Arrays.sort(timestampFiles, FILENAME_ASC_COMP)

        outputSVMFile.delete()
        outputSVMFile.withWriter('UTF-8') { Writer writer ->
            timestampFiles.each { File timestampFile ->
                // filename: 09_06_06_16_00
                def (String monthStr, String dayOfMonthStr, String dayOfWeekStr, String hourStr, String condensedMinStr) = timestampFile.getName().split('_')
                int dayOfWeek = Integer.parseInt(dayOfWeekStr, 10)
                int hour = Integer.parseInt(hourStr, 10)
                int condensedMin = Integer.parseInt(condensedMinStr, 10)

                // count # of lines in each file to get avail count for a timestamp
                int availCount = 0
                timestampFile.withReader('UTF-8') { Reader reader ->
                    reader.each {
                        availCount++
                    }
                }
                // DEBUG
                if (availCount > 32) {
                    println "${timestampFile.getPath()} has avail count > 30!"
                }

                writer.write("${availCount} 1:${dayOfWeek} 2:${hour + 1} 3:${condensedMin + 1}\n")
            }
        }
    }
}
