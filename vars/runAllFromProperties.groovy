import com.mainak.Release

def call(){
    def rel = new Release(this)
    def properties = rel.loadProperties()
    if (properties.app == "docker") {
        dockerBuildAndTest(properties.project, properties.dockerfile, properties.testfile)
    }
    deployToK8s(properties.project, properties.namespace, properties.prod)
}