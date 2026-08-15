import os
import subprocess
import threading
from pathlib import Path

import numpy as np
import pytest

from flimkit_fiji_bridge.demo_server import BridgeState, create_server


SCRIPT = Path(__file__).parents[1] / 'fiji' / 'FijiBridgeDemo.groovy'


@pytest.fixture
def running_fiji_demo_server():
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


def test_fiji_demo_uses_java_8_compatible_http_client():
    source = SCRIPT.read_text(encoding='utf-8')

    assert 'java.net.HttpURLConnection' in source
    assert 'java.net.http' not in source


def test_fiji_demo_contains_clear_java_error():
    source = SCRIPT.read_text(encoding='utf-8')

    assert 'Fiji Bridge requires Java 8 or newer' in source
    assert 'download a current Fiji release with its bundled JDK' in source


def test_installed_fiji_fetches_images_and_posts_roi(running_fiji_demo_server):
    fiji_path = os.environ.get('FIJI_PATH')
    if not fiji_path:
        pytest.skip('set FIJI_PATH to run the live Fiji demo test')
    assert fiji_path is not None
    base_url, state = running_fiji_demo_server

    completed = subprocess.run(
        [
            fiji_path,
            '--headless',
            '--run',
            str(SCRIPT),
            f'baseUrl="{base_url}",token="test-token"',
        ],
        capture_output=True,
        text=True,
        timeout=120,
    )

    output = completed.stdout + completed.stderr
    assert completed.returncode == 0, output
    assert 'FIJI_JAVA_OK version=' in output, output
    assert 'FIJI_IMAGES_OK intensity=34.0 lifetime=3.4' in output, output
    assert 'FIJI_ROI_POST_OK features=1' in output, output
    assert len(state.received_rois) == 1
    feature = state.received_rois[0]['features'][0]
    assert feature['properties']['name'] == 'Fiji polygon'
    assert feature['geometry']['coordinates'] == [
        [[1.25, 2.5], [4.5, 2.5], [3.0, 4.0], [1.25, 2.5]],
    ]
