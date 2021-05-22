#!/usr/bin/groovy
def call(String filename, String annotation) {
    def yamlfile = readYaml (file: filename)
    def annotations = yamlfile.spec.template.metadata.annotations
    return annotations.contains(annotation)
}