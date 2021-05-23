#!/usr/bin/groovy
def call(String filename, String annotation, String value) {
    def fullfilename = "${env.WORKSPACE}/$filename"
    echo "Reading manifest $fullfilename"
    def yamlfile = readYaml (file: fullfilename)
    def annotations = yamlfile.spec.template.metadata.annotations["$annotation"]
    return annotations == value
}