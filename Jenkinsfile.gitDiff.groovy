currentBuild.displayName = "${env.BUILD_NUMBER}"
def commitId = params.commitId
String.metaClass.getEmpty = { delegate.allWhitespace }
String branchName, 

--------------
// here are basic set up for all stages below
pipeline {
    agent {
        label 'slexng-slave'
    }
    environment {
        EMAIL_RECIPIENTS = 'jan.balint@expersoft.com' //'slexng-infra@whitestein.com'
        REGISTRY_CREDS = credentials('gitlabJenkins') //check with simon gitlab credentials
    }

        tools{
            //do not specified tool - maven, jdk, gradle - ahs to be preconfigure in jenkins
        maven= 'Maven'
    }
    
    parameters { //for external configuration to provide my pipeline 
    choice(name: 'selectedEnv',choices: ['dev', 'qa', 'test'],description: 'started against the selected environment')
    string(name: 'commitId', defaultValue:'',description:'Here should be saved all git logs' trim: true)
    booleanParam(name: 'execute test', defaultValue: true, description'' ) //for example skip some stage  
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

--------------     
    stages {
        stage('Host settings') {
            steps {
                script {
                    sh """
                        git config --global user.name $REGISTRY_CREDS_USR
                        git config --global user.email devops@expersoft.com
                        git config --global fetch --all
                    """
                        }
                    }
                }

        stage('Checkout') {
            when {
                expression { commitId.empty == false }
            }
            steps {
                script {
                    commitFromStable = """${message}=(git log --oneline --graph --decorate --abbrev-commit origin/stable...main); then echo "true"; else echo "false"; fi"""
                    commitFound = sh(returnStdout: true, script: commitFromStable).trim()

                    if (commitFound == 'true') {
                        currentBuild.result = "FAILED"
                        throw new RuntimeException("Commit ${commitId} is not exist in stable branch")
                    } else {
                        currentBuild.result = "SUCCESFULL"
                        throw new RuntimeException("Main and stable branches are same")
                    }
                }
            }
        }

        
        stage('Diff Keycloak configuration') {
            steps {
                script {
                    def environments = [dev : [domain: "https://slexng-dev.k8s-lsps.whitestein.net"],
                                        qa  : [domain: "https://slexng-qa.k8s-lsps.whitestein.net"],
                                        test: [domain: "https://slexng-test.dmz.whitestein.com"]]
                    String filename = "slexng-realm-export-${params.selectedEnv}.json"
                    String filepath = "deployment/keycloak/config/"
                    String keycloak_domain = environments[params.selectedEnv].domain

                    sh """
                        TOKEN=\$(curl -d "username=$REGISTRY_CREDS_USR" -d "password=$REGISTRY_CREDS_PSW" -d "grant_type=password" -d "client_id=slexng-jenkins" -H "Content-Type: application/x-www-form-urlencoded" -X POST ${keycloak_domain}/auth/realms/slexng/protocol/openid-connect/token | jq -r '.access_token')
                        curl -o ${filepath}${filename}.server -X POST -H 'Accept: application/json' -H "Authorization: Bearer \${TOKEN}" ${keycloak_domain}/auth/admin/realms/slexng/partial-export?exportClients=True&exportGroupsAndRoles=False
                    """
                    keycloak_diff = sh(script: """diff <(cat ${filepath}${filename}  | jq --sort-keys) <(cat ${filepath}${filename}.server  | jq --sort-keys) || true""", returnStdout: true).trim()

                    if (keycloak_diff.length() > 0) {
                        println "########## KEYCLOAK CONFIGURATION DIFF START ##########"
                        println keycloak_diff
                        println "########## KEYCLOAK CONFIGURATION DIFF END ##########"

                        emailext attachLog: false,
                                body: "See ${env.BUILD_URL}\n\n\nThe changes occurred in the following lines:\n\n${keycloak_diff}",
                                subject: """Slexng-${params.selectedEnv} keycloak configuration is not matching""",
                                to: "${EMAIL_RECIPIENTS}"
                        currentBuild.result = 'FAILURE'
                    } else {
                        echo "Git ${params.selectedEnv}-keycloak configuration and ${params.selectedEnv}-keycloak server configuration are matching"
                        }   
                     }
                }
            }
        }
    }   

    post { //some logic after all stages are done 
        always {
            script {
                if (branchName.empty == false || tagName.empty == false) {
                    sh "git checkout main && git branch -D ${branchName}>/dev/null && git tag -d ${tagName}>/dev/null"
                }
            }
        }
        failure {
            emailext attachLog: false,
                    body: "See ${env.BUILD_URL}\n\n" + getMailSummary(),
                    subject: """SlexNG: release creation failed""",
                    recipientProviders: [[$class: 'RequesterRecipientProvider'], [$class: 'CulpritsRecipientProvider']],
                    to: "${EMAIL_RECIPIENTS}"
        }
    }
}



------------------------------------------------------

def getMailSummary() {
    passedBuilds = []
    lastSuccessfulBuild(passedBuilds, currentBuild)
    def changeLog = getChangeLog(passedBuilds)
    return "Here is the change log:\n${changeLog}"
}

def lastSuccessfulBuild(passedBuilds, build) {
    if ((build != null) && (build.result != 'SUCCESS')) {
        passedBuilds.add(build)
        lastSuccessfulBuild(passedBuilds, build.getPreviousBuild())
    }
}

@NonCPS
def getChangeLog(passedBuilds) {
    def log = ""
    for (int x = 0; x < passedBuilds.size(); x++) {
        def currentBuild = passedBuilds[x]
        def changeLogSets = currentBuild.changeSets
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                log += " -${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg} \n"
            }
        }
    }
    return log
}
