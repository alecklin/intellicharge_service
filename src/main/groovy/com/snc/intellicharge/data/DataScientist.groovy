package com.snc.intellicharge.data

import com.snc.intellicharge.ml.ChargePointModelManager
import com.snc.intellicharge.schedule.Scheduler
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.InheritConstructors
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Canonical
//@InheritConstructors
@Service
class DataScientist {
    @Autowired
    ChargePointDataFileToLibSVM fChargePointDataFileToLibSVM

    @Autowired
    Scheduler fScheduler

    @Autowired
    ChargePointModelManager fChargePointModelManager

    boolean convertJsonToCountAvailSVM() {
        String chargePointDataDir = '/Users/chowie.lin/chargepoint'
        File outletFilesDir = new File('/Users/chowie.lin/chargepoint/outlets')
        println "DataScientist.convertJsonToCountAvailSVM from: $chargePointDataDir to ${outletFilesDir.getPath()}"
        fChargePointDataFileToLibSVM.convertToSVMStatusCountByTimestampFile(chargePointDataDir, outletFilesDir, ['122601'].toSet())
    }

    void convertJsonToCountAvailSVM2() {
        File outletFilesDir = new File('/Users/chowie.lin/chargepoint/outlets')
        File timestampFilesDir = new File('/Users/chowie.lin/chargepoint/timestamps')
        println "DataScientist.convertJsonToCountAvailSVM2 from: ${outletFilesDir.getPath()} to ${timestampFilesDir.getPath()}"
        fChargePointDataFileToLibSVM.convertToOutletAvailFileToTimestampAvailCountFiles(outletFilesDir, timestampFilesDir)
    }

    void convertJsonToCountAvailSVM3() {
        File timestampFilesDir = new File('/Users/chowie.lin/chargepoint/timestamps')
        File timestampAvailFile = new File('/Users/chowie.lin/chargepoint/timestampavail')
        println "DataScientist.convertJsonToCountAvailSVM3 from: ${timestampFilesDir.getPath()} to ${timestampAvailFile.getPath()}"
        fChargePointDataFileToLibSVM.convertTimestampAvailCountFilesToSVM(timestampFilesDir, timestampAvailFile)
    }

    int[] fetchStatusCountsFromLatestDatapoint() {
        int availCount = 0
        int inuseCount = 0
        int unknownCount = 0
        int totalCount = 0

        // for each .zip file, get charge ports status and add up available, unknown, in_use ports
        new File('/Users/chowie.lin/chargepoint/latest').eachFileMatch({ String name ->
            !name.startsWith('122601.')
        }) { File timestampFile ->
            ZipFile zf = null
            try {
                try {
                    zf = new ZipFile(timestampFile)
                    zf.getEntries().findAll { ZipArchiveEntry entry -> !entry.directory }.each {
                        ZipArchiveEntry zaf ->
//                                println zaf.name
                            InputStream zfIs = zf.getInputStream(zaf)
                            try {
                                InputStreamReader zfIsr = new InputStreamReader(zfIs, 'UTF-8')
                                def json = new JsonSlurper().parse(zfIsr)
                                String deviceIdStr = json[0].summaries[0].device_id
                                String outlet1Status = json[0].summaries[0].port_status.outlet_1.status
                                String outlet2Status = json[0].summaries[0].port_status.outlet_2.status

                                switch (outlet1Status) {
                                    case 'unknown':
                                        unknownCount++
                                        break
                                    case 'available':
                                        availCount++
                                        break
                                    case 'in_use':
                                        inuseCount++
                                        break
                                    default:
                                        println 'ERROR: Outlet status not valid'
                                }
                                switch (outlet2Status) {
                                    case 'unknown':
                                        unknownCount++
                                        break
                                    case 'available':
                                        availCount++
                                        break
                                    case 'in_use':
                                        inuseCount++
                                        break
                                    default:
                                        println 'ERROR: Outlet status not valid'
                                }

                            } finally {
                                zfIs.close()
                            }
                    }
                } finally {
                    if (zf != null) {
                        zf.close()
                    }
                }
            } catch (Exception ex) {
                println "Corrupt .zip file: ${timestampFile.getPath()}"
            }
        }

        totalCount = availCount + inuseCount + unknownCount
        [availCount, inuseCount, unknownCount, totalCount]
    }
}
