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
                    script{
                        def properties = steps.readProperties file: filepath
                        def reponame = properties['repo']
                        env.REPONAME = reponame
                        env.BRANCHNAME = scm.branches[0].name
                        echo "Bumping version set of release"
                        sh "bump2version patch --allow-dirty"
                        env.NEWVER = readFile 'VERSION'
                        sh "git tag ${env.NEWVER} ${env.GIT_COMMIT}"
                    }
                    withCredentials([usernamePassword(credentialsId: "mainak90", usernameVariable: "username", passwordVariable: "password")]){
                        sh "/usr/bin/git push https://$username:$password@github.com/${env.REPONAME}.git --tags --force"
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