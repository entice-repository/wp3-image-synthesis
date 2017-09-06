import pytest


def test_config(flask_app):
    config, client = flask_app
    assert config.WSPATH != ''
    assert config.DATADIR != ''
    assert config.ENDPOINT != ''


@pytest.mark.parametrize("test_input, test_method", [
    ('', 'post'),
    ('/1', 'get'),
    ('/1', 'delete'),
    ('/1/result', 'get'),
    ('/1/result', 'delete'),
    ('/1/result/image', 'get'),
    ('/1/result/log', 'get')
])
def test_api_nothing_uploaded(flask_app, test_input, test_method):
    import json

    config, client = flask_app
    response = getattr(client, test_method)(config.WSPATH + test_input)
    data = json.loads(response.get_data())
    assert data
    assert "status" in data
    assert data["status"] == "failed"
