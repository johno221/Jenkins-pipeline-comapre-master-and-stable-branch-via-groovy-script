pipeline {
         agent any
         stages {
                 stage('Build') {
                 steps {
                     echo 'Hi, GeekFlare. Starting to build the App.'
                 }}
        
                  stage('test') {
                  steps {
                     script {
                         def test = 2 + 2 > 3 ? 'cool': 'not cool'
                         echo test
                     }
                  }}
                  stage('deploy') {
                  steps {
                     echo 'Deploy the application.'
                  }}
                  
         }}
