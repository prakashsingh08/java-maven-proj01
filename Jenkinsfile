pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-17'
            args '-v jenkins-maven-repo:/root/.m2'
        }
    }

    environment {
        CLOUDSMITH = credentials('cloudsmith-creds')
        APP_VERSION = "1.0.${BUILD_NUMBER}"
    }

    stages {
        stage('Build') {
            steps {
                echo "Building version ${APP_VERSION}"
                sh 'mvn -B compile -Drevision=${APP_VERSION}'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test -Drevision=${APP_VERSION}'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests -Drevision=${APP_VERSION}'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Publish') {
            when {
                branch 'main'
            }
            steps {
                sh '''
                    cat > settings-cloudsmith.xml <<EOF
<settings>
  <servers>
    <server>
      <id>cloudsmith</id>
      <username>${CLOUDSMITH_USR}</username>
      <password>${CLOUDSMITH_PSW}</password>
    </server>
  </servers>
</settings>
EOF
                    mvn -B deploy -DskipTests -Drevision=${APP_VERSION} -s settings-cloudsmith.xml
                    rm -f settings-cloudsmith.xml
                '''
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
