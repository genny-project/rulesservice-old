pipeline {
	agent any
  tools {
      maven 'mvn 3.5'
  }
	stages {
		stage('Build') {
			steps {
				sh 'mvn clean package -U -DskipTests=false'
			}
		}
		stage('Deploy') {
      when { branch 'master'}
			steps {
				sh 'echo Deploying...'
			}
		}
		stage('Done') {
			steps {
				sh 'echo Slacking'
			}
		}
	}
	post {
		always {
	    deleteDir()
	  }
	}
}
