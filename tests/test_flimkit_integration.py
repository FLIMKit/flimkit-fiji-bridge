import numpy as np

from flimkit.UI.roi_tools import RoiManager
from flimkit_fiji_bridge.flimkit_adapter import FlimkitDataSource


class _Preview:
    def __init__(self, intensity, lifetime, manager):
        self._intensity_map = intensity
        self._lifetime_map = lifetime
        self._roi_manager = manager
        self._roi_analysis_panel = None
        self.redraw_count = 0
        self.save_count = 0

    def _redraw_region_overlays(self):
        self.redraw_count += 1

    def _save_regions_update(self):
        self.save_count += 1


class _App:
    def __init__(self, preview):
        self._fov_preview = preview
        self.root = None


def test_adapter_uses_real_flimkit_image_and_roi_bindings():
    intensity = np.arange(24, dtype=np.float32).reshape(3, 4, 2)
    expected_intensity = intensity.sum(axis=2)
    lifetime = expected_intensity / 10.0
    manager = RoiManager()
    region_id = manager.add_region(
        'FLIMKit rectangle',
        'rect',
        [[1, 1], [3, 2]],
    )
    region = manager.get_region(region_id)
    assert region is not None
    region['statistics'] = {'tau_median': 2.5}
    preview = _Preview(intensity, lifetime, manager)
    source = FlimkitDataSource(_App(preview))

    images = source.get_images()
    exported = source.export_rois()
    region_ids = source.import_rois({
        'type': 'FeatureCollection',
        'features': [{
            'type': 'Feature',
            'properties': {'name': 'Fiji polygon'},
            'geometry': {
                'type': 'Polygon',
                'coordinates': [[[0, 0], [2, 0], [1, 2], [0, 0]]],
            },
        }],
    })

    np.testing.assert_array_equal(images['intensity'], expected_intensity)
    np.testing.assert_array_equal(images['lifetime'], lifetime)
    assert images['intensity'].ndim == 2
    properties = exported['features'][0]['properties']
    assert properties['name'] == 'FLIMKit rectangle'
    assert properties['statistics'] == {'tau_median': 2.5}
    assert 'tau_median' not in properties
    imported = manager.get_region(region_ids[0])
    assert imported is not None
    assert imported['name'] == 'Fiji polygon'
    assert preview.redraw_count == 1
    assert preview.save_count == 1
