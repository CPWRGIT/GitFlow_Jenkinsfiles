
node{

    def gitHubTokenBasic    = getGitTokenBasic(gitCredentials)
    def gitRepoName         = "GitFlow_" + gitRepoOwner

    cleanWs()

    currentBuild.displayName = "${gitRepoName}: Pull Request for Release ${gitReleaseTag}"

    createPullRequest(gitHubTokenBasic, gitRepoName)

}

def getGitTokenBasic(credentials) {

    def token

    withCredentials(
        [
            usernamePassword(
                credentialsId:      credentials, 
                passwordVariable:   'gitHubTkTmp', 
                usernameVariable:   'gitHubUserTmp'
            )
        ]
    )
    {

        def tmpSecret = gitHubUserTmp + ":" + gitHubTkTmp
        token = tmpSecret.bytes.encodeBase64().toString()
    }

    return token
}

def createPullRequest(token, repo) {

    try{

        def requestBody = '''{
            "title": 	"Merge Release ''' + gitReleaseTag + '''",
	        "head":	  	"development",
	        "base":		"main",
	        "body":		"Release ''' + gitReleaseTag + ''' has been implemented successfully in production."   
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
            url:                        'https://api.github.com/repos/CPWRGIT/' + repo + '/pulls', 
            validResponseCodes:         '200', 
            wrapAsMultipart:            false
        )

    }
    catch(exception){

        error "[Error] - Unexpected http response code. " + exception.toString() + ". See previous log messages to determine cause.\n"
    
    }
}