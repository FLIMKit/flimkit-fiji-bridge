import json
import sys
from types import SimpleNamespace
from urllib.request import urlopen

import numpy as np
import pytest

from flimkit_fiji_bridge.runtime import BridgeRuntime
from flimkit_fiji_bridge.server import BridgeState


def _source_factory(app):
    assert app is _APP
    return BridgeState(images={
        'intensity': np.ones((2, 3), dtype=np.float32),
        'lifetime': np.full((2, 3), 2.5, dtype=np.float32),
    })


_APP = object()


def test_runtime_starts_one_authenticated_loopback_server():
    runtime = BridgeRuntime(source_factory=_source_factory)

    try:
        first = runtime.start(_APP, token='known-token')
        second = runtime.start(_APP, token='different-token')

        assert first == second
        assert first.base_url.startswith('http://127.0.0.1:')
        assert first.token == 'known-token'
        assert runtime.running
        with urlopen(f'{first.base_url}/v1/status') as response:
            payload = json.load(response)
        assert payload['protocol'] == 'flimkit-fiji'
    finally:
        runtime.stop()

    assert not runtime.running


def test_runtime_rejects_non_loopback_binding():
    runtime = BridgeRuntime(source_factory=_source_factory)

    with pytest.raises(ValueError, match='loopback'):
        runtime.start(_APP, host='0.0.0.0')


def test_plugin_tool_starts_runtime_and_shows_pairing_details(monkeypatch):
    import flimkit_fiji_bridge

    calls = []
    connection = SimpleNamespace(
        base_url='http://127.0.0.1:8123',
        token='pairing-token',
    )
    runtime = SimpleNamespace(start=lambda app: calls.append(app) or connection)
    messages = []
    monkeypatch.setattr(flimkit_fiji_bridge, '_RUNTIME', runtime)
    messagebox = SimpleNamespace(
        showinfo=lambda title, message, parent=None: messages.append(
            (title, message, parent),
        ),
        showerror=lambda *args, **kwargs: None,
    )
    monkeypatch.setitem(
        sys.modules,
        'tkinter',
        SimpleNamespace(messagebox=messagebox),
    )
    app = SimpleNamespace(root=object())

    flimkit_fiji_bridge.open_bridge(app)

    assert calls == [app]
    assert len(messages) == 1
    assert 'http://127.0.0.1:8123' in messages[0][1]
    assert 'pairing-token' in messages[0][1]
    assert messages[0][2] is app.root


def test_runtime_closes_server_if_thread_start_fails(monkeypatch):
    import flimkit_fiji_bridge.runtime as runtime_module

    class Server:
        server_address = ('127.0.0.1', 8123)

        def __init__(self):
            self.close_count = 0

        def serve_forever(self):
            pass

        def server_close(self):
            self.close_count += 1

    server = Server()
    monkeypatch.setattr(runtime_module, 'create_server', lambda *args: server)
    monkeypatch.setattr(
        runtime_module.threading.Thread,
        'start',
        lambda self: (_ for _ in ()).throw(RuntimeError('thread failed')),
    )
    runtime = BridgeRuntime(source_factory=_source_factory)

    with pytest.raises(RuntimeError, match='thread failed'):
        runtime.start(_APP, token='known-token')

    assert server.close_count == 1
    assert not runtime.running
