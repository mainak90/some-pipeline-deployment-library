import com.mainak.Release

def call(){
    def rel = new Release(this)
    rel.releaseAllFromProperties()
}