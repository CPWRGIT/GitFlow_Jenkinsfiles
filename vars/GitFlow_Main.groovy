#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL
import groovy.xml.*

def execParms
def configFile

def call(Map parms) {

    parms.demoEnvironment   = parmsarms.demoEnvironment.toLowerCase()
    configFile              = './config/gitflow.yml'    

    def settings = [:]

    node {

        settings     = initializeEnvSettings(configFile, parms)

        cloneRepo(runtimeParms)

        if(BRANCH_NAME.startsWith("feature")) {

            def assignmentId = 'GFLD0000001'

            loadMainframeCode(runtimeParms, envSettings)

            //assignmentId = getAssignmentId(envSettings.automaticBuildFile)

            if (assignmentId != null) {

                buildMainframeCode(envSettings.hostConnection, runtimeParms.cesCredentialsId)

                runUnitTests(runtimeParms, envSettings)

                runIntegrationTests(runtimeParms, envSettings)

                runSonarScan(runtimeParms, envSettings)
            }
        }
        else if(BRANCH_NAME.startsWith("release")) {

            def releaseAssignmentId
            def cesToken
            def xlrReleaseNumber

            runtimeParms = extendRuntimeParms(runtimeParms)

            loadMainframeCode(fromCommit, toCommit, runtimeParms, envSettings)

            releaseAssignmentId = getAssignmentId(envSettings.automaticBuildFile)

            if (releaseAssignmentId != null) {

                buildMainframeCode(envSettings.hostConnection, runtimeParms.cesCredentialsId)

                ispwReleaseNumber   = determineIspwReleaseNumber(currentTag)
                cesToken            = extractToken(runtimeParms.cesCredentialsId)

                startXlr(ispwReleaseNumber, releaseAssignmentId, cesToken, runtimeParms, envSettings)
            
            }
        }
        else {

            runSonarScan(runtimeParms, encSettings)
        }
    }
}

def initialize(configFile, parms) {

    def settings

    settings.demoEnvironment    = parms.demoEnvironment
    settings.hci.credentialsId  = parms.hostCredentialsId
    settings.ces.credentialsId  = parms.cesCredentialsId
    settings.git.repoUrl        = parms.gitRepoUrl
    settings.git.credentialsId  = parms.gitCredentialsId
    settings.coco.repo          = parms.ccRepo

    stage("Imitialization") {

        cleanWs()

        def tmpSettings = readYaml(text: libraryResource(configFile))
        settings        = tmpSetting.executionEnvironments[settings.demoEnvironment]
        settings        = addFolderNames(settings)
        settings        = addCoCoParms(settings)
    }

    return settings
}

def addFolderNames(settings) {

    settings.ispw.configFile        = settings.ispw.configFile.folder           + '/' + settings.ispw.configFile.name
    settings.ttt.rootFolder         = settings.ispw.mfProject.rootFolder        + '/' + settings.ttt.folders.root
    settings.ttt.vtFolder           = settings.ttt.rootFolder                   + '/' + settings.ttt.folders.virtualizedTests
    settings.ttt.nvtFolder          = settings.ttt.rootFolder                   + '/' + synchConfig.ttt.folders.nonVirtualizedTests
    settings.coco.sources           = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.cobolFolder      = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.copybookFolder   = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.resultsFolder    = settings.ttt.results.sonar.folder 
    settings.sonar.resultsFileVt    = settings.ttt.folders.virtualizedTests     + '.' + settings.ttt.results.sonar.fileNameBase
    settings.sonar.resultsFileNvt   = settings.ttt.folders.nonVirtualizedTests  + '.' + settings.ttt.results.sonar.fileNameBase
    settings.sonar.resultsFileList  = []        
    settings.sonar.codeCoverageFile = settings.coco.results.sonar.folder        + '/' + settings.coco.results.sonar.file
    settings.jUnitResultsFile       = synchConfig.ttt.results.jUnit.folder      + '/' + synchConfig.ttt.results.jUnit.file

    return settings
}

def addIspwConfigFileContent(settings)  {
    
    def tmpText     = readFile(file: settings.ipsw.configFile)

    // remove the first line (i.e. use the the substring following the first carriage return '\n')
    tmpText         = tmpText.substring(tmpText.indexOf('\n') + 1)
    def ispwConfig  = readYaml(text: tmpText).ispwApplication

    settings.ispw.runtimeConfig = ispwConfig.runtimeConfig
    settings.ispw.stream        = ispwCOnfig.stream
    settings.ispw.application   = ispwConfig.application
    settings.ispw.appQualifier  = settings.ispw.libraryQualifier    + ispwConfig.ispwApplication.application

    return settings
}

def addCoCoParms(settings) {

    def ccSystemId
    def CC_SYSTEM_ID_MAX_LEN    = 15
    def CC_TEST_ID_MAX_LEN      = 15

    if(BRANCH_NAME.length() > CC_SYSTEM_ID_MAX_LEN) {
        ccSystemId  = BRANCH_NAME.substring(BRANCH_NAME.length() - CC_SYSTEM_ID_MAX_LEN)
    }
    else {
        ccSystemId  = executionBranch
    }
    
    settings.coco.systemId  = ccSystemId
    settings.coco.testId    = ccTestId    = BUILD_NUMBER

    return settings
}

def extendRuntimeParms(parms) {

    def hostCreds       = extractCredentials(parms.hostCredentialsId) 
    parms.hostUser      = hostCreds[0]
    parms.hostPassword  = hostCreds[1]

    def gitCreds        = extractCredentials(parms.gitCredentialsId)

    parms.gitUser       = gitCreds[0]
    parms.gitPassword   = gitCreds[1]

    def commitInfo      = determineCommitInfo()

    parms.fromCommit    = commitInfo['fromCommit']
    parms.toCommit      = commitInfo['toCommit']
    parms.currentTag    = commitInfo['currentTag']

    return parms
}

def cloneRepo(runtimeParms) {

    stage ('Checkout') {
        // checkout scm
        checkout(
            changelog:  false, 
            poll:       false, 
            scm:        [
                $class:             'GitSCM', 
                branches:           [[name: BRANCH_NAME]], 
                extensions:         [], 
                userRemoteConfigs:  [[
                    credentialsId:  runtimeParms.gitCredentialsId, 
                    url:            runtimeParms.gitRepoUrl
                ]]
            ]
        )
    }
}

def extractCredentials(credentialsId) {

    def credentialsInfo = []

    withCredentials(
        [
            usernamePassword(
                credentialsId:      credentialsId, 
                passwordVariable:   'tmpPw', 
                usernameVariable:   'tmpUser'
            )
        ]
    )
    {
        credentialsInfo[0]  = tmpUser
        credentialsInfo[1]  = tmpPw
    }

    return credentialsInfo
}

def extractToken(credentialsId) {

    def token

    withCredentials(
        [
            string(
                credentialsId:  credentialsId, 
                variable:       'tmpToken'
            )
        ]
    )
    {
        token = tmpToken
    } 
    return token
}

def determineCommitInfo() {

    def commitInfo  = [:]

    def currentTag  = bat(returnStdout: true, script: 'git describe --tags').split("\n")[2].trim()
    echo "Determined Current Tag: " + currentTag
    commitInfo['currentTag'] = currentTag

    def previousTag = bat(returnStdout: true, script: 'git describe --abbrev=0 --tags ' + currentTag + '~').split("\n")[2].trim()
    echo "Determined Previous Tag: " + previousTag
    commitInfo['previousTag'] = previousTag

    def fromCommit  = bat(returnStdout: true, script: 'git rev-list -1 ' + previousTag).split("\n")[2].trim()
    echo "Determined From Commit: " + fromCommit
    commitInfo['fromCommit'] = fromCommit

    def toCommit = bat(returnStdout: true, script: 'git rev-parse --verify HEAD')split("\n")[2].trim()
    echo "Determined To Commit: " + toCommit
    commitInfo['toCommit'] = toCommit

    return commitInfo
}

def loadMainframeCode(Map runtimeParms, Map envSettings) {
    echo "Load Feature Code"
}

def loadMainframeCode(String fromCommit, String toCommit, Map runtimeParms, Map envSettings) {

    stage("Mainframe Load") {

        def output = bat(
            returnStdout: true,
            script: envSettings.cliPath + '/IspwCLI.bat ' +  
                '-operation syncGitToIspw ' + 
                '-host "' + envSettings.hostName + '" ' +
                '-port "' + envSettings.hostPort + '" ' +
                '-id "' + runtimeParms.hostUser + '" ' +
                '-pass "' + runtimeParms.hostPassword + '" ' +
                '-protocol None ' +
                '-code 1047 ' +
                '-timeout "0" ' +
                '-targetFolder ./ ' +
                '-data ./TopazCliWkspc ' +
                '-ispwServerConfig ' + envSettings.ispwRuntimeConfig + ' ' +
                '-ispwServerStream ' + envSettings.ispwStream + ' ' +
                '-ispwServerApp ' + envSettings.ispwApplication + ' ' +
                '-ispwCheckoutLevel RLSE ' +
                '-ispwConfigPath ' + envSettings.ispwConfigFile + ' ' +
                '-ispwContainerCreation per-branch ' +
                '-gitUsername "' + runtimeParms.gitUser + '" ' +
                '-gitPassword "' + runtimeParms.gitPassword + '" ' +
                '-gitRepoUrl "' + runtimeParms.gitRepoUrl + '" ' +
                '-gitBranch ' + BRANCH_NAME + ' ' +
                '-gitFromHash ' + fromCommit + ' ' +
                '-gitLocalPath ./ ' +
                '-gitCommit ' + toCommit
        )

        echo output

    //     gitToIspwIntegration( 
    //         connectionId:       'de2ad7c3-e924-4dc2-84d5-d0c3afd3e756', //synchConfig.environment.hci.connectionId,                    
    //         credentialsId:      'ea48408b-b2be-4810-8f4e-5b5f35977eb1', //pipelineParms.hostCredentialsId,                     
    //         runtimeConfig:      'iccga', //ispwConfig.ispwApplication.runtimeConfig,
    //         stream:             'GITFLOW', //ispwConfig.ispwApplication.stream,
    //         app:                'GITFLOWE', //ispwConfig.ispwApplication.application, 
    //         branchMapping:      'release/** => RLSE,per-branch', //branchMappingString,
    //         ispwConfigPath:     './GenApp_MainframeCore/ispwconfig.yml', //ispwConfigFile, 
    //         gitCredentialsId:   '67a3fb18-073f-498b-adee-1a3c75192745', //pipelineParms.gitCredentialsId, 
    //         gitRepoUrl:         'https://github.com/CPWRGIT/GitFlow_HDDRXM0.git' //pipelineParms.gitRepoUrl
    //     )

    }
}

def getAssignmentId(buildFile) {

    def buildFileContent

    try {
    
        buildFileContent = readJSON(file: buildFile)

        return buildFileContent.containerId
    }
    catch(Exception e) {

        echo "[Info] - No Automatic Build Params file was found.  Meaning, no mainframe sources have been changed.\n" +
        "[Info] - Mainframe Build and Test steps will be skipped. Sonar scan will be executed against code only."

        return null
    }
}

def buildMainframeCode(hostConnection, cesCredentialsId) {

    stage("Mainframe Build") {

        try{
            ispwOperation(
                connectionId:           hostConnection, //'38e854b0-f7d3-4a8f-bf31-2d8bfac3dbd4', 
                credentialsId:          cesCredentialsId,       
                consoleLogResponseBody: true, 
                ispwAction:             'BuildTask', 
                ispwRequestBody:        '''
                    runtimeConfiguration=ic2ga
                    buildautomatically = true
                '''
            )
        }
        catch(Exception e) {
            echo "[Error] - Error occurred during Build of Mainframe Code: \n" +
                e
        }
    }
}

def runUnitTests(Map runtimeParms, Map envSettings) {

    stage("Run Unit Test") {

        echo "[Info] - Execute Unit Tests."

        totaltest(
            serverUrl:                          envSettings.ces.url, 
            serverCredentialsId:                runtimeParms.hostCredentialsId, 
            credentialsId:                      runtimeParms.hostCredentialsId, 
            environmentId:                      envSettings.ttt.environmentIds.virtualized,
            localConfig:                        false, 
            folderPath:                         envSettings.ttt.vtFolder, 
            recursive:                          true, 
            selectProgramsOption:               true, 
            jsonFile:                           envSettings.ispw.changedProgramsFile,
            haltPipelineOnFailure:              false,                 
            stopIfTestFailsOrThresholdReached:  false,
            createJUnitReport:                  true, 
            createReport:                       true, 
            createResult:                       true, 
            createSonarReport:                  true,
            contextVariables:                   '"ispw_app=' + envSettings.ispw.appQualifier + ',ispw_level=FEAT"',
            collectCodeCoverage:                true,
            collectCCRepository:                runtimeParms.ccRepo,
            collectCCSystem:                    envSettings.coco.systemId,
            collectCCTestID:                    envSettings.coco.testId,
            clearCodeCoverage:                  false,
            logLevel:                           'INFO'
        )

        junit(
            allowEmptyResults:  true, 
            keepLongStdio:      true, 
            testResults:        envSettings.ttt.results.jUnit.folder + '/*.xml'
        )
    }
}

def runIntegrationTests(Map runtimeParms, Map envSettings) {

    echo "[Info] - Execute Module Integration Tests."

    envSettings.ttt.environmentIds.nonVirtualized.each {

        def envType     = it.key
        def envId       = it.value

        totaltest(
            connectionId:                       settings.hci.connectionId,
            credentialsId:                      runtimeParms.hostCredentialsId,             
            serverUrl:                          settings.ces.url, 
            serverCredentialsId:                runtimeParms.hostCredentialsId, 
            environmentId:                      envId, 
            localConfig:                        false,
            folderPath:                         envSettings.ttt.nvtFolder, 
            recursive:                          true, 
            selectProgramsOption:               true, 
            jsonFile:                           envSettings.ispw.changedProgramsFile,
            haltPipelineOnFailure:              false,                 
            stopIfTestFailsOrThresholdReached:  false,
            createJUnitReport:                  true, 
            createReport:                       true, 
            createResult:                       true, 
            createSonarReport:                  true,
            // contextVariables:                   '"nvt_ispw_app=' + applicationQualifier + 
            //                                     ',nvt_ispw_level1=' + synchConfig.ttt.loadLibQualfiers[ispwTargetLevel].level1 + 
            //                                     ',nvt_ispw_level2=' + synchConfig.ttt.loadLibQualfiers[ispwTargetLevel].level2 + 
            //                                     ',nvt_ispw_level3=' + synchConfig.ttt.loadLibQualfiers[ispwTargetLevel].level3 + 
            //                                     ',nvt_ispw_level4=' + synchConfig.ttt.loadLibQualfiers[ispwTargetLevel].level4 + 
            //                                     '"',                
            collectCodeCoverage:                true,
            collectCCRepository:                runtimeParms.ccRepo,
            collectCCSystem:                    envSettings.coco.systemId,
            collectCCTestID:                    envSettings.coco.testId,
            clearCodeCoverage:                  false,
            logLevel:                           'INFO'
        )
    }
}

def runSonarScan(Map runtimeParms, Map envSettings) {
    echo "Running Sonar Scan"
}

def determineIspwReleaseNumber(tag) {

    def releaseNumber       = tag.substring(1, 9)
    def releaseNumberParts  = releaseNumber.split("[.]")

    return releaseNumberParts[0] + releaseNumberParts[1] + releaseNumberParts[2]
}

def startXlr(releaseNumber, assignmentId, cesToken, runtimeParms, envSettings) {

    echo "Start XLR with: "
    echo 'CES_Token: ' + cesToken
    echo 'ISPW_Runtime: ' + envSettings.ispwRuntimeConfig
    echo 'ISPW_Application: ' + envSettings.ispwApplication
    echo 'ISPW_Owner: ' + runtimeParms.hostUser
    echo 'ISPW_Assignment: ' + assignmentId
    echo 'Jenkins_CES_Credentials: ' + runtimeParms.cesCredentialsId
    echo 'Release Number: ' + releaseNumber

    xlrCreateRelease(
        releaseTitle:       "GitFlow - Release for ${runtimeParms.hostUser}", 
        serverCredentials:  'admin', 
        startRelease:       true, 
        template:           'GitFlow/GitFlow_Release', 
        variables: [
            [
                propertyName:   'CES_Token', 
                propertyValue:  cesToken
            ], 
            [
                propertyName:   'ISPW_Release_Number', 
                propertyValue:  releaseNumber
            ], 
            [
                propertyName:   'ISPW_Assignment', 
                propertyValue:  assignmentId
            ], 
            [
                propertyName:   'ISPW_Runtime', 
                propertyValue:  envSettings.ispwRuntimeConfig
            ], 
            [
                propertyName:   'ISPW_Application', 
                propertyValue:  envSettings.ispwApplication
            ], 
            [
                propertyName:   'ISPW_Owner', 
                propertyValue:  runtimeParms.hostUser
            ],
            [
                propertyName: 'Jenkins_CES_Credentials', 
                propertyValue: runtimeParms.cesCredentialsId
            ]
            // ,
            // [
            //     propertyName: 'Jenkins_Git_Credentials', 
            //     propertyValue: pipelineParms.gitCredentialsId
            // ] 
        ]
    )    
}