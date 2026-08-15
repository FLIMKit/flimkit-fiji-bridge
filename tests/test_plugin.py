from importlib.metadata import entry_points

import flimkit_plugin_template
from flimkit import plugins


def test_api_version_matches():
    assert flimkit_plugin_template.FLIMKIT_PLUGIN_API == plugins.API_VERSION


def test_tool_is_registered():
    found = plugins.get_tool('plugin_template_hello')
    assert found is not None
    assert found.menu_path == ('Tools',)
    assert callable(found.callback)


def test_entry_point_is_declared():
    names = [e.name for e in entry_points(group='flimkit.plugins')]
    assert 'plugin_template' in names
