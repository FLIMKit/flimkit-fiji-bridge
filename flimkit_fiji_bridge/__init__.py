from flimkit.plugins import plugin_config, tool

FLIMKIT_PLUGIN_API = 1

PLUGIN_NAME = 'fiji_bridge'


@tool(id='fiji_bridge_demo', label='Fiji Bridge Demo...', menu='Tools', order=500)
def open_demo(app):
    from tkinter import messagebox
    cfg = plugin_config(PLUGIN_NAME)
    opened = int(cfg.get('times_opened', 0) or 0) + 1
    cfg.set('times_opened', opened)
    cfg.save()
    messagebox.showinfo(
        'Fiji Bridge Demo',
        'The direct communication demo is installed.\n\n'
        'The interactive Fiji bridge is not implemented yet. See the project '
        'README for the verified command-line demo.\n\n'
        f'Opened {opened} time(s).',
        parent=app.root,
    )
