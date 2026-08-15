from types import SimpleNamespace

import numpy as np
import pytest

from flimkit_fiji_bridge.flimkit_adapter import (
    FlimkitCompatibilityError,
    FlimkitDataSource,
)


class _Bindings:
    def __init__(self):
        self.calls = []

    def get_current_images(self, app):
        self.calls.append(('images', app))
        return {
            'intensity': np.ones((2, 3), dtype=np.float32),
            'lifetime': np.full((2, 3), 2.5, dtype=np.float32),
            'private-map': np.zeros((2, 3), dtype=np.float32),
        }

    def export_rois_geojson(self, app):
        self.calls.append(('export', app))
        return {'type': 'FeatureCollection', 'features': []}

    def import_rois_geojson(self, app, payload, mode='append'):
        self.calls.append(('import', app, payload, mode))
        return [4, 5]


def test_adapter_calls_public_flimkit_bindings_with_live_app():
    app = object()
    bindings = _Bindings()
    source = FlimkitDataSource(app, bindings=bindings)
    payload = {'type': 'FeatureCollection', 'features': []}

    images = source.get_images()
    exported = source.export_rois()
    region_ids = source.import_rois(payload)

    assert set(images) == {'intensity', 'lifetime'}
    assert exported == payload
    assert region_ids == [4, 5]
    assert bindings.calls == [
        ('images', app),
        ('export', app),
        ('import', app, payload, 'append'),
    ]


def test_adapter_rejects_flimkit_without_public_bindings():
    old_bindings = SimpleNamespace(get_current_images=lambda app: {})

    with pytest.raises(
        FlimkitCompatibilityError,
        match='requires a newer FLIMKit version',
    ):
        FlimkitDataSource(object(), bindings=old_bindings)
