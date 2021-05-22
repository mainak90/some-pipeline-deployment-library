#!/usr/bin/groovy
def call(String filename, String annotation) {
    node {
        def fullfilename = "${env.WORKSPACE}/$filename"
        echo "Reading manifest $fullfilename"
        def yamlfile = readYaml (file: fullfilename)
        def annotations = yamlfile.spec.template.metadata.annotations
        return annotations.contains(annotation)
    }
}