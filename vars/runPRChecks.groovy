#!/usr/bin/groovy
import com.mainak.Release

def call(String filepath){
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

            stage('Run PR Build and Test') {
                steps {
                    script {
                        def rel = new Release(this)
                        rel.runPRTest("$filepath")
                    }
                }
            }


            stage('Tag and push') {
                steps {
                    script {
                        def properties = steps.readProperties file: filepath
                        def reponame = properties['reponame']
                        echo "Bumping version set of release"
                        try {
                            sh "bump2version patch --allow-dirty"
                        } catch (Exception ex){
                                echo 'Exception occurred: ' + ex.toString()
                                currentBuild.result = 'FAILURE'
                        }
                        withCredentials([usernamePassword(credentialsId: "mainak90", usernameVariable: "username", passwordVariable: "password")]){
                            echo "Repo url : https://$username:$password@github.com/$reponame.git"
                            sh("/usr/bin/git push https://$username:$password@github.com/$reponame.git")
                        }
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