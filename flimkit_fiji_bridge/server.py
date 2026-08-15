import copy
import json
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from typing import Any, Dict, List, Protocol

import numpy as np
import tifffile


_MAX_ROI_BYTES = 1_000_000


class BridgeDataSource(Protocol):
    def get_images(self) -> Dict[str, Any]: ...

    def export_rois(self) -> Dict: ...

    def import_rois(self, payload: Dict) -> List[int]: ...


@dataclass
class BridgeState:
    """In-memory data source used by isolated bridge tests."""

    images: dict[str, np.ndarray]
    received_rois: list[dict] = field(default_factory=list)
    rois: dict = field(default_factory=lambda: {
        'type': 'FeatureCollection',
        'features': [],
    })
    _next_region_id: int = field(default=0, init=False, repr=False)

    def get_images(self) -> Dict[str, Any]:
        return self.images

    def export_rois(self) -> Dict:
        return copy.deepcopy(self.rois)

    def import_rois(self, payload: Dict) -> List[int]:
        stored = copy.deepcopy(payload)
        self.received_rois.append(stored)
        self.rois = stored
        count = len(payload.get('features', []))
        region_ids = list(range(self._next_region_id, self._next_region_id + count))
        self._next_region_id += count
        return region_ids


def create_server(
    host: str,
    port: int,
    token: str,
    source: BridgeDataSource,
):
    class Handler(BaseHTTPRequestHandler):
        def _authorized(self):
            return self.headers.get('Authorization') == f'Bearer {token}'

        def _send_json(self, status, payload, content_type='application/json'):
            body = json.dumps(payload).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _require_authorization(self):
            if self._authorized():
                return True
            self.send_error(401)
            return False

        def do_GET(self):
            if self.path == '/v1/status':
                self._send_json(200, {
                    'protocol': 'flimkit-fiji',
                    'protocol_version': 1,
                })
                return

            if self.path == '/v1/rois':
                if not self._require_authorization():
                    return
                try:
                    payload = source.export_rois()
                except Exception as error:
                    self._send_json(503, {'error': str(error)})
                    return
                self._send_json(200, payload, 'application/geo+json')
                return

            prefix = '/v1/images/'
            if self.path.startswith(prefix) and self.path.endswith('.tif'):
                if not self._require_authorization():
                    return
                image_id = self.path[len(prefix):-len('.tif')]
                try:
                    images = source.get_images()
                    image = images[image_id]
                except KeyError:
                    self.send_error(404)
                    return
                except Exception as error:
                    self._send_json(503, {'error': str(error)})
                    return
                buffer = BytesIO()
                tifffile.imwrite(
                    buffer,
                    np.asarray(image, dtype=np.float32),
                )
                body = buffer.getvalue()
                self.send_response(200)
                self.send_header('Content-Type', 'image/tiff')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            self.send_error(404)

        def do_POST(self):
            if self.path != '/v1/rois':
                self.send_error(404)
                return
            if not self._require_authorization():
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
            except ValueError:
                self.send_error(400)
                return
            if length <= 0:
                self.send_error(400)
                return
            if length > _MAX_ROI_BYTES:
                self.send_error(413)
                return
            try:
                payload = json.loads(self.rfile.read(length).decode('utf-8'))
            except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
                self.send_error(400)
                return
            if (
                not isinstance(payload, dict)
                or payload.get('type') != 'FeatureCollection'
                or not isinstance(payload.get('features'), list)
            ):
                self.send_error(400)
                return
            try:
                region_ids = source.import_rois(payload)
            except ValueError as error:
                self._send_json(400, {'error': str(error)})
                return
            except Exception as error:
                self._send_json(503, {'error': str(error)})
                return
            self._send_json(200, {
                'received_features': len(payload['features']),
                'imported_region_ids': region_ids,
            })

        def log_message(self, format, *args):
            pass

    return ThreadingHTTPServer((host, port), Handler)
