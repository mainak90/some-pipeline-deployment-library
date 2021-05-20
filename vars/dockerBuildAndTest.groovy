#!/usr/bin/groovy
def call(String projectname, String dockerfilepath = ".", String testfile) {
    def version = readFile(file: 'VERSION')
    echo "Build version : $version"
    echo "Trigerring the build..."
    sh "docker build -t $projectname:$version $dockerfilepath"
    sh "docker save $projectname:$version > $projectname-$version.tar"
    sh "microk8s ctr image import $projectname-$version.tar"
    echo "Running unit tests..."
    try {
        sh "container-structure-test test --image $projectname:$version --config $testfile"
        currentBuild.result = 'SUCCESS'
    } catch (Exception ex){
        error(ex.toString())
        currentBuild.result = 'FAILURE'
    }
}