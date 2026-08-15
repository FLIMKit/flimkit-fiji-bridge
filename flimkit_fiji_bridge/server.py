import copy
import ipaddress
import json
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from typing import Any, Dict, List, Protocol

import numpy as np
import tifffile


_MAX_ROI_BYTES = 1_000_000


def is_loopback_host(host: str) -> bool:
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


class BridgeDataSource(Protocol):
    def get_images(self) -> Dict[str, Any]: ...

    def export_rois(self) -> Dict: ...

    def import_rois(self, payload: Dict) -> List[int]: ...


@dataclass
class BridgeState:
    """In-memory data source used by isolated bridge tests."""

    images: dict[str, np.ndarray]
    units: dict[str, str] = field(default_factory=dict)
    received_rois: list[dict] = field(default_factory=list)
    rois: dict = field(default_factory=lambda: {
        'type': 'FeatureCollection',
        'features': [],
    })
    _next_region_id: int = field(default=0, init=False, repr=False)

    def get_images(self) -> Dict[str, Any]:
        return {
            'images': self.images,
            'units': self.units,
        }

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
    if not is_loopback_host(host):
        raise ValueError(
            'The Fiji bridge may bind only to a numeric loopback address',
        )

    class Handler(BaseHTTPRequestHandler):
        def _authorized(self):
            return self.headers.get('Authorization') == f'Bearer {token}'

        def _send_body(self, status, body, content_type, headers=None):
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            for name, value in (headers or {}).items():
                self.send_header(name, value)
            self.end_headers()
            self.wfile.write(body)

        def _send_json(self, status, payload, content_type='application/json'):
            body = json.dumps(payload).encode('utf-8')
            self._send_body(status, body, content_type)

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
                    body = json.dumps(payload).encode('utf-8')
                except Exception as error:
                    self._send_json(503, {'error': str(error)})
                    return
                self._send_body(200, body, 'application/geo+json')
                return

            prefix = '/v1/images/'
            if self.path.startswith(prefix) and self.path.endswith('.tif'):
                if not self._require_authorization():
                    return
                image_id = self.path[len(prefix):-len('.tif')]
                try:
                    image_bundle = source.get_images()
                    images = image_bundle['images']
                    units = image_bundle['units']
                except Exception as error:
                    self._send_json(503, {'error': str(error)})
                    return
                if not isinstance(images, dict) or not isinstance(units, dict):
                    self._send_json(503, {'error': 'Image metadata is unavailable'})
                    return
                if image_id not in images:
                    self.send_error(404)
                    return
                image = images[image_id]
                unit = units.get(image_id)
                if (
                    not isinstance(unit, str)
                    or not unit
                    or '\r' in unit
                    or '\n' in unit
                ):
                    self._send_json(503, {'error': 'Image unit is unavailable'})
                    return
                try:
                    buffer = BytesIO()
                    tifffile.imwrite(
                        buffer,
                        np.asarray(image, dtype=np.float32),
                    )
                    body = buffer.getvalue()
                except Exception as error:
                    self._send_json(503, {'error': str(error)})
                    return
                self._send_body(
                    200,
                    body,
                    'image/tiff',
                    headers={'X-FLIMKit-Value-Unit': unit},
                )
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
            except (TypeError, ValueError) as error:
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
