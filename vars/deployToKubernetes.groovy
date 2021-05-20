#!/usr/bin/groovy
def call(String application, String namespace){
        String fullpath = "manifest/$application"
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