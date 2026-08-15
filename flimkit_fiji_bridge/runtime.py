import ipaddress
import secrets
import threading
from dataclasses import dataclass
from typing import Callable, Optional

from .flimkit_adapter import FlimkitDataSource
from .server import BridgeDataSource, create_server


@dataclass(frozen=True)
class BridgeConnection:
    base_url: str
    token: str


class BridgeRuntime:
    def __init__(
        self,
        source_factory: Callable[[object], BridgeDataSource] = FlimkitDataSource,
    ):
        self._source_factory = source_factory
        self._lock = threading.Lock()
        self._server = None
        self._thread: Optional[threading.Thread] = None
        self._app = None
        self._connection: Optional[BridgeConnection] = None

    @property
    def running(self) -> bool:
        with self._lock:
            return self._server is not None

    def start(
        self,
        app,
        host: str = '127.0.0.1',
        port: int = 0,
        token: Optional[str] = None,
    ) -> BridgeConnection:
        if not _is_loopback(host):
            raise ValueError('The Fiji bridge may bind only to a loopback address')

        with self._lock:
            if self._connection is not None:
                if app is not self._app:
                    raise RuntimeError(
                        'The Fiji bridge is already running for another FLIMKit session',
                    )
                return self._connection

            source = self._source_factory(app)
            pairing_token = token or secrets.token_urlsafe(24)
            server = create_server(host, port, pairing_token, source)
            address, selected_port = server.server_address[:2]
            connection = BridgeConnection(
                base_url=f'http://{address}:{selected_port}',
                token=pairing_token,
            )
            thread = threading.Thread(
                target=server.serve_forever,
                name='flimkit-fiji-bridge',
                daemon=True,
            )
            thread.start()
            self._server = server
            self._thread = thread
            self._app = app
            self._connection = connection
            return connection

    def stop(self):
        with self._lock:
            server = self._server
            thread = self._thread
            self._server = None
            self._thread = None
            self._app = None
            self._connection = None

        if server is not None:
            server.shutdown()
            server.server_close()
        if thread is not None:
            thread.join(timeout=5)


def _is_loopback(host: str) -> bool:
    if host == 'localhost':
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False
