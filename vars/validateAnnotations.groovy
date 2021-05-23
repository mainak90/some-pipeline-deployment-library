#!/usr/bin/groovy
def call(String filename, String annotation) {
    def fullfilename = "${env.WORKSPACE}/$filename"
    echo "Reading manifest $fullfilename"
    def yamlfile = readYaml (file: fullfilename)
    def annotations = yamlfile.get('spec').get('template').get('metadata').get('annotations')
    echo "$annotations"
    return annotations.containsKey("$annotation")
}