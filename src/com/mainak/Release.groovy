package com.mainak

import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsProvider

class Release implements Serializable {

    def steps

    Release(steps) {this.steps = steps}

    def getCommitMessage(String hash) {
        return steps.sh(script: "git show --format='%s' -s $hash", returnStdout: true).trim()
    }


    def getRepoName(String gitUrl) {
        return gitUrl.tokenize('/').last().minus('.git')
    }

    def getLatestCommitHash() {
        return steps.sh(script: "git rev-parse --short HEAD", returnStdout: true)?.trim()
    }

    @NonCPS
    def loadProperties() {
        Properties properties = new Properties()
        this.getClass().getResource('app.properties').withInputStream {
            properties.load(it)
        }
        return properties
    }

    def releaseAllFromProperties(){
        def properties = steps.readProperties file: "app.properties"
        if (properties['app'] == "docker") {
            dockerBuildAndTest(properties['project'], '.', properties['testfile'])
        }
        boolean prod
        if (properties['prod'] == 'False') {
            prod = false
        } else {
            prod = true
        }
        deployToK8s(properties['project'], properties['namespace'], prod)
    }

    @NonCPS
    def deployToK8s(String application, String namespace, boolean Prod){
        String fullpath = "manifest/$application/"
        if (Prod) {
            namespace = "production"
        }
        def namespaceExists = sh returnStatus: true, script: "microk8s.kubectl get namespace $namespace"
        if (namespaceExists != 0) {
            echo "Namespace $namespace does not exist, creating namespace"
            try {
                sh "microk8s.kubectl create namespace $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                currentBuild.result = 'FAILURE'
            }
        } else {
            echo "Namespace $namespace exists!"
        }
        def projectExists = sh returnStatus: true, script: "microk8s.kubectl get deployment $application -n $namespace"
        if (projectExists != 0) {
            echo "Project $application does not exist, creating deployment $application"
            try {
                sh "microk8s.kubectl create -f $fullpath -n $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                currentBuild.result = 'UNSTABLE'
            }
        } else {
            echo "Project $application exists in namespace $namespace"
            try {
                sh "microk8s.kubectl apply -f $fullpath -n $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                currentBuild.result = 'UNSTABLE'
            }
        }
    }

    @NonCPS
    def dockerBuildAndTest(String projectname, String dockerfilepath = ".", String testfile) {
        def version = steps.readFile 'VERSION'
        echo "Build version : $version"
        echo "Trigerring the build..."
        sh "docker build -t $projectname:$version $dockerfilepath"
        sh "docker save $projectname:$version > ${projectname}-${version}.tar"
        sh "microk8s ctr image import ${projectname}-${version}.tar"
        echo "Running unit tests..."
        try {
            sh "container-structure-test test --image $projectname:$version --config $testfile"
            currentBuild.result = 'SUCCESS'
        } catch (Exception ex){
            error(ex.toString())
            currentBuild.result = 'FAILURE'
        }
    }


}