currentBuild.displayName = "Xfer 2 Release ${IspwApplication} - ${ReleaseId}"

def hostConnectionCwc2  = '38e854b0-f7d3-4a8f-bf31-2d8bfac3dbd4' 
def hostConnectionCwcc  = ''

def hostConnection      = hostConnectionCwc2

node{
    dir(".\\") 
    {
        deleteDir()
    }

    stage("Release"){

        ispwOperation(
            connectionId:               hostConnection, 
            credentialsId:              CesCredentials,
            consoleLogResponseBody:     true, 
            ispwAction:                 'TransferTask', 
            ispwRequestBody:            """runtimeConfiguration=${IspwRuntimeConfig}
                                            assignmentId=${AssignmentId}
                                            level=RLSE
                                            containerId=${ReleaseId}
                                            containerType=R"""
        )        
    }
    
}
