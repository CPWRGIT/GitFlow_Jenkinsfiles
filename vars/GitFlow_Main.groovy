#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL
import groovy.xml.*

def execParms
def configFile

def call(Map parms) {

    parms.demoEnvironment   = parms.demoEnvironment.toLowerCase()
    configFile              = './config/gitflow.yml'    

    def settings = [:]

    node {

        settings     = initializeSettings(configFile, parms)

        cloneRepo(settings)

        if(BRANCH_NAME.startsWith("feature")) {

            def assignmentId = 'GFLD0000001'

            loadMainframeCode(settings)

            //assignmentId = getAssignmentId(envSettings.automaticBuildFile)

            if (assignmentId != null) {

                buildMainframeCode(settings.hci.connectionId, settings.ces.credentialsId)

                runUnitTests(settings)

                runIntegrationTests(settings)

                runSonarScan(settings)
            }
        }
        else if(BRANCH_NAME.startsWith("release")) {

            def releaseAssignmentId
            def cesToken
            def xlrReleaseNumber

            settings = extendSettings(settings)

            loadMainframeCode(fromCommit, toCommit, settings)

            releaseAssignmentId = getAssignmentId(settings.automaticBuildFile)

            if (releaseAssignmentId != null) {

                buildMainframeCode(settings.hci.connectionId, settings.ces.credentialsId, settings.ispw.runtimeConfig)

                ispwReleaseNumber   = determineIspwReleaseNumber(settings.currentTag)
                cesToken            = extractToken(settings.ces.credentialsId)

                startXlr(ispwReleaseNumber, releaseAssignmentId, cesToken, settings)
            
            }
        }
        else {

            runSonarScan(settings)
        }
    }
}

def initializeSettings(configFile, parms) {

    def settings = [:]

    stage("Imitialization") {

        cleanWs()

        def tmpSettings             = readYaml(text: libraryResource(configFile))
        settings                    = tmpSettings.executionEnvironments[parms.demoEnvironment]
        settings                    = addFolderNames(settings)
        settings                    = addCoCoParms(settings)
        settings                    = addIspwConfigFileContent(settings)

        settings.demoEnvironment    = parms.demoEnvironment
        settings.hci.credentialsId  = parms.hostCredentialsId
        settings.ces.credentialsId  = parms.cesCredentialsId
        settings.git                = [:]
        settings.git.repoUrl        = parms.gitRepoUrl
        settings.git.credentialsId  = parms.gitCredentialsId
        settings.coco.repo          = parms.ccRepo
    }

    return settings
}

def addFolderNames(settings) {

    settings.ispw.configFile        = settings.ispw.configFile.folder           + '/' + settings.ispw.configFile.name
    settings.ttt.rootFolder         = settings.ispw.mfProject.rootFolder        + '/' + settings.ttt.folders.root
    settings.ttt.vtFolder           = settings.ttt.rootFolder                   + '/' + settings.ttt.folders.virtualizedTests
    settings.ttt.nvtFolder          = settings.ttt.rootFolder                   + '/' + settings.ttt.folders.nonVirtualizedTests
    settings.coco.sources           = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.cobolFolder      = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.copybookFolder   = settings.ispw.mfProject.rootFolder        +       settings.ispw.mfProject.sourcesFolder
    settings.sonar.resultsFolder    = settings.ttt.results.sonar.folder 
    settings.sonar.resultsFileVt    = settings.ttt.folders.virtualizedTests     + '.' + settings.ttt.results.sonar.fileNameBase
    settings.sonar.resultsFileNvt   = settings.ttt.folders.nonVirtualizedTests  + '.' + settings.ttt.results.sonar.fileNameBase
    settings.sonar.resultsFileList  = []        
    settings.sonar.codeCoverageFile = settings.coco.results.sonar.folder        + '/' + settings.coco.results.sonar.file
    settings.jUnit                  = [:]
    settings.jUnit.resultsFile      = settings.ttt.results.jUnit.folder         + '/' + settings.ttt.results.jUnit.file

    return settings
}

def addIspwConfigFileContent(settings)  {

    def tmpText                 = readFile(file: settings.ispw.configFile)

    // remove the first line (i.e. use the the substring following the first carriage return '\n')
    tmpText                     = tmpText.substring(tmpText.indexOf('\n') + 1)
    def ispwConfig              = readYaml(text: tmpText).ispwApplication
    settings.ispw.runtimeConfig = ispwConfig.runtimeConfig
    settings.ispw.stream        = ispwConfig.stream
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

def extendSettings(settings) {

    def hostCreds           = extractCredentials(settings.hci.credentialsId) 
    settings.hci.user       = hostCreds[0]
    settings.hci.password   = hostCreds[1]

    def gitCreds            = extractCredentials(settings.git.credentialsId)
    settings.git.user       = gitCreds[0]
    settings.git.password   = gitCreds[1]

    def commitInfo          = determineCommitInfo()
    settings.fromCommit    = commitInfo['fromCommit']
    settings.toCommit      = commitInfo['toCommit']
    settings.currentTag    = commitInfo['currentTag']

    return settings
}

def cloneRepo(settings) {

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
                    credentialsId:  settings.git.credentialsId, 
                    url:            settings.git.repoUrl
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

def loadMainframeCode(Map settings) {

    gitToIspwIntegration( 
        connectionId:       settings.hci.connectionId,
        credentialsId:      settings.hci.credentialsId,
        runtimeConfig:      settings.ispw.runtimeConfig,
        stream:             settings.ispw.stream,
        app:                settings.ispw.application, 
        branchMapping:      'feature/** => FEAT,per-branch',
        ispwConfigPath:     settings.ispw.ispwConfigFile,
        gitCredentialsId:   settings.git.credentialsId,
        gitRepoUrl:         settings.git.repoUrl
    )
}

def loadMainframeCode(String fromCommit, String toCommit, Map settings) {

    stage("Mainframe Load") {

        def output = bat(
            returnStdout: true,
            script: settings.jenkins.cliPath + '/IspwCLI.bat ' +  
                '-operation syncGitToIspw ' + 
                '-host "' + settings.hci.hostName + '" ' +
                '-port "' + envSettings.hostPort + '" ' +
                '-id "' + settings.hci.user + '" ' +
                '-pass "' + settings.hci.assword + '" ' +
                '-protocol None ' +
                '-code 1047 ' +
                '-timeout "0" ' +
                '-targetFolder ./ ' +
                '-data ./TopazCliWkspc ' +
                '-ispwServerConfig ' + settings.ispw.runtimeConfig + ' ' +
                '-ispwServerStream ' + settings.ispw.stream + ' ' +
                '-ispwServerApp ' + settings.ispw.application + ' ' +
                '-ispwCheckoutLevel RLSE ' +
                '-ispwConfigPath ' + settings.ispw.configFile + ' ' +
                '-ispwContainerCreation per-branch ' +
                '-gitUsername "' + settings.git.user + '" ' +
                '-gitPassword "' + settings.git.password + '" ' +
                '-gitRepoUrl "' + settings.git.repoUrl + '" ' +
                '-gitBranch ' + BRANCH_NAME + ' ' +
                '-gitFromHash ' + settings.fromCommit + ' ' +
                '-gitLocalPath ./ ' +
                '-gitCommit ' + settings.toCommit
        )

        echo output
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
                    runtimeConfiguration=''' + runtimeConfig + '''
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

def runUnitTests(Map settings) {

    stage("Run Unit Test") {

        echo "[Info] - Execute Unit Tests."

        totaltest(
            serverUrl:                          settings.ces.url, 
            serverCredentialsId:                settings.hci.credentialsId, 
            credentialsId:                      settings.hci.credentialsId, 
            environmentId:                      settings.ttt.environmentIds.virtualized,
            localConfig:                        false, 
            folderPath:                         settings.ttt.vtFolder, 
            recursive:                          true, 
            selectProgramsOption:               true, 
            jsonFile:                           settings.ispw.changedProgramsFile,
            haltPipelineOnFailure:              false,                 
            stopIfTestFailsOrThresholdReached:  false,
            createJUnitReport:                  true, 
            createReport:                       true, 
            createResult:                       true, 
            createSonarReport:                  true,
            contextVariables:                   '"ispw_app=' + envSettings.ispw.appQualifier + ',ispw_level=FEAT"',
            collectCodeCoverage:                true,
            collectCCRepository:                settings.coco.repo,
            collectCCSystem:                    settings.coco.systemId,
            collectCCTestID:                    settings.coco.testId,
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

def runIntegrationTests(Map settings) {

    echo "[Info] - Execute Module Integration Tests."

    envSettings.ttt.environmentIds.nonVirtualized.each {

        def envType     = it.key
        def envId       = it.value

        totaltest(
            connectionId:                       settings.hci.connectionId,
            credentialsId:                      settings.hci.credentialsId,             
            serverUrl:                          settings.ces.url, 
            serverCredentialsId:                settings.hci.credentialsId, 
            environmentId:                      envId, 
            localConfig:                        false,
            folderPath:                         settings.ttt.nvtFolder, 
            recursive:                          true, 
            selectProgramsOption:               true, 
            jsonFile:                           settings.ispw.changedProgramsFile,
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
            collectCCRepository:                settings..coc.repo,
            collectCCSystem:                    settings.coco.systemId,
            collectCCTestID:                    settings.coco.testId,
            clearCodeCoverage:                  false,
            logLevel:                           'INFO'
        )
    }
}

def runSonarScan(Map settings) {
    echo "Running Sonar Scan"
}

def determineIspwReleaseNumber(tag) {

    def releaseNumber       = tag.substring(1, 9)
    def releaseNumberParts  = releaseNumber.split("[.]")

    return releaseNumberParts[0] + releaseNumberParts[1] + releaseNumberParts[2]
}

def startXlr(releaseNumber, assignmentId, cesToken, settings) {

    echo "Start XLR with: "
    echo 'CES_Token: ' + cesToken
    echo 'ISPW_Runtime: ' + settings.ispw.runtimeConfig
    echo 'ISPW_Application: ' + settings.ispw.application
    echo 'ISPW_Owner: ' + settings.hci.user
    echo 'ISPW_Assignment: ' + assignmentId
    echo 'Jenkins_CES_Credentials: ' + settings.ces.credentialsId
    echo 'Release Number: ' + releaseNumber

    xlrCreateRelease(
        releaseTitle:       "GitFlow - Release for ${settings.hci.user}", 
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
                propertyValue:  settings.ispw.runtimeConfig
            ], 
            [
                propertyName:   'ISPW_Application', 
                propertyValue:  settings.ispw.application
            ], 
            [
                propertyName:   'ISPW_Owner', 
                propertyValue:  settings.hci.user
            ],
            [
                propertyName: 'Jenkins_CES_Credentials', 
                propertyValue: settings.ces.credentialsId
            ]
            // ,
            // [
            //     propertyName: 'Jenkins_Git_Credentials', 
            //     propertyValue: pipelineParms.gitCredentialsId
            // ] 
        ]
    )    
}