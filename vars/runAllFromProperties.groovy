import com.mainak.Release

def call(String filepath){
    def rel = new Release(this)
    rel.releaseAllFromProperties(filepath)
}