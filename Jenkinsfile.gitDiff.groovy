currentBuild.displayName = "${env.BUILD_NUMBER}"
String.metaClass.getEmpty = { delegate.allWhitespace }
String gitLogMessage


pipeline {
    agent {
        label 'slexng-slave'
    }
    environment {
        EMAIL_RECIPIENTS = 'ADD HERE EMAIL' 
        REGISTRY_CREDS = credentials('gitlabJenkins')
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Compare main with stable') {
            steps {
                script {
                    gitLogCommand = "git log --oneline --graph --decorate --abbrev-commit origin/stable..origin/main"
                    gitLogMessage = sh(returnStdout: true, script: gitLogCommand).trim()


                    if (gitLogMessage.length() > 0) {
                        println "Commits are not merged from main to stable branch"
                        println "########## GIT LOG DIFF START ##########"
                        println gitLogMessage
                        println "########## GIT LOG DIFF END ##########"

                        currentBuild.result = 'FAILURE'
                    } else {
                        println "Main and stable branche are same"
                    }
                }
            }
        }
    }

    post {
        success {
            emailext attachLog: false,
                    body: "See ${env.BUILD_URL}\n\n Master and stable branch are mattching, all commits are merged",
                    subject: """Slexng-${env.BUILD_NUMBER} Difference between MAIN and STABLE branch:""",
                    to: "${EMAIL_RECIPIENTS}"
        }
        failure {
            emailext attachLog: false,
                    body: "See ${env.BUILD_URL}\n\n Master and stable branch are not mattching, please merge following commits to stable branch:\n\n${gitLogMessage}\n\n",
                    subject: """Slexng main branch is not merged to stable""",
                    recipientProviders: [[$class: 'RequesterRecipientProvider'], [$class: 'CulpritsRecipientProvider']],
                    to: "${EMAIL_RECIPIENTS}"
        }
    }
}
