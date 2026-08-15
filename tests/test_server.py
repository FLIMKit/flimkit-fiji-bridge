import json
import threading
from io import BytesIO
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import numpy as np
import pytest
import tifffile

from flimkit_fiji_bridge.server import BridgeState, create_server


@pytest.fixture
def running_server():
    state = BridgeState(images={
        'intensity': np.arange(35, dtype=np.float32).reshape(5, 7),
        'lifetime': np.arange(35, dtype=np.float32).reshape(5, 7) / 10.0,
    })
    server = create_server('127.0.0.1', 0, 'test-token', state)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        host = server.server_address[0]
        port = server.server_address[1]
        yield f'http://{host}:{port}', state
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def authorized_request(url, *, data=None, method=None, content_type=None):
    headers = {'Authorization': 'Bearer test-token'}
    if content_type:
        headers['Content-Type'] = content_type
    return Request(url, data=data, method=method, headers=headers)


def test_status_reports_protocol_without_authentication(running_server):
    base_url, _ = running_server

    with urlopen(f'{base_url}/v1/status') as response:
        payload = json.load(response)

    assert response.status == 200
    assert payload == {
        'protocol': 'flimkit-fiji',
        'protocol_version': 1,
    }


def test_image_endpoint_requires_pairing_token(running_server):
    base_url, _ = running_server

    with pytest.raises(HTTPError) as caught:
        urlopen(f'{base_url}/v1/images/intensity.tif')

    assert caught.value.code == 401


@pytest.mark.parametrize('image_id', ['intensity', 'lifetime'])
def test_authorized_image_round_trips_as_float_tiff(running_server, image_id):
    base_url, state = running_server
    request = authorized_request(f'{base_url}/v1/images/{image_id}.tif')

    with urlopen(request) as response:
        received = tifffile.imread(BytesIO(response.read()))

    assert response.status == 200
    assert response.headers['Content-Type'] == 'image/tiff'
    np.testing.assert_array_equal(received, state.images[image_id])
    assert received.dtype == np.float32


def test_image_id_cannot_access_other_state_attributes(running_server):
    base_url, _ = running_server
    request = authorized_request(f'{base_url}/v1/images/received_rois.tif')

    with pytest.raises(HTTPError) as caught:
        urlopen(request)

    assert caught.value.code == 404


def test_authenticated_geojson_roi_is_received_exactly(running_server):
    base_url, state = running_server
    payload = {
        'type': 'FeatureCollection',
        'features': [{
            'type': 'Feature',
            'properties': {'name': 'Cell 1'},
            'geometry': {
                'type': 'Polygon',
                'coordinates': [[[1.25, 2.5], [4.5, 2.5], [1.25, 2.5]]],
            },
        }],
    }
    request = authorized_request(
        f'{base_url}/v1/rois',
        data=json.dumps(payload).encode('utf-8'),
        method='POST',
        content_type='application/geo+json',
    )

    with urlopen(request) as response:
        reply = json.load(response)

    assert response.status == 200
    assert reply == {
        'received_features': 1,
        'imported_region_ids': [0],
    }
    assert state.received_rois == [payload]


def test_authenticated_geojson_rois_are_exported(running_server):
    base_url, state = running_server
    payload = {
        'type': 'FeatureCollection',
        'features': [{
            'type': 'Feature',
            'properties': {'name': 'FLIMKit ROI'},
            'geometry': {
                'type': 'Polygon',
                'coordinates': [[[1, 1], [4, 1], [2, 4], [1, 1]]],
            },
        }],
    }
    state.rois = payload
    request = authorized_request(f'{base_url}/v1/rois')

    with urlopen(request) as response:
        received = json.load(response)

    assert response.status == 200
    assert response.headers['Content-Type'] == 'application/geo+json'
    assert received == payload


def test_roi_export_requires_pairing_token(running_server):
    base_url, _ = running_server

    with pytest.raises(HTTPError) as caught:
        urlopen(f'{base_url}/v1/rois')

    assert caught.value.code == 401
