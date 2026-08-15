from flimkit.plugins import tool

from .flimkit_adapter import FlimkitCompatibilityError
from .runtime import BridgeRuntime


FLIMKIT_PLUGIN_API = 1

PLUGIN_NAME = 'fiji_bridge'
_RUNTIME = BridgeRuntime()


@tool(id='fiji_bridge_open', label='Fiji Bridge...', menu='Tools', order=500)
def open_bridge(app):
    from tkinter import messagebox

    try:
        connection = _RUNTIME.start(app)
    except FlimkitCompatibilityError as error:
        messagebox.showerror(
            'Fiji Bridge',
            str(error),
            parent=app.root,
        )
        return
    except Exception as error:
        messagebox.showerror(
            'Fiji Bridge',
            f'The Fiji bridge could not start: {error}',
            parent=app.root,
        )
        return

    messagebox.showinfo(
        'Fiji Bridge',
        'The Fiji bridge is running.\n\n'
        f'Address: {connection.base_url}\n'
        f'Pairing token: {connection.token}\n\n'
        'Keep FLIMKit open while Fiji is connected. Opening this tool again '
        'shows the same connection details.',
        parent=app.root,
    )
