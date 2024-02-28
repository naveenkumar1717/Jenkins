pipeline {
    agent any
    environment {
        AWS_DEFAULT_REGION = 'us-east-1'
    }
    parameters {
        string(name: 'gitUrl',defaultValue: 'https://github.com/naveenkumar1717/SpringbootTest.git')
        string(name: 'gitBranch' ,defaultValue: 'main')
        string(name: 'ecrRepo',defaultValue: 'springbootest')
        string(name: 'ecrUri',defaultValue: '315124373014.dkr.ecr.us-east-1.amazonaws.com')
    }
    stages {
        stage('Scm checkout') {
            steps {
             script{
                echo "git checkout"
                def gitUrl = params.gitUrl
                def gitBranch = params.gitBranch
                checkout scmGit(branches: [[name: '${gitBranch}']],
                userRemoteConfigs: [
                    [ credentialsId: 'my-git-credentials', url: '${gitUrl}' ]
                ])
             }
                
            }
        }
        stage ('Build'){
            steps{
              script {
                  sh 'chmod +x gradlew'
                  sh './gradlew clean build'
              }  
            }
        }
        stage('Code Analysis') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'sonar', variable: 'TOKEN')]) {
                    def IP = sh(script: "curl http://169.254.169.254/latest/meta-data/local-ipv4", returnStdout: true).trim()
                    sh "docker run --rm -e SONAR_HOST_URL=http:$IP:9000 -v ${WORKSPACE}:/usr/src -v ${WORKSPACE}:/project sonarsource/sonar-scanner-cli -Dsonar.projectBaseDir=/project -Dsonar.login=$TOKEN -Dsonar.java.binaries=build/classes"
                    // Stop and remove SonarQube container
                    //sh "docker stop $containerId && docker rm $containerId"
  }
                   
                }
            }
        }
          // stage("Quality Gate") {
          //   steps {
          //     script {
          //         timeout(time: 1, unit: 'HOURS') { // Just in case something goes wrong, pipeline will be killed after a timeout
          //         def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
          //         if (qg.status != 'OK') {
          //         error "Pipeline aborted due to quality gate failure: ${qg.status}"
          //        }
          //      }
          //     } 
          //   }
          // }
        stage('Create image & push to ecr'){
            steps{
                script{
                    def ecr_uri = "${params.ecrUri}/springbootest"
                    def reponame = "${params.ecrRepo}"
                    //def user = sh(script: "echo $(whoami)", returnStdout: true).trim()
                    //def reponame = sh(script: "basename ${params.gitUrl} .git", returnStdout: true).trim()
                    def version = sh(script: "cat build.gradle | grep -o \"version\\s*=\\s*['\\\"]\\S*['\\\"]\" | grep -o \"['\\\"]\\S*['\\\"]\" | tr -d \"'\\\"\"", returnStdout: true).trim()
                    def region = "us-east-1"
                    def ecr_domain = "${params.ecrUri}"
                    withCredentials([usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'Access_Key_ID', passwordVariable: 'Secret_Access_Key')]) {
                    sh 'aws configure set aws_access_key_id $Access_Key_ID'
                    sh 'aws configure set aws_secret_access_key $Secret_Access_Key'
                    sh 'aws configure set region $AWS_DEFAULT_REGION'
                    sh "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${ecr_domain}"
                    //sh "sudo usermod -aG docker jenkins ${user}"
                    sh "docker build -t ${reponame} ."
                    // Tag Docker image for ECR
                    sh "docker tag ${reponame}:latest ${ecr_uri}:${version}"
                    // Push Docker image to ECR
                    sh "docker push ${ecr_uri}:${version}"
                    //sh "docker run -d --name sonarqube -p 9000:9000 -p 9092:9092 sonarqube"
                }
              }    
            }
        } 
        
    }
  }
