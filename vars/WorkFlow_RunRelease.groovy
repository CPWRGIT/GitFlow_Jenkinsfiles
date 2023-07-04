node {
    def continueRelease         = false

    dir('./') {
        deleteDir()
    }

    currentBuild.displayName = ISPW_Application + "/" + Owner_Id + ", Release: " + ispwRelease
    
    stage("Create ISPW Release"){

        ispwOperation (
            connectionId:           Host_Connection, 
            credentialsId:          Jenkins_CES_Credentials, 
            consoleLogResponseBody: true,             
            ispwAction:             'CreateRelease', 
            ispwRequestBody: """
                runtimeConfiguration=${ispwRuntimeConfig}
                stream=GITDEMO1
                application=${ISPW_Application}
                releaseId=${ispwRelease}
                description=RELEASE ${ispwRelease} FOR GITFLOW APP ${ISPW_Application}
            """
        )
    }
    
    stage("Transfer Tasks"){
        
        ispwOperation(
            connectionId:           Host_Connection, 
            credentialsId:          Jenkins_CES_Credentials, 
            consoleLogResponseBody: true,             
            ispwAction:             'TransferTask', 
            ispwRequestBody:        """
                runtimeConfiguration=${ispwRuntimeConfig}
                assignmentId=${ISPW_Assignment}
                level=RLSE
                containerId=${ispwRelease}
                containerType=R
            """
        )        

    }
    
    stage("Promote Release to PREP"){
        
        ispwOperation(
            connectionId:           Host_Connection, 
            credentialsId:          Jenkins_CES_Credentials, 
            consoleLogResponseBody: true,             
            ispwAction:             'PromoteRelease', 
            ispwRequestBody:        """
                runtimeConfiguration=${ispwRuntimeConfig}
                releaseId=${ispwRelease}
                level=PREP                
            """
        )        

    }

{
    // stage("Clone Repo"){
        
    //     checkout(
    //         changelog: false, 
    //         poll: false, 
    //         scm: [
    //             $class: 'GitSCM', 
    //             branches: [[name: '*/main']], 
    //             doGenerateSubmoduleConfigurations: false, 
    //             extensions: [], 
    //             submoduleCfg: [], 
    //             userRemoteConfigs: [
    //                 [
    //                     credentialsId: gitCredentials, 
    //                     url: "https://github.com/CPWRGIT/${gitRepo}.git"
    //                 ]
    //             ]
    //         ]
    //     )
    // }
    
    // stage("Add Tag"){
        
    //     def gitMessage = '"Release ' + gitTagName + '"'
        
    //     def stdOut = bat(
    //         label: '', 
    //         returnStdout: true, 
    //         script: 'git tag -a ' + gitTagName + ' -m ' + gitMessage
    //     )
        
    //     echo stdOut
        
    //     withCredentials(
    //         [
    //             usernamePassword(
    //                 credentialsId:      gitCredentials, 
    //                 passwordVariable:   'gitHubPassword', 
    //                 usernameVariable:   'gitHubUser'
    //             )
    //         ]
    //     ) 
    //     {

    //         stdOut = bat(
    //             returnStdout: true, 
    //             script: 'git remote set-url origin https://' + gitHubUser + ':' + gitHubPassword + "@github.com/CPWRGIT/${gitRepo}.git"
    //         )

    //         echo stdOut

    //         stdOut = bat(
    //             returnStdout: true, 
    //             script: "git push origin ${gitTagName}"
    //         )
    
    //         echo stdOut
            
    //     }
    // }
}
    stage("Evaluation - Manual Input"){

        def releaseStatus

        releaseStatus = input(
            message: 'Select the status for the release from the options below and click "Proceed"', 
            parameters: [
                choice(choices: ['Successful Release', 'Fallback Release'], description: 'Options', name: 'releaseOption')]        
        )

        if(releaseStatus == 'Successful Release'){
            continueRelease = true
        }
        else{
            continueRelease = false
        }
    }

    if(continueRelease){

        stage("Promote Release to MAIN"){
            
            ispwOperation(
                connectionId:           Host_Connection, 
                credentialsId:          Jenkins_CES_Credentials, 
                consoleLogResponseBody: true,             
                ispwAction:             'PromoteRelease', 
                ispwRequestBody:        """
                    runtimeConfiguration=${ispwRuntimeConfig}
                    releaseId=${ispwRelease}
                    level=MAIN                
                """
            )        

        }

        // stage("Close Release"){

        //     ispwOperation(
        //         connectionId:           Host_Connection, 
        //         credentialsId:          Jenkins_CES_Credentials, 
        //         consoleLogResponseBody: true,             
        //         ispwAction:             'CloseRelease', 
        //         ispwRequestBody:        """
        //             runtimeConfiguration=${ispwRuntimeConfig}
        //             releaseId=${ispwRelease}
        //         """
        //     )

        // }
    }
    else
    {
        stage("Fallback Release"){

            ispwOperation(
                connectionId:           Host_Connection, 
                credentialsId:          Jenkins_CES_Credentials, 
                consoleLogResponseBody: true,             
                ispwAction:             'FallbackRelease', 
                ispwRequestBody:        """
                    runtimeConfiguration=${ispwRuntimeConfig}
                    releaseId=${ispwRelease}
                    level=PROD
                """
            )

        }

        stage("Close Release"){

            ispwOperation(
                connectionId:           Host_Connection, 
                credentialsId:          Jenkins_CES_Credentials, 
                consoleLogResponseBody: true,             
                ispwAction:             'CloseRelease', 
                ispwRequestBody:        """
                    runtimeConfiguration=${ispwRuntimeConfig}
                    releaseId=${ispwRelease}
                """
            )

        }

        stage("Create Bugfix Branch"){
            build(
                job: '../GITDEMO_Workflow/GITDEMO_Branch_Managenent', 
                parameters: [
                    string(name: 'BranchAction', value: 'Create'), 
                    string(name: 'HostUserId', value: gitRepo), 
                    string(name: 'GitHubCredentialsId', value: gitCredentials), 
                    string(name: 'BranchType', value: 'Bugfix'), 
                    string(name: 'BranchName', value: 'failed_' + gitTagName), 
                    booleanParam(name: 'DeleteAssignment', value: false)
                ]
            )
        }

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
                            credentialsId: gitCredentials, 
                            url: "https://github.com/CPWRGIT/${gitRepo}.git"
                        ]
                    ]
                ]
            )
        }

        // stage("Remove Tag"){
          
        //     withCredentials(
        //         [
        //             usernamePassword(
        //                 credentialsId:      gitCredentials, 
        //                 passwordVariable:   'gitHubPassword', 
        //                 usernameVariable:   'gitHubUser'
        //             )
        //         ]
        //     ) 
        //     {

        //         stdOut = bat(
        //             returnStdout: true, 
        //             script: '''
        //                 git remote set-url origin https://''' + gitHubUser + ''':''' + gitHubPassword + '''@github.com/CPWRGIT/''' + gitRepo + '''.git
        //                 git reset --hard ''' + gitTagName + '''~
        //                 git push --delete origin ''' + gitTagName + '''
        //                 git push origin HEAD:main -f
        //             '''
        //         )

        //         echo stdOut

        //     }
        // }
    }

}