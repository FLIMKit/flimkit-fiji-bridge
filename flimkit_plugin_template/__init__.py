from flimkit.plugins import plugin_config, tool

FLIMKIT_PLUGIN_API = 1

PLUGIN_NAME = 'plugin_template'


@tool(id='plugin_template_hello', label='Template Hello...', menu='Tools', order=500)
def open_hello(app):
    from tkinter import messagebox
    cfg = plugin_config(PLUGIN_NAME)
    opened = int(cfg.get('times_opened', 0) or 0) + 1
    cfg.set('times_opened', opened)
    cfg.save()
    messagebox.showinfo(
        'Template Hello',
        f'This window was opened by an add-on, not by FLIMKit.\n\n'
        f'FLIMKit handed it the live {type(app).__name__}, whose window is titled '
        f'{app.root.title()!r}.\n\n'
        f'Opened {opened} time(s), counted in plugin:{PLUGIN_NAME} of '
        f'~/.flimkit/config.json.')
