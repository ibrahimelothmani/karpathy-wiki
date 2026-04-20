pipeline {
    agent any
tools {
    jdk 'jdk-25'
    maven 'maven-3.8.7'
    }

environment {

    SONAR_HOST = 'https://sonarcloud.io'
    IMAGE = 'karpathy-wiki'
    TAG = 'latest'

    }

    stages {
        stage('Checkout') {
            steps {
                git branch 'main' url 'https://github.com/the-green-1/karpathy-wiki.git'
            }
        }
        stage('Build & Unit Test') {
            steps {
                sh '''
                echo "JAVA_HOME = $JAVA_HOME"
                echo "MAVEN_HOME = $MAVEN_HOME"
                java -version
                ./mvnw -B -U clean verify -Dspring-javaformat.skip
                '''
            }
            post {
                always {
                    junit allowEmptyResults : true, testResults : ' target/surefire-reports/*.xml '
                    archiveArtifacts artifacts : 'target/*.jar', fingerprint : true
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withCredentials ([ string ( credentialsId : '01' , variable : 'SONAR_TOKEN') ]) {
                    sh '''
                    ./ mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                    - Dsonar.projectKey=karpathy-wiki \
                    - Dsonar.organization=the-green-1 \
                    - Dsonar.host.url=https://sonarcloud.io \
                    - Dsonar.login=$SONAR_TOKEN
                    '''
                }
            }
        }

        post {
            success {
                echo 'Pipeline completed successfully'
            }
            failure {
                echo 'Pipeline failed'
            }
            always {
                echo 'Pipeline completed'
            }
        }
    }
}