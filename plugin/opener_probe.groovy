#@ Context context
import io.github.flimkit.fiji.FlimFileOpener
import org.scijava.io.IOService

def opener = new FlimFileOpener()
println("OPENER_PTU " + opener.supportsOpen("/tmp/x.ptu"))
println("OPENER_SDT " + opener.supportsOpen("/tmp/x.sdt"))
println("OPENER_TIF " + opener.supportsOpen("/tmp/x.tif"))
println("OPENER_DECODE " + FlimFileOpener.decode(
    "file:/Users/as-hunt/Downloads/Yangchen%20FLIMKit%20troubleshooting%20materials/convallaria.ptu"))

def io = context.getService(IOService.class)
def handler = io.getOpener("/tmp/x.ptu")
println("OPENER_REGISTERED " + (handler == null ? "none" : handler.getClass().getName()))
