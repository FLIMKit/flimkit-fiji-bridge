#@ String baseUrl
#@ String token

import ij.io.Opener
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale


def javaVersion = System.getProperty('java.specification.version', '0')
def javaMajor = javaVersion.startsWith('1.')
    ? Integer.parseInt(javaVersion.substring(2))
    : Integer.parseInt(javaVersion.tokenize('.')[0])
if (javaMajor < 8) {
    def message = 'Fiji Bridge requires Java 8 or newer. Please download a current Fiji release with its bundled JDK.'
    System.err.println(message)
    throw new IllegalStateException(message)
}
println("FIJI_JAVA_OK version=${javaVersion}")


def openConnection = { String path, String method ->
    def connection = (HttpURLConnection) new URL("${baseUrl}${path}").openConnection()
    connection.setRequestMethod(method)
    connection.setRequestProperty('Authorization', "Bearer ${token}")
    connection.setConnectTimeout(10000)
    connection.setReadTimeout(10000)
    return connection
}

def readResponse = { HttpURLConnection connection ->
    def status = connection.getResponseCode()
    def stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()
    def body = stream == null ? new byte[0] : stream.bytes
    def valueUnit = connection.getHeaderField('X-FLIMKit-Value-Unit')
    if (stream != null) {
        stream.close()
    }
    return [status, body, valueUnit]
}

def fetchTiff = { String imageId, String title ->
    def connection = openConnection("/v1/images/${imageId}.tif", 'GET')
    def response
    try {
        response = readResponse(connection)
    } finally {
        connection.disconnect()
    }
    if (response[0] != 200) {
        throw new IllegalStateException("GET ${imageId} returned ${response[0]}")
    }
    def valueUnit = response[2]
    if (valueUnit == null || valueUnit.isEmpty()) {
        throw new IllegalStateException("GET ${imageId} returned no value unit")
    }
    def image = new Opener().openTiff(
        new ByteArrayInputStream((byte[]) response[1]), title)
    if (image == null) {
        throw new IllegalStateException("Fiji could not decode ${imageId} TIFF")
    }
    image.getCalibration().setValueUnit(valueUnit)
    image.setProperty('FLIMKit value unit', valueUnit)
    if (image.getWidth() != 7 || image.getHeight() != 5) {
        throw new IllegalStateException(
            "${imageId} shape was ${image.getWidth()}x${image.getHeight()}, expected 7x5")
    }
    return image
}


def intensity = fetchTiff('intensity', 'FLIMKit intensity')
def lifetime = fetchTiff('lifetime', 'FLIMKit lifetime')
def intensityValue = intensity.getProcessor().getf(6, 4)
def lifetimeValue = lifetime.getProcessor().getf(6, 4)
if (intensityValue != 34.0f || Math.abs(lifetimeValue - 3.4f) > 1e-6f) {
    throw new IllegalStateException(
        "pixel mismatch: intensity=${intensityValue}, lifetime=${lifetimeValue}")
}
println(String.format(
    Locale.US,
    'FIJI_IMAGES_OK intensity=%.1f lifetime=%.1f',
    intensityValue,
    lifetimeValue,
))
def intensityUnit = intensity.getCalibration().getValueUnit()
def lifetimeUnit = lifetime.getCalibration().getValueUnit()
if (intensityUnit != 'photons' || lifetimeUnit != 'ns') {
    throw new IllegalStateException(
        "unit mismatch: intensity=${intensityUnit}, lifetime=${lifetimeUnit}")
}
println("FIJI_UNITS_OK intensity=${intensityUnit} lifetime=${lifetimeUnit}")


def geojson = '''{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "properties": {"name": "Fiji polygon"},
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[1.25, 2.5], [4.5, 2.5], [3.0, 4.0], [1.25, 2.5]]]
    }
  }]
}'''
def requestBody = geojson.getBytes('UTF-8')
def postConnection = openConnection('/v1/rois', 'POST')
postConnection.setRequestProperty('Content-Type', 'application/geo+json')
postConnection.setDoOutput(true)
postConnection.setFixedLengthStreamingMode(requestBody.length)
def output = postConnection.getOutputStream()
try {
    output.write(requestBody)
} finally {
    output.close()
}
def postResponse
try {
    postResponse = readResponse(postConnection)
} finally {
    postConnection.disconnect()
}
def responseText = new String((byte[]) postResponse[1], 'UTF-8')
if (postResponse[0] != 200) {
    throw new IllegalStateException(
        "POST ROIs returned ${postResponse[0]}: ${responseText}")
}
if (!responseText.contains('"received_features": 1')) {
    throw new IllegalStateException("unexpected POST response: ${responseText}")
}
println('FIJI_ROI_POST_OK features=1')
System.exit(0)
