import json
import os
import subprocess
import threading
from pathlib import Path

import numpy as np
import pytest

from flimkit_bridge.server import BridgeState, create_server

PROBE = Path(__file__).parents[1] / 'plugin' / 'roundtrip_probe.groovy'

pytestmark = pytest.mark.skipif(
    not os.environ.get('FIJI_PATH'),
    reason='set FIJI_PATH to run the Fiji plugin tests',
)


@pytest.fixture
def served(tmp_path):
    state = BridgeState(
        images={
            'intensity': np.arange(35, dtype=np.float32).reshape(5, 7),
            'lifetime': np.arange(35, dtype=np.float32).reshape(5, 7) / 10.0,
        },
        units={'intensity': 'photons', 'lifetime': 'ns'},
    )
    state.exported_rois = {
        'type': 'FeatureCollection',
        'features': [{
            'type': 'Feature',
            'properties': {'name': 'FLIMKit region'},
            'geometry': {
                'type': 'Polygon',
                'coordinates': [[[1.0, 1.0], [4.0, 1.0], [4.0, 3.0], [1.0, 1.0]]],
            },
        }],
    }
    server = create_server('127.0.0.1', 0, 'plugin-token', state)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    host, port = server.server_address[:2]
    discovery = tmp_path / 'bridge.json'
    discovery.write_text(json.dumps({
        'protocol': 'flimkit-bridge',
        'protocol_version': 1,
        'url': f'http://{host}:{port}',
        'token': 'plugin-token',
    }), encoding='utf-8')
    try:
        yield discovery, state
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def _run(discovery):
    completed = subprocess.run(
        [os.environ['FIJI_PATH'], '--headless', '--run', str(PROBE),
         f'discovery="{discovery}"'],
        capture_output=True, text=True, timeout=180)
    output = completed.stdout + completed.stderr
    assert completed.returncode == 0, output
    return output


def test_the_plugin_reads_the_discovery_file(served):
    discovery, _ = served

    output = _run(discovery)

    assert 'PLUGIN_CONNECT_OK' in output, output


def test_a_fiji_region_reaches_flimkit_as_geojson(served):
    discovery, state = served

    output = _run(discovery)

    assert 'PLUGIN_SENT features=1' in output, output
    sent = state.received_rois[-1]
    feature = sent['features'][0]
    assert feature['properties']['name'] == 'Fiji plugin triangle'
    assert feature['geometry']['coordinates'] == [
        [[1.25, 2.5], [4.5, 2.5], [3.0, 4.0], [1.25, 2.5]]]


def test_a_flimkit_region_becomes_an_imagej_roi(served):
    discovery, _ = served

    output = _run(discovery)

    assert 'PLUGIN_FETCHED count=1' in output, output
    assert 'name=FLIMKit region' in output, output
    assert 'points=3' in output, output


def test_the_images_arrive_with_their_units(served):
    discovery, _ = served

    output = _run(discovery)

    assert 'PLUGIN_IMAGE intensity 7x5 unit=photons' in output, output
    assert 'PLUGIN_IMAGE lifetime 7x5 unit=ns' in output, output
