import groovy.json.JsonSlurper

// Pipeline Parameters
String hostUserId                   = HostUserId.toUpperCase()
String hostCredentialsId            = HostCredentialsId
String ispwApp                      = IspwApp.toUpperCase()
String cesCredentialsId             = CesCredentialsId
String gitCredentialsId             = GitCredentialsId
String codeCoverageRepo             = CodeCoverageRepo.toUpperCase()
String gitHubAdminUser              = GitHubAdminUser.toUpperCase()
String gitHubAdminPassword          = GitHubAdminPassword
String targetEnvironment            = TargetEnvironment

// String defaultUtLevel               = DefaultUtLevel.toUpperCase()
// String defaultFtLevel               = DefaultFtLevel.toUpperCase()

String hostName 

String rootProjectFile              = "./.project"
String jenkinsfile                  = "./GitFlow.jenkinsfile"
String ispwConfigFile               = "./GenApp_MainframeCore/ispwconfig.yml"
String projectSettingsFile          = "./GenApp_MainframeCore/.settings/GenApp_MainframeCore.prefs"
String sonarLintSettingsFileMf      = "./GenApp_MainframeCore/.settings/org.sonarlint.eclipse.core.prefs"
String sonarLintSettingsFileDist    = "./GenApp_WebServer/.settings/org.sonarlint.eclipse.core.prefs"

String ispwStream                   = "GITFLOW"

String sonarServerUrl               = "http://192.168.96.169:9000" //"http://aus-bdc-sonarqube-cwcc.bmc.com:9000"        
String sonarQualityGateId           = "AYb96SGym7XD63sZxcew"
String sonarQubeTokenStore          = 'SonarQube_Token_Basic'
String sonarQubeToken               = ''

String repoTemplate                 = 'GitFlow_Template'

String gitHubAdminCredentialsPw     = 'CPWRGIT'
String gitHubAdminCredentialsTk     = 'CPWRGIT_GitHub_New'
String gitHubAdminTokenStore        = 'CPWRGIT_Token_Basic'
String gitHubAdminTokenPlain        = ''
String gitHubAdminTokenBasic        = ''

String jenkinsGitFlowFolder         = 'GitFlow'
String jenkinsGitFlowAdminFolder    = 'Demo_Environment_Administration'
String jenkinsJobTemplate           = 'GitFlow_Template'
String jenkinsJobName               = 'GitFlow_' + ispwApp.substring(ispwApp.length() - 1)

def environmentSettings             = [
        'CWCC': [
            'lparName':                 'CWCC',
            'hostName':                 'cwcc.bmc.com',
            'hciConnectionId':          'de2ad7c3-e924-4dc2-84d5-d0c3afd3e756',
            'ispwRuntime':              'iccga',
            'xgSsid':                   'MXG1',
            'sonarProjectName':         "${ispwStream}_${ispwApp}",
            'gitHubRepo':               "GitFlow_" + hostUserId, 
            'jenkinsUrl':               "http://192.168.96.133:8080",
            'tttExecutionEnvironment':  '5b508b8a787be73b59238d38',
            'componentIds':             [
                'CWXTCOB':              '5d5fea81180742000cf98888'
            ]
        ],
        'CWC2': [
            'lparName':                 'CWC2',                                    
            'hostName':                 'cwc2.bmc.com',
            'hciConnectionId':          '263438b6-f699-4373-be9f-378af3d28633',
            'ispwRuntime':              'ic2ga',
            'xgSsid':                   'MXG2',   
            'sonarProjectName':         "${ispwStream}_${ispwApp}",
            'gitHubRepo':               "GitFlow_" + ispwApp, 
            'jenkinsUrl':               "http://192.168.96.146:8080",            
            'tttExecutionEnvironment':  '5c519facfba8720a90ccc645',
            'componentIds':             [
                'CWXTCOB':              '6046063418074200e864cb5e'
            ]                                    
        ]
    ]

def components                      = ['CWXTCOB']

node{

    checkPipelineCredentials(gitHubAdminCredentialsPw)

    gitHubAdminTokenPlain   = getGitHubAdminTokenPlain(gitHubAdminCredentialsTk)

    gitHubAdminTokenBasic   = getGitHubAdminTokenBasic(gitHubAdminTokenStore)
    sonarQubeToken          = getSonarQubeToken(sonarQubeTokenStore)

    def sonarProjectName    = environmentSettings[targetEnvironment].sonarProjectName
    def gitHubRepo          = environmentSettings[targetEnvironment].gitHubRepo

    dir("./"){
        deleteDir()
    }

    currentBuild.displayName = "Setup for repo CPWRGIT/${gitHubRepo}"

    stage("Check Git Repository"){

        checkGitRepository(gitHubAdminTokenBasic, gitHubRepo)

    }

    stage("Create Git Repository"){

        createGitRepository(gitHubAdminTokenBasic, gitHubRepo, repoTemplate)

    }

    sleep 30

    stage("Clone Git repository"){

        cloneGitRepository(gitHubRepo, gitHubAdminCredentialsTk)        

    }

    stage("Modify jenkinsfile, ispwconfig, properties"){

        def filesStringsList = [
            [rootProjectFile,
                [
                    ['GitFlow_Template', gitHubRepo]
                ]
            ],
            [jenkinsfile, 
                [
                    ['<demoEnvironment>', targetEnvironment],
                    ['<hostCredentials>', hostCredentialsId], 
                    ['<cesCredentials>', cesCredentialsId],
                    ['<gitRepoUrl>', "https://github.com/CPWRGIT/${gitHubRepo}.git"],
                    ['<jenkinsGitCredentialsId>', gitCredentialsId],
                    ['<codeCoverageRepo>', codeCoverageRepo]
                ]
            ],
            [ispwConfigFile,
                [
                    ['<hostName>', environmentSettings[targetEnvironment].hostName],
                    ['<ispwRuntime>', environmentSettings[targetEnvironment].ispwRuntime],
                    ['<ispwApplication>', ispwApp],    
                ]
            ],
            [sonarLintSettingsFileMf,
                [
                    ['<projectName>', sonarProjectName]
                ]
            ],
            [sonarLintSettingsFileDist,
                [
                    ['<projectName>', sonarProjectName]
                ]
            ]
        ]

        filesStringsList.each{

            def fileName    = it[0]
            def stringsList = it[1]

            println "Modifying file: " + fileName.toString()

            replaceFileContent(fileName, stringsList)

        }
    }
    
    stage("Modify TTT assets"){

        def vtContextFiles = findFiles(glob: '**/Tests/Unit/**/*.context')

        def stringsList = [
                ['<ispw_application>', ispwApp]
                // ,
    //             // ['${ispw_level_value}', DefaultUtLevel],
    //             // ['${ut_level}', DefaultUtLevel],
    //             // ['${ft_level}', DefaultFtLevel],
    //             [environmentSettings['CWCC'].tttExecutionEnvironment, environmentSettings[targetEnvironment].tttExecutionEnvironment]
            ]

        vtContextFiles.each{

            println "Modifying file: " + it.path.toString()

            def content = readFile(file: it.path)
            
            replaceFileContent(it.path, stringsList)            

        }
    //     def nvtContextFiles = findFiles(glob: '**/Tests/Integration/**/*.context')

    //     stringsList = [
    //             ['${ispw_app_value}', ispwApp],
    //             // ['${ut_level}', DefaultUtLevel],
    //             // ['${ft_level}', DefaultFtLevel],
    //             [environmentSettings['CWCC'].tttExecutionEnvironment, environmentSettings[targetEnvironment].tttExecutionEnvironment]
    //         ]

    //     components.each{
    //         stringsList.add([environmentSettings['CWCC'].componentIds[it], environmentSettings[targetEnvironment].componentIds[it]])
    //     }

    //     nvtContextFiles.each{

    //         println "Modifying file: " + it.path.toString()

    //         def content = readFile(file: it.path)
            
    //         replaceFileContent(it.path, stringsList)            

    //     }

    //     def nvtScenarioFiles = findFiles(glob: '**/Tests/Integration/**/*.scenario')

    //     stringsList = [
    //         ['${lpar_name}', environmentSettings[targetEnvironment].lparName],
    //         ['${mf_userid}', hostUserId],
    //         ['${ispw_app_value}', ispwApp],
    //         ['${xg_ssid}', environmentSettings[targetEnvironment].xgSsid]
    //     ]

    //     nvtScenarioFiles.each{

    //         println "Modfying file: " + it.path.toString()

    //         def content = readFile(file: it.path)
            
    //         replaceFileContent(it.path, stringsList)            

    //     }
    }

    // stage("Modify JOB source files"){

    //     def jobFiles    = findFiles(glob: '**/Sources/**/Jobs/*.jcl')
    //     // def ispwPathNum = DefaultUtLevel.substring(DefaultUtLevel.length() - 1, DefaultUtLevel.length())

    //     def stringsList = [
    //             ['${user_id}', hostUserId],
    //             ['${ispw_app_value}', ispwApp],
    //             ['${ispw_path_num}', ispwPathNum]
    //         ]

    //     jobFiles.each{

    //         println "Modfying file: " + it.path.toString()

    //         def content = readFile(file: it.path)
            
    //         replaceFileContent(it.path, stringsList)            

    //     }

    // }

    stage("Create Sonar and configure"){

        if(checkForProject(sonarProjectName, sonarServerUrl, sonarQubeToken) == "NOT FOUND") {

            createProject(sonarProjectName, sonarServerUrl, sonarQubeToken)

            setQualityGate(sonarQualityGateId, sonarProjectName, sonarServerUrl, sonarQubeToken)

            renameBranch(sonarProjectName, sonarServerUrl, sonarQubeToken)

        }
        else{
            echo "Sonar project ${sonarProjectName} already exists."
        }
    }
    
    stage("Push to GitHub and create new branches"){

        finalizeGitHub(gitHubRepo, gitHubAdminUser, gitHubAdminTokenPlain)

    }

    stage("Create Jenkins Multibranch Pipeline") {

        httpRequest(
            consoleLogResponseBody:     true, 
            httpMode:                   'GET', 
            outputFile:                 'job_config.xml', 
            responseHandle:             'NONE', 
            url:                        environmentSettings[targetEnvironment].jenkinsUrl + '/job/' + jenkinsGitFlowFolder + '/job/' + jenkinsGitFlowAdminFolder + '/job/' + jenkinsJobTemplate + '/config.xml', 
            wrapAsMultipart:            false
        )

        def tmpConfig = readFile("./job_config.xml")

        tmpConfig = tmpConfig.replace('xxxxxx', hostUserId).replace("&lt;gitHubRepo&gt;", gitHubRepo)
        
        writeFile(file: './job_config.xml', text: tmpConfig)
        
        httpRequest(
            consoleLogResponseBody:     true, 
            customHeaders:              [[maskValue: false, name: 'Content-Type', value: 'text/xml']],
            httpMode:                   'POST', 
            responseHandle:             'NONE', 
            uploadFile:                 './job_config.xml', 
            url:                        environmentSettings[targetEnvironment].jenkinsUrl + '/job/' + jenkinsGitFlowFolder + '/createItem?name=' + jenkinsJobName, 
            wrapAsMultipart: false
        )
    }
    // stage("Allocate and copy TEST datasets"){

    //     def jobJcl = buildJcl(ispwApp)

    //     topazSubmitFreeFormJcl(
    //         connectionId: hciConnectionId, 
    //         credentialsId: HostCredentialsId, 
    //         jcl: jobJcl, 
    //         maxConditionCode: '0'
    //     )    

    // }
}

def checkPipelineCredentials(credentials){

    withCredentials(
        [
            usernamePassword(
                credentialsId:      credentials, 
                passwordVariable:   'gitHubAdminPwTmp', 
                usernameVariable:   'gitHubAdminUserTmp'
            )
        ]
    )
    {
        if(
            !(gitHubAdminUser       == gitHubAdminUserTmp) ||
            !(gitHubAdminPassword   == gitHubAdminPwTmp)
        )
        {
            error '[Error] - The specified GitHub credentials could not be verified. Aborting process.'
        }
    }
}

def getGitHubAdminTokenPlain (credentials) {

    def token

    withCredentials(
        [
            usernamePassword(
                credentialsId:      credentials, 
                passwordVariable:   'gitHubAdminTkTmp', 
                usernameVariable:   'gitHubAdminUserTmp'
            )
        ]
    )
    {
        token = gitHubAdminTkTmp
    }

    return token
}

def getGitHubAdminTokenBasic(credentials) {

    def token = 'Basic'

    withCredentials(
        [
            string( 
                [
                    credentialsId:  credentials, 
                    variable:       'cpwrgitTokenTmp'
                ]
            )
        ]
    )
    {
        token = token + ' ' + cpwrgitTokenTmp
    }

    return token
}

def getSonarQubeToken(credentials) {

    def token = 'Basic'

    withCredentials(
        [
            string( 
                [
                    credentialsId:  credentials, 
                    variable:       'sonarQubeTokenTmp'
                ]
            )
        ]
    )
    {
        token = token + ' ' + sonarQubeTokenTmp
    }

    return token
}

def checkGitRepository(token, repo) {

    try{

        def response = httpRequest(

            consoleLogResponseBody: true, 
            customHeaders:          [
                [maskValue: false,  name: 'content-type',   value: 'application/json'], 
                [maskValue: true,   name: 'authorization',  value: token], 
                // [maskValue: false,  name: 'accept',         value: 'application/vnd.github.v3+json'], 
                [maskValue: false,  name: 'accept',         value: 'application/vnd.github+json'],                     
                [maskValue: false,  name: 'user-agent',     value: 'cpwrgit']
            ], 
            ignoreSslErrors:        true, 
            url:                    'https://api.github.com/repos/CPWRGIT/' + repo, 
            validResponseCodes:     '200,404', 
            wrapAsMultipart:        false

        )

        if(response.status == 200){

            error "[Error] - The repository ${repo} already exists. Cannot create again.\n"

        }

    }
    catch(exception){

        error "[Error] - " + exception.toString() + ". See previous log messages to determine cause.\n"

    }

}

def createGitRepository(token, repo, template) {

    try{

        def requestBody = '''{
                "owner":    "CPWRGIT",
                "name":     "''' + repo + '''",
                "private":  true   
            }'''

        httpRequest(
            consoleLogResponseBody:     true, 
            customHeaders:              [
                [maskValue: false,  name: 'content-type',   value: 'application/json'], 
                [maskValue: true,   name: 'authorization',  value: token], 
                [maskValue: false,  name: 'accept',         value: 'application/vnd.github+json'], 
                [maskValue: false,  name: 'user-agent',     value: 'cpwrgit']
            ], 
            httpMode:                   'POST', 
            ignoreSslErrors:            true, 
            requestBody:                requestBody, 
            url:                        'https://api.github.com/repos/CPWRGIT/' + template + '/generate', 
            validResponseCodes:         '201', 
            wrapAsMultipart:            false
        )

    }
    catch(exception){

        error "[Error] - Unexpected http response code. " + exception.toString() + ". See previous log messages to determine cause.\n"
    
    }
}

def cloneGitRepository(repo, credentials) {

    checkout(
        changelog: false, 
        poll: false, 
        scm: [
            $class: 'GitSCM', 
            branches: [[name: '*/development']], 
            doGenerateSubmoduleConfigurations: false, 
            extensions: [], 
            submoduleCfg: [], 
            userRemoteConfigs: [[
                credentialsId: credentials,
                url: "https://github.com/CPWRGIT/${repo}.git"
            ]]
        ]
    )
}

def replaceFileContent(fileName, stringsList){

    def fileNewContent  = readFile(file: fileName)

    stringsList.each{
    
        def oldString = it[0]
        def newString = it[1]

        println "Replace: " + oldString 
        println "With   : " + newString

        fileNewContent  = fileNewContent.replace(oldString, newString)

    }
      
    writeFile(file: fileName, text: fileNewContent)
}

def checkForProject(sonarProjectName, sonarServerUrl, sonarQubeToken){

    def httpResponse = httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: sonarQubeToken]],
        httpMode:                   'GET',
        ignoreSslErrors:            true, 
        responseHandle:             'NONE', 
        consoleLogResponseBody:     true,
        url:                        "${sonarServerUrl}/api/projects/search?projects=${sonarProjectName}&name=${sonarProjectName}"

    def jsonSlurper = new JsonSlurper()
    def httpResp    = jsonSlurper.parseText(httpResponse.getContent())

    httpResponse    = null
    jsonSlurper     = null

    if(httpResp.message != null)
    {
        echo "Resp: " + httpResp.message
        error
    }
    else
    {
        def pagingInfo = httpResp.paging
        if(pagingInfo.total == 0)
        {
            response = "NOT FOUND"
        }
        else
        {
            response = "FOUND"
        }
    }

    return response
}

def createProject(sonarProjectName, sonarServerUrl, sonarQubeToken){

    def httpResponse = httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: sonarQubeToken]],
        httpMode:                   'POST',
        ignoreSslErrors:            true, 
        responseHandle:             'NONE', 
        consoleLogResponseBody:     true,
        url:                        "${sonarServerUrl}/api/projects/create?project=${sonarProjectName}&name=${sonarProjectName}"

    def jsonSlurper = new JsonSlurper()
    def httpResp    = jsonSlurper.parseText(httpResponse.getContent())
    
    httpResponse    = null
    jsonSlurper     = null

    if(httpResp.message != null)
    {
        echo "Resp: " + httpResp.message
        error
    }
    else
    {
        echo "Created SonarQube project ${sonarProjectName}."
    }
}

def setQualityGate(qualityGateId, sonarProjectName, sonarServerUrl, sonarQubeToken){

    def httpResponse = httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: sonarQubeToken]],
        httpMode:                   'POST',
        ignoreSslErrors:            true, 
        responseHandle:             'NONE', 
        consoleLogResponseBody:     true,
        url:                        "${sonarServerUrl}/api/qualitygates/select?gateId=${qualityGateId}&projectKey=${sonarProjectName}"

    echo "Assigned QualityGate 'Git2IspwDemo' to project ${sonarProjectName}."
}

def renameBranch(sonarProjectName, sonarServerUrl, sonarQubeToken){

    def httpResponse = httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: sonarQubeToken]],
        httpMode:                   'POST',
        ignoreSslErrors:            true, 
        responseHandle:             'NONE', 
        consoleLogResponseBody:     true,
        url:                        "${sonarServerUrl}/api/project_branches/rename?name=main&project=${sonarProjectName}"

    echo "Renamed master branch of SonarQube project ${sonarProjectName} to 'main'."
}

def finalizeGitHub(repo, userid, token) {
    
    def message     = '"Inital Setup"'
        
    dir("./")
    {
        bat(returnStdout: true, script: 'git config user.email "cpwrgit@compuware.com"')
        bat(returnStdout: true, script: 'git config user.name "CPWRGIT"')

        bat(returnStdout: true, script: 'git commit -a -m ' + message)
        bat(returnStdout: true, script: "git push  https://" + userid + ":" + token + "@github.com/CPWRGIT/${repo} HEAD:development -f")
        
        bat(returnStdout: true, script: 'git branch main')
        bat(returnStdout: true, script: "git push  https://" + userid + ":" + token + "@github.com/CPWRGIT/${repo} refs/heads/main:refs/heads/main -f")
        bat(returnStdout: true, script: 'git tag -a v00.00.01 -m "Baseline Release"')
        bat(returnStdout: true, script: 'git remote set-url origin https://' + userid + ':' + token + "@github.com/CPWRGIT/${repo}.git")
        bat(returnStdout: true, script: "git push origin v00.00.01")

        bat(returnStdout: true, script: 'git branch feature/demo_feature')
        bat(returnStdout: true, script: "git push  https://" + userid + ":" + token + "@github.com/CPWRGIT/${repo} refs/heads/feature/demo_feature:refs/heads/feature/demo_feature -f")
    }
}

def buildJcl(ispwApp){

    def jobRecords = []
    def jobJcl = ''

    jobRecords.add(/\/\/GITDEMO1 JOB ('GITDEMO'),'${ispwApp} TEST FILES',NOTIFY=&SYSUID,/)
    jobRecords.add(/\/\/             MSGLEVEL=(1,1),MSGCLASS=X,CLASS=A,REGION=0M/)
    jobRecords.add(/\/*JOBPARM S=*/)
    jobRecords.add(/\/\/****************************************************************/)
    jobRecords.add(/\/\/DELETE   EXEC PGM=IDCAMS/)
    jobRecords.add(/\/\/SYSPRINT DD  SYSOUT=*/)
    jobRecords.add(/\/\/SYSIN    DD  */)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWKTDB2X.IN/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWXTDATA/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWXTRPT/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWXTRPT.EXPECT/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM.EXPECT/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWKTKSDS/)
    jobRecords.add(/  DELETE SALESSUP.${ispwApp}.TEST.CWKTKSDS.COPY/)
    jobRecords.add(/  SET MAXCC = 0/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/ALLOCSEQ EXEC PGM=IEFBR14/)
    jobRecords.add(/\/\/CWKTDB2X DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=F,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWKTDB2X.IN/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/CWXTDATA DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=F,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWXTDATA/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/CWXTRPT  DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=FA,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/RPTEOM   DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=FA,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/CWXTRPTX DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=FA,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EXPECT/)
    jobRecords.add(/\/\/*/)
    jobRecords.add(/\/\/RPTEOMX  DD DISP=(,CATLG,DELETE),/)
    jobRecords.add(/\/\/            SPACE=(TRK,(5,1)),/)
    jobRecords.add(/\/\/            DCB=(LRECL=80,RECFM=FA,BLKSIZE=0),/)
    jobRecords.add(/\/\/            UNIT=SYSDA,/)
    jobRecords.add(/\/\/            DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM.EXPECT/)
    jobRecords.add(/\/\/****************************************************************/)
    jobRecords.add(/\/\/ALLOCVSM EXEC PGM=IDCAMS/)
    jobRecords.add(/\/\/SYSPRINT DD  SYSOUT=*/)
    jobRecords.add(/\/\/SYSIN    DD  */)
    jobRecords.add(/    DEFINE CLUSTER -/)
    jobRecords.add(/    (NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS) -/)
    jobRecords.add(/    BUFFERSPACE(37376) -/)
    jobRecords.add(/    INDEXED -/)
    jobRecords.add(/    KEYS(5 0) -/)
    jobRecords.add(/    MANAGEMENTCLASS(STANDARD) -/)
    jobRecords.add(/    OWNER(HDDRXM0) -/)
    jobRecords.add(/    RECORDSIZE(80 80) -/)
    jobRecords.add(/    SHAREOPTIONS(4 4) -/)
    jobRecords.add(/    RECOVERY -/)
    jobRecords.add(/    STORAGECLASS(STDNOCSH)) -/)
    jobRecords.add(/    DATA(NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS.DATA) -/)
    jobRecords.add(/    TRACKS(3 15) -/)
    jobRecords.add(/    CONTROLINTERVALSIZE(18432)) -/)
    jobRecords.add(/    INDEX(NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS.INDEX) -/)
    jobRecords.add(/    TRACKS(1 1) -/)
    jobRecords.add(/    CONTROLINTERVALSIZE(512))/)
    jobRecords.add(/ /)
    jobRecords.add(/    DEFINE CLUSTER -/)
    jobRecords.add(/    (NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS.COPY) -/)
    jobRecords.add(/    BUFFERSPACE(37376) -/)
    jobRecords.add(/    INDEXED -/)
    jobRecords.add(/    KEYS(5 0) -/)
    jobRecords.add(/    MANAGEMENTCLASS(STANDARD) -/)
    jobRecords.add(/    OWNER(HDDRXM0) -/)
    jobRecords.add(/    RECORDSIZE(80 80) -/)
    jobRecords.add(/    SHAREOPTIONS(4 4) -/)
    jobRecords.add(/    RECOVERY -/)
    jobRecords.add(/    STORAGECLASS(STDNOCSH)) -/)
    jobRecords.add(/    DATA(NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS.COPY.DATA) -/)
    jobRecords.add(/    TRACKS(3 15) -/)
    jobRecords.add(/    CONTROLINTERVALSIZE(18432)) -/)
    jobRecords.add(/    INDEX(NAME(SALESSUP.${ispwApp}.TEST.CWKTKSDS.COPY.INDEX) -/)
    jobRecords.add(/    TRACKS(1 1) -/)
    jobRecords.add(/    CONTROLINTERVALSIZE(512))/)
    jobRecords.add(/\/*/)
    jobRecords.add(/\/\/****************************************************************/)
    jobRecords.add(/\/\/COPYDS   EXEC PGM=FILEAID,REGION=08M/)
    jobRecords.add(/\/\/STEPLIB  DD DISP=SHR,DSN=SYS2.CW.VJ.#CWCC.CXVJLOAD/)
    jobRecords.add(/\/\/         DD DISP=SHR,DSN=SYS2.CW.VJR17B.SXVJLOAD/)
    jobRecords.add(/\/\/SYSPRINT DD  SYSOUT=*/)
    jobRecords.add(/\/\/SYSLIST  DD  SYSOUT=*/)
    jobRecords.add(/\/\/DD01     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWKTDB2X.IN/)
    jobRecords.add(/\/\/DD02     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWXTDATA/)
    jobRecords.add(/\/\/DD03     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWXTRPT/)
    jobRecords.add(/\/\/DD04     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWXTRPT.EOM/)
    jobRecords.add(/\/\/DD05     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWXTRPT.EXP/)
    jobRecords.add(/\/\/DD06     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWXTRPT.EOM.EXP/)
    jobRecords.add(/\/\/DD07     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWKTKS/)
    jobRecords.add(/\/\/DD08     DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.GITDEMO1.TEST.MSTR.CWKTKS.CPY/)
    jobRecords.add(/\/\/DD01O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWKTDB2X.IN/)
    jobRecords.add(/\/\/DD02O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWXTDATA/)
    jobRecords.add(/\/\/DD03O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT/)
    jobRecords.add(/\/\/DD04O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM/)
    jobRecords.add(/\/\/DD05O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EXPECT/)
    jobRecords.add(/\/\/DD06O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWXTRPT.EOM.EXPECT/)
    jobRecords.add(/\/\/DD07O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWKTKSDS/)
    jobRecords.add(/\/\/DD08O    DD  DISP=SHR,/)
    jobRecords.add(/\/\/             DSN=SALESSUP.${ispwApp}.TEST.CWKTKSDS.COPY/)
    jobRecords.add(/\/\/SYSIN    DD  */)
    jobRecords.add('$$DD01 COPY')
    jobRecords.add('$$DD02 COPY')
    jobRecords.add('$$DD03 COPY')
    jobRecords.add('$$DD04 COPY')
    jobRecords.add('$$DD05 COPY')
    jobRecords.add('$$DD06 COPY')
    jobRecords.add('$$DD07 COPY')
    jobRecords.add('$$DD08 COPY')
    jobRecords.add(/\/*/)

    jobRecords.each{
        jobJcl = jobJcl + it + '\n'
    }

    return jobJcl

}