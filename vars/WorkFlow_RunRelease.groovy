node {
    def continueRelease         = false

    stage("Manual Intervention"){

        input 'Manual Intervention Point for Demo Purposes'

    }

    echo "Paramters"
    echo 'ISPW_Application          : ' + ISPW_Application        
    echo 'ISPW_Assignment           : ' + ISPW_Assignment         
    echo 'ISPW_Owner_Id             : ' + ISPW_Owner_Id            
    echo 'ISPW_Release              : ' + ISPW_Release             
    echo 'Host_Connection           : ' + Host_Connection          
    echo 'Jenkins_CES_Credentials   : ' + Jenkins_CES_Credentials  
    echo 'ISPW_Runtime_Config       : ' + ISPW_Runtime_Config     
    echo 'Git_Repo_Url              : ' + Git_Repo_Url            
    echo 'Git_Hub_Credentials       : ' + Git_Hub_Credentials     

    dir('./') {
        deleteDir()
    }

    ISPW_Release = "GLF1" + ISPW_Release

    currentBuild.displayName = ISPW_Application + "/" + ISPW_Owner_Id + ", Release: " + ISPW_Release
    
    stage("Create ISPW Release"){

        ispwOperation (
            connectionId:           Host_Connection, 
            credentialsId:          Jenkins_CES_Credentials, 
            consoleLogResponseBody: true,             
            ispwAction:             'CreateRelease', 
            ispwRequestBody: """
                runtimeConfiguration=${ISPW_Runtime_Config}
                stream=GITFLOW
                application=${ISPW_Application}
                subAppl=${ISPW_Application}
                releaseId=${ISPW_Release}
                description=RELEASE ${ISPW_Release} FOR GITFLOW APP ${ISPW_Application}
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
                runtimeConfiguration=${ISPW_Runtime_Config}
                assignmentId=${ISPW_Assignment}
                level=RLSE
                containerId=${ISPW_Release}
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
                runtimeConfiguration=${ISPW_Runtime_Config}
                releaseId=${ISPW_Release}
                level=RLSE                
            """
        )        

    }

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

    stage("Manual Intervention"){

        input 'Manual Intervention Point for Demo Purposes'

    }

    stage("Promote Release to PROD"){
        
        ispwOperation(
            connectionId:           Host_Connection, 
            credentialsId:          Jenkins_CES_Credentials, 
            consoleLogResponseBody: true,             
            ispwAction:             'PromoteRelease', 
            ispwRequestBody:        """
                runtimeConfiguration=${ISPW_Runtime_Config}
                releaseId=${ISPW_Release}
                level=PREP                
            """
        )        
    }

    stage("Decision"){

        def releaseStatus

        releaseStatus = input(
            message: 'Select the status for the release from the options below and click "Proceed"', 
            parameters: [
                choice(choices: ['Successful Release', 'Abort Release'], description: 'Options', name: 'releaseOption')]        
        )

        if(releaseStatus == 'Successful Release'){
            continueRelease = true
        }
        else{
            continueRelease = false
        }
    }

    if(continueRelease){

        stage("Close Release"){

            ispwOperation(
                connectionId:           Host_Connection, 
                credentialsId:          Jenkins_CES_Credentials, 
                consoleLogResponseBody: true,             
                ispwAction:             'CloseRelease', 
                ispwRequestBody:        """
                    runtimeConfiguration=${ISPW_Runtime_Config}
                    releaseId=${ISPW_Release}
                """
            )
        }
    }
    else
    {
        // stage("Fallback Release"){

        //     ispwOperation(
        //         connectionId:           Host_Connection, 
        //         credentialsId:          Jenkins_CES_Credentials, 
        //         consoleLogResponseBody: true,             
        //         ispwAction:             'FallbackRelease', 
        //         ispwRequestBody:        """
        //             runtimeConfiguration=${ISPW_Runtime_Config}
        //             releaseId=${ISPW_Release}
        //             level=PROD
        //         """
        //     )

        // }

        // stage("Close Release"){

        //     ispwOperation(
        //         connectionId:           Host_Connection, 
        //         credentialsId:          Jenkins_CES_Credentials, 
        //         consoleLogResponseBody: true,             
        //         ispwAction:             'CloseRelease', 
        //         ispwRequestBody:        """
        //             runtimeConfiguration=${ISPW_Runtime_Config}
        //             releaseId=${ISPW_Release}
        //         """
        //     )

        // }

        // stage("Create Bugfix Branch"){
        //     build(
        //         job: '../GITDEMO_Workflow/GITDEMO_Branch_Managenent', 
        //         parameters: [
        //             string(name: 'BranchAction', value: 'Create'), 
        //             string(name: 'HostUserId', value: gitRepo), 
        //             string(name: 'GitHubCredentialsId', value: gitCredentials), 
        //             string(name: 'BranchType', value: 'Bugfix'), 
        //             string(name: 'BranchName', value: 'failed_' + gitTagName), 
        //             booleanParam(name: 'DeleteAssignment', value: false)
        //         ]
        //     )
        // }

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