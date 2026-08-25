#@ String discovery
import io.github.flimkit.fiji.BridgeClient
import io.github.flimkit.fiji.Discovery
import io.github.flimkit.fiji.FetchImagesCommand
import io.github.flimkit.fiji.GeoJson
import ij.gui.PolygonRoi
import ij.gui.Roi
import com.google.gson.JsonParser
import java.nio.file.Paths

def details = Discovery.read(Paths.get(discovery))
def client = new BridgeClient(details.url(), details.token())
println("PLUGIN_CONNECT_OK " + client.baseUrl())

def roi = new PolygonRoi(
    [1.25f, 4.5f, 3.0f] as float[],
    [2.5f, 2.5f, 4.0f] as float[],
    3, Roi.POLYGON)
roi.setName("Fiji plugin triangle")
def reply = JsonParser.parseString(
    client.sendRois(GeoJson.toFeatureCollection([roi]))).getAsJsonObject()
println("PLUGIN_SENT features=" + reply.get("received_features").getAsInt())

def back = GeoJson.toRois(client.fetchRois())
println("PLUGIN_FETCHED count=" + back.size())
back.each {
    def p = it.getFloatPolygon()
    println("  name=" + it.getName() + " points=" + p.npoints)
}

['intensity', 'lifetime'].each { name ->
    def image = FetchImagesCommand.fetch(client, name, "FLIMKit " + name)
    println("PLUGIN_IMAGE " + name + " " + image.getWidth() + "x" + image.getHeight()
        + " unit=" + image.getCalibration().getValueUnit())
}
