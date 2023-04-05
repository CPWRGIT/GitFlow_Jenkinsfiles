currentBuild.displayName = "Closing ${ispwApplication} - Release ${releaseId}"

def hostConnectionCwc2  = '38e854b0-f7d3-4a8f-bf31-2d8bfac3dbd4' 
def hostConnectionCwcc  = ''

def hostConnection      = hostConnectionCwc2

node{

    cleanWs()
    
    stage("Close Release"){

        ispwOperation(
            connectionId:           hostConnection, 
            credentialsId:          cesCredentials,
            consoleLogResponseBody: true, 
            ispwAction:             'CloseRelease', 
            ispwRequestBody: """
                runtimeConfiguration=${ispwRuntimeConfig}
                releaseId=${releaseId}"""
        )
    }
    
}