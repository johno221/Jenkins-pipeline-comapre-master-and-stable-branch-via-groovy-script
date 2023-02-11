pipeline {
    agent any 
    stages {
        stage('build') {
            steps {
               echo 'building the application' 
            }
        }
        stage('test') {
            steps {
                echo 'test the application'
               
            }
        }
        stage('deploy') {
            steps {
                echo 'deploy the application'
               
            }
        }
    }
