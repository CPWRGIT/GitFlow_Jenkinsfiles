currentBuild.displayName = "Closing ${ispwApplication} - Release ${releaseId}"

node{

    cleanWs()
    
    stage("Clone Repo"){
        
        checkout(
            changelog: false, 
            poll: false, 
            scm: [
                $class: 'GitSCM', 
                branches: [[name: '*/main']], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [], 
                submoduleCfg: [], 
                userRemoteConfigs: [
                    [
                        credentialsId: GitCredentials, 
                        url: "https://github.com/CPWRGIT/${gitRepository}.git"
                    ]
                ]
            ]
        )
    }
    
    stage("Add Tag"){
        
        def gitMessage = '"Release ' + tagName + '"'
        
        def stdOut = bat(
            label: '', 
            returnStdout: true, 
            script: 'git tag -a ' + tagName + ' -m ' + gitMessage
        )
        
        echo stdOut
        
        withCredentials(
            [
                usernamePassword(
                    credentialsId:      GitCredentials, 
                    passwordVariable:   'GitHubPassword', 
                    usernameVariable:   'GitHubUser'
                )
            ]
        ) 
        {

            stdOut = bat(
                returnStdout: true, 
                script: 'git remote set-url origin https://' + GitHubUser + ':' + GitHubPassword + "@github.com/CPWRGIT/${gitRepository}.git"
            )

            echo stdOut

            stdOut = bat(
                returnStdout: true, 
                script: "git push origin ${tagName}"
            )
    
            echo stdOut
            
        }
    }
}

currentBuild.displayName = "Close Release ${IspwApplication} - ${ReleaseId}"

node{
    dir(".\\") 
    {
        deleteDir()
    }

    stage("Release"){

        ispwOperation(
            connectionId:           '196de681-04d7-4170-824f-09a5457c5cda', 
            credentialsId:          CesCredentials,
            consoleLogResponseBody: true, 
            ispwAction:             'CloseRelease', 
            ispwRequestBody: """
                runtimeConfiguration=ispw
                releaseId=${ReleaseId}"""
        )
    }
    
}