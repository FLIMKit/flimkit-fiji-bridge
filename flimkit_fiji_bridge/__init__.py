from flimkit_bridge import discovery


FLIMKIT_PLUGIN_API = 1

PLUGIN_NAME = 'fiji_bridge'


def connection():
    return discovery.read_live()
