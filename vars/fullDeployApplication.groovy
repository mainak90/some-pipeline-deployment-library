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
                    defaultValue: 'nginx',
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
            choice(
                    name: 'canary',
                    choices: ["False", "True"],
                    description: "Deploy canary set or not"
            )
            string(
                    name: 'version',
                    defaultValue: '0.1.0',
                    description: "Version to deploy, this parameter is only used for production deployment"
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

            stage('Wait for canary approval') {
                agent none
                when {
                    allOf {
                        expression { params.prod == 'True' }
                        expression { params.canary == 'True' }
                    }
                }
                steps {
                    timeout(time: 2, unit: "MINUTES") {
                        input message: 'Do you want to approve the canary deploy to staging?', ok: 'Yes'
                    }
                }
            }

            stage('Check and deploy canaries') {
                when {
                    allOf {
                        expression { params.prod == 'True' }
                        expression { params.canary == 'True' }
                    }
                }
                steps {
                    script {
                        def rel = new Release(this)
                        rel.releaseCanary("${params.version}","${params.filepath}")
                    }
                }
            }

            stage('Wait for production approval') {
                agent none
                when {
                    allOf {
                        expression { params.prod == 'True' }
                    }
                }
                steps {
                    timeout(time: 2, unit: "MINUTES") {
                        input message: 'Do you want to approve the deploy to production?', ok: 'Yes'
                    }
                }
            }

            stage('Check and deploy production') {
                when {
                    allOf {
                        expression { params.prod == 'True' }
                    }
                }
                steps {
                    script {
                        def rel = new Release(this)
                        rel.releaseProduction("${params.version}","${params.filepath}")
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