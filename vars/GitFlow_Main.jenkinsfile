def fromCommit
def toCommit

def call(Map execParms) {

    node {
        
        stage ('Checkout') {

            dir('./') {
                deleteDir()
            }

            checkout scm

        }

        if(BRANCH_NAME.startsWith("release")) {

            fromCommit  = determineFromCommit()
            toCommit    = determineToCommit()

            // loadMainframeCode()

            // buildMainframeCode()

            // runSonarScan()

            // startXlr()

        }
    }
}

def determineFromCommit() {
    def fromCommit = bat(stdOut: true, script('git log -1 $(git describe --abbrev=0 --tags $(git describe --tags)^)')).trim()

    echo "Determined From Commit: " + fromCommit

    return fromCommit
}


def determineToCommit() {
    def toCommit = bat(stdOut: true, script('git rev-parse --verify HEAD')).trim()

    echo "Determined From Commit: " + toCommit

    return toCommit
}

def loadMainframeCode() {

   stage("Mainframe Load") {

    def output = bat(
        stdOut: true,
        script: 'C:\TopazCLI201301\IspwCLI.bat 
-operation syncGitToIspw 
-host "cwcc.bmc.com" 
-port "16196" 
-id "hddrxm0" 
-pass "tmsp2301" 
-protocol None 
-code 1148 
-timeout "0" 
-targetFolder .\ 
-data .\TopazCliWkspc 
-ispwServerConfig iccga 
-ispwServerStream GITFLOW 
-ispwServerApp GITFLOWE 
-ispwCheckoutLevel RLSE 
-ispwConfigPath .\GenAppCore\ispwconfig.yml 
-ispwContainerCreation per-branch 
-gitUsername "cpwrgit" 
-gitPassword "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" 
-gitRepoUrl "https://github.com/CPWRGIT/HDDRXM0.git" 
-gitBranch "release/v01.00.01" 
-gitFromHash' + fromCommit + '
-gitLocalPath .\ 
-gitCommit' + toCommit

    )

        // gitToIspwIntegration( 
        //     connectionId:       'de2ad7c3-e924-4dc2-84d5-d0c3afd3e756', //synchConfig.environment.hci.connectionId,                    
        //     credentialsId:      'ea48408b-b2be-4810-8f4e-5b5f35977eb1', //pipelineParms.hostCredentialsId,                     
        //     runtimeConfig:      'iccga', //ispwConfig.ispwApplication.runtimeConfig,
        //     stream:             'GITFLOW', //ispwConfig.ispwApplication.stream,
        //     app:                'GITFLOWE', //ispwConfig.ispwApplication.application, 
        //     branchMapping:      'release/** => RLSE,per-branch', //branchMappingString,
        //     ispwConfigPath:     './GenApp_MainframeCore/ispwconfig.yml', //ispwConfigFile, 
        //     gitCredentialsId:   '67a3fb18-073f-498b-adee-1a3c75192745', //pipelineParms.gitCredentialsId, 
        //     gitRepoUrl:         'https://github.com/CPWRGIT/GitFlow_HDDRXM0.git' //pipelineParms.gitRepoUrl
        // )
    }
}