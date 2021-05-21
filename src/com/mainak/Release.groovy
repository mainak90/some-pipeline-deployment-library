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

    def releaseAllFromProperties(String filepath){
        def properties = steps.readProperties file: filepath
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


    def deployToK8s(String application, String namespace, boolean Prod){
        String fullpath = "manifest/$application/"
        if (Prod) {
            namespace = "production"
        }
        def namespaceExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get namespace $namespace")
        if (namespaceExists != 0) {
            steps.echo "Namespace $namespace does not exist, creating namespace"
            try {
                steps.sh "microk8s.kubectl create namespace $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                steps.currentBuild.result = 'FAILURE'
            }
        } else {
            steps.echo "Namespace $namespace exists!"
        }
        def projectExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get deployment $application -n $namespace")
        if (projectExists != 0) {
            steps.echo "Project $application does not exist, creating deployment $application"
            try {
                steps.sh "microk8s.kubectl create -f $fullpath -n $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                steps.currentBuild.result = 'UNSTABLE'
            }
        } else {
            steps.echo "Project $application exists in namespace $namespace"
            try {
                steps.sh "microk8s.kubectl apply -f $fullpath -n $namespace"
            } catch (Exception ex) {
                error(ex.toString())
                steps.currentBuild.result = 'UNSTABLE'
            }
        }
    }


    def dockerBuildAndTest(String projectname, String dockerfilepath = ".", String testfile) {
        def version = steps.readFile 'VERSION'
        steps.echo "Build version : $version"
        steps.echo "Trigerring the build..."
        steps.sh "docker build -t $projectname:$version $dockerfilepath"
        steps.sh "docker save $projectname:$version > ${projectname}-${version}.tar"
        steps.sh "microk8s ctr image import ${projectname}-${version}.tar"
        steps.echo "Running unit tests..."
        try {
            steps.sh "container-structure-test test --image $projectname:$version --config $testfile"
            steps.currentBuild.result = 'SUCCESS'
        } catch (Exception ex){
            error(ex.toString())
            steps.currentBuild.result = 'FAILURE'
        }
    }


}