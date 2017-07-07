from __future__ import unicode_literals
from distutils import dir_util
import pytest
import os


@pytest.fixture
def datadir(tmpdir, request):
    '''
    Fixture responsible for searching a folder with the same name of test
    module and, if available, moving all contents to a temporary directory so
    tests can use them freely.
    '''
    filename = request.module.__file__
    test_dir, _ = os.path.splitext(filename)

    if os.path.isdir(test_dir):
        dir_util.copy_tree(test_dir, bytes(tmpdir))

    return tmpdir


@pytest.fixture(scope="session")
def flask_app():
    from frontend import app, init_application
    from config import TestConfiguration as config

    init_application(app, config)
    client = app.test_client()
    client.testing = True
    return config, client
