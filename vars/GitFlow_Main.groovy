def call(Map runtimeParms) {

    def envSettings = [:]
    envSettings['hostConnection']        = '38e854b0-f7d3-4a8f-bf31-2d8bfac3dbd4'
    envSettings['ispwStream']            = 'GITFLOW'
    envSettings['ispwApplication']       = 'GFLD'
    envSettings['automaticBuildFile']    = 'automaticBuildParams.txt'
    envSettings['ispwRuntimeConfig']     = 'ic2ga'
    envSettings['ispwConfigFile']        = './GenApp_MainframeCore/ispwconfig.yml'
    envSettings['cliPath']               = 'C:/TopazCLI201301'
    envSettings['hostName']              = 'cwc2.bmc.com'
    envSettings['hostPort']              = '16196'

    node {

        initialize()

        cloneRepo(runtimeParms)

        if(BRANCH_NAME.startsWith("feature")) {

            def assignmentId = 'GFLD0000001'

            loadMainframeCode(runtimeParms, envSettings)

            //assignmentId = getAssignmentId(envSettings.automaticBuildFile)

            if (assignmentId != null) {

                buildMainframeCode(runtimeParms.hostConnection, runtimeParms.cesCredentialsId)

                runUnitTests(runtimeParms, envSettings)

                runIntegrationTests(runtimeParms, envSettings)

                runSonarScan(runtimeParms, envSettings)
            }
        }
        else if(BRANCH_NAME.startsWith("release")) {

            def fromCommit
            def toCommit
            def currentTag
            def assignmentId
            def ispwOwner
            def cesToken
            def xlrReleaseNumber

            def hostCreds   = extractCredentials(runtimeParms.hostCredentialsId) 

            runtimeParms['hostUser']        = hostCreds[0]
            runtimeParms['hostPassword']    = hostCreds[1]

            def gitCreds    = extractCredentials(runtimeParms.gitCredentialsId)

            runtimeParms['gitUser']         = gitCreds[0]
            runtimeParms['gitPassword']     = gitCreds[1]

            def commitInfo  = determineCommitInfo()

            fromCommit  = commitInfo['fromCommit']
            toCommit    = commitInfo['toCommit']
            currentTag  = commitInfo['currentTag']

            loadMainframeCode(fromCommit, toCommit, runtimeParms, envSettings)

            releaseAssignmentId = getAssignmentId(envSettings.automaticBuildFile)

            if (releaseAssignmentId != null) {

                buildMainframeCode(runtimeParms.hostConnection, runtimeParms.cesCredentialsId)

                ispwReleaseNumber   = determineIspwReleaseNumber(currentTag)
                cesToken            = extractToken(runtimeParms.cesCredentialsId)

                startXlr(ispwReleaseNumber, releaseAssignmentId, cesToken, runtimeParms, envSettings)
            
            }
        }
    }
}

def initialize() {

    stage("Imitialization") {

        cleanWs()
    }
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
    echo "Running Unit Tests"
}

def runIntegrationTests(Map runtimeParms, Map envSettings) {
    echo "Running Integration Tests"
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

    // xlrCreateRelease(
    //     releaseTitle:       "GitFlow - Release for ${runtimeParms.hostUser}", 
    //     serverCredentials:  'admin', 
    //     startRelease:       true, 
    //     template:           'GitFlow/GitFlow_Release', 
    //     variables: [
    //         [
    //             propertyName:   'CES_Token', 
    //             propertyValue:  cesToken
    //         ], 
    //         [
    //             propertyName:   'ISPW_Release_Number', 
    //             propertyValue:  releaseNumber
    //         ], 
    //         [
    //             propertyName:   'ISPW_Assignment', 
    //             propertyValue:  assignmentId
    //         ], 
    //         [
    //             propertyName:   'ISPW_Runtime', 
    //             propertyValue:  envSettings.ispwRuntimeConfig
    //         ], 
    //         [
    //             propertyName:   'ISPW_Application', 
    //             propertyValue:  envSettings.ispwApplication
    //         ], 
    //         [
    //             propertyName:   'ISPW_Owner', 
    //             propertyValue:  runtimeParms.hostUser
    //         ],
    //         [
    //             propertyName: 'Jenkins_CES_Credentials', 
    //             propertyValue: runtimeParms.cesCredentialsId
    //         ]
    //         // ,
    //         // [
    //         //     propertyName: 'Jenkins_Git_Credentials', 
    //         //     propertyValue: pipelineParms.gitCredentialsId
    //         // ] 
    //     ]
    // )    
}