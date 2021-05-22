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

    def overrideAndRelease(String filepath, Map input){
        def properties = steps.readProperties file: filepath
        if (properties['namespace'] == input.namespace && properties['prod'] == input.prod) {
            releaseAllFromProperties(filepath)
        } else {
            if (properties['app'] == "docker") {
                dockerBuildAndTest(properties['project'], '.', properties['testfile'])
            }
            boolean prod
            prod = false
            deployToK8s(properties['project'], input.namespace, prod)
        }
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
                steps.echo 'Exception occurred: ' + ex.toString()
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
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'UNSTABLE'
            }
        } else {
            steps.echo "Project $application exists in namespace $namespace"
            try {
                steps.sh "microk8s.kubectl apply -f $fullpath -n $namespace"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
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
            steps.echo 'Exception occurred: ' + ex.toString()
            steps.currentBuild.result = 'FAILURE'
        }
    }

    def getOrBuildImage(String appname, String version, String dockerfilepath = ".") {
        def imageExists = steps.sh(returnStatus: true, script: "microk8s ctr image list | grep $appname | grep $version")
        if (imageExists != 0) {
            steps.echo "Trigerring new image build..."
            steps.sh "docker build -t $appname:$version $dockerfilepath"
            steps.sh "docker save $appname:$version > ${appname}-${version}.tar"
            steps.sh "microk8s ctr image import ${appname}-${version}.tar"
        } else {
            steps.echo "Image $appname:$version exists in local registry, preoceeding with release.."
        }
    }

    def releaseCanary(String version, String filepath) {
        def properties = steps.readProperties file: filepath
        def projectname = properties['project']
        getOrBuildImage(projectname, version, ".")
        steps.sh "find manifest/$projectname/canary/staging/ -type f | xargs sed -i 's/VERSION/$version/g'"
        def namespaceExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get namespace canary")
        if (namespaceExists != 0) {
            steps.echo "Namespace canary does not exist, creating namespace"
            try {
                steps.sh "microk8s.kubectl create namespace canary"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'FAILURE'
            }
        } else {
            steps.echo "Namespace canary exists!"
        }
        def projectExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get deployment $projectname -n canary")
        if (projectExists != 0) {
            steps.echo "Project $projectname does not exist, creating deployment $projectname"
            try {
                steps.sh "microk8s.kubectl create -f manifest/$projectname/canary/staging/ -n canary"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'UNSTABLE'
            }
        } else {
            steps.echo "Project $projectname exists in namespace canary"
            try {
                steps.sh "microk8s.kubectl apply -f manifest/$projectname/canary/staging/ -n canary"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'UNSTABLE'
            }
        }
    }

    def releaseProduction(String version, String filepath){
        def properties = steps.readProperties file: filepath
        def projectname = properties['project']
        getOrBuildImage(projectname, version, ".")
        steps.sh "find manifest/$projectname/canary/prod/ -type f | xargs sed -i 's/VERSION/$version/g'"
        def namespaceExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get namespace production")
        if (namespaceExists != 0) {
            steps.echo "Namespace production does not exist, creating namespace"
            try {
                steps.sh "microk8s.kubectl create namespace production"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'FAILURE'
            }
        } else {
            steps.echo "Namespace production exists!"
        }
        def projectExists = steps.sh(returnStatus: true, script: "microk8s.kubectl get deployment $projectname -n production")
        if (projectExists != 0) {
            steps.echo "Project $projectname does not exist, creating deployment $projectname"
            try {
                steps.sh "microk8s.kubectl create -f manifest/$projectname/canary/prod/ -n production"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'UNSTABLE'
            }
        } else {
            steps.echo "Project $projectname exists in namespace production"
            try {
                steps.sh "microk8s.kubectl apply -f manifest/$projectname/canary/prod/ -n production"
            } catch (Exception ex) {
                steps.echo 'Exception occurred: ' + ex.toString()
                steps.currentBuild.result = 'UNSTABLE'
            }
        }
    }

}