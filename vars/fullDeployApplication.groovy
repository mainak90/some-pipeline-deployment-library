import com.mainak.Release

def call(Map input, String filepath){
    try {
        pipeline {
            agent {
                node {
                    label "worker"
                }
            }

            options {
                buildDiscarder(logRotator(numToKeepStr: '30'))
                timeout(time: 1, unit: 'HOURS')
                timestamps()
            }

            stages {
                stage('Check and deploy namespace') {
                    steps {
                        script {
                            def rel = new Release(this)
                            rel.overrideAndRelease(input,filepath)
                        }
                    }
                }

                stage('Wait for approval') {
                    agent none
                    steps {
                        timeout(time: 2, unit: "MINUTES") {
                            input message: 'Do you want to approve the deploy in production?', ok: 'Yes'
                        }
                    }
                }

            }
    } catch (Exception ex) {
        error(ex.toString())
        currentBuild.result = 'FAILURE'
    }
}