#!/usr/bin/groovy
import com.mainak.Release

def call(){
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

        parameters {
            string(
                    name: 'namespace',
                    defaultValue: 'default',
                    description: 'The namespace to deploy to'
            )
            choice(
                    name: 'prod',
                    choices: ["False", "True"],
                    description: "Deploy to prod or not"
            )
            string(
                    name: 'filepath',
                    defaultValue: 'app.properties',
                    description: 'Path to the property file'
            )
        }

        stages {

            stage('Check and deploy namespace') {
                steps {
                    script {
                        def input = [:]
                        input.namespace = "${params.namespace}"
                        input.prod = "${params.prod}"
                        def rel = new Release(this)
                        rel.overrideAndRelease("${params.filepath}",input)
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

        post {
            always {
                cleanWs()
            }
            failure {
                script {
                    env.logs = currentBuild.rawBuild.getLog(50).join('\n')
                }

            }
        }
    }
}