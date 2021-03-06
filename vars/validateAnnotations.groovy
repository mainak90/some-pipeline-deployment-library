#!/usr/bin/groovy
def call(String filename, String annotation) {
    def fullfilename = "${env.WORKSPACE}/$filename"
    echo "Reading manifest $fullfilename"
    sh(script: "cat $fullfilename", returnStdout: true)
    def yamlfile = readYaml (file: fullfilename)
    def annotations = yamlfile.spec.template.metadata.annotations
    echo "$annotations"
    return annotations.containsKey("$annotation")
}