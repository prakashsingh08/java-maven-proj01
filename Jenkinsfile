pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-17'
            args '-v jenkins-maven-repo:/root/.m2'
        }
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B compile'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Publish') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'cloudsmith-creds', usernameVariable: 'CLOUDSMITH_USER', passwordVariable: 'CLOUDSMITH_API_KEY')]) {
                    sh '''
                        cat > settings-cloudsmith.xml <<EOF
<settings>
  <servers>
    <server>
      <id>cloudsmith</id>
      <username>${CLOUDSMITH_USER}</username>
      <password>${CLOUDSMITH_API_KEY}</password>
    </server>
  </servers>
</settings>
EOF
                        mvn -B deploy -DskipTests -s settings-cloudsmith.xml
                        rm -f settings-cloudsmith.xml
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline succeeded.'
        }
        failure {
            echo 'Pipeline failed.'
        }
    }
}
