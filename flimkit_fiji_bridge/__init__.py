from flimkit.plugins import plugin_config, tool

FLIMKIT_PLUGIN_API = 1

PLUGIN_NAME = 'fiji_bridge'


@tool(id='fiji_bridge_open', label='Fiji Bridge...', menu='Tools', order=500)
def open_bridge(app):
    from tkinter import messagebox
    cfg = plugin_config(PLUGIN_NAME)
    opened = int(cfg.get('times_opened', 0) or 0) + 1
    cfg.set('times_opened', opened)
    cfg.save()
    messagebox.showinfo(
        'Fiji Bridge',
        'The Fiji bridge add-on is installed.\n\n'
        'Image and ROI exchange is still under development. See the project '
        'README for the current test instructions.\n\n'
        f'Opened {opened} time(s).',
        parent=app.root,
    )
