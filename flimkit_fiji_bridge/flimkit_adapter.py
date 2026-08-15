from typing import Any, Dict, List


_REQUIRED_BINDINGS = (
    'get_current_images',
    'export_rois_geojson',
    'import_rois_geojson',
)


class FlimkitCompatibilityError(RuntimeError):
    pass


class FlimkitDataSource:
    """Bridge data source backed by FLIMKit's public plugin bindings."""

    def __init__(self, app, bindings=None):
        if bindings is None:
            from flimkit import plugins as bindings

        missing = [
            name for name in _REQUIRED_BINDINGS
            if not callable(getattr(bindings, name, None))
        ]
        if missing:
            names = ', '.join(missing)
            raise FlimkitCompatibilityError(
                'The Fiji bridge requires a newer FLIMKit version with public '
                f'image and ROI bindings. Missing: {names}. Update FLIMKit and '
                'restart it.',
            )
        self._app = app
        self._bindings = bindings

    def get_images(self) -> Dict[str, Any]:
        current = self._bindings.get_current_images(self._app)
        images = current['images']
        units = current['units']
        selected_images = {
            name: images[name]
            for name in ('intensity', 'lifetime')
            if name in images
        }
        return {
            'images': selected_images,
            'units': {
                name: units[name]
                for name in selected_images
            },
        }

    def export_rois(self) -> Dict:
        return self._bindings.export_rois_geojson(self._app)

    def import_rois(self, payload: Dict) -> List[int]:
        return self._bindings.import_rois_geojson(
            self._app,
            payload,
            mode='append',
        )
