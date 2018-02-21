from __future__ import print_function
import os
import sys
import json
import random
import string


def random_string(N):
    ''' TODO: Move this somewhere else '''
    return ''.join(random.SystemRandom().choice(
        string.ascii_uppercase + string.digits) for _ in range(N))


_basedir = os.path.abspath(os.path.dirname(__file__))
_random_string = random_string(5)


class BaseConfiguration(object):
    """
    Base configuration. All others are derived from here.
    """
    # Debugging enabled (disable for production site).
    DEBUG = False
    # Testing enabled (disable for production site).
    TESTING = False
    # Application port (HTTP)
    APP_PORT = 5000
    # SECURITY_* is for Flask-Security and is currently not used by the
    # applicaton.
    SECURITY_PASSWORD_HASH = ""
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    # Root folder for storing incoming build requests. Must be the same as for
    # the frontend.
    DATADIR = "/tmp/entice-builder/datadir"
    # Protocol+host+port part of the public address of the service. This is
    # used for returning the URL of the finished image for download
    ENDPOINT = "https://localhost:5000"
    # Prefix for all webservice endpoints
    WSPATH = "/api/imagebuilder/build"
    # TODO: component storage default path prefix
    COMPONENT_STORAGE_URL="http://entice.lpds.sztaki.hu/synth/"


class DebugConfiguration(BaseConfiguration):
    """
    Configuration for debugging the application.
    """
    DEBUG = True
    SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
    SECURITY_PASSWORD_SALT = "CHANGE_ME"
    SECRET_KEY = "CHANGE_ME"
    APP_PORT = 4000
    DATADIR = "/tmp/datadir"
    ENDPOINT = "http://localhost:4000"


class TestConfiguration(BaseConfiguration):
    TESTING = True
    SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    APP_PORT = 4000
    DATADIR = "/tmp/entice-builder-testing-" + \
        _random_string + "/datadir"
    ENDPOINT = "http://localhost:4000"


class LiveConfiguration(BaseConfiguration):
    """
    Live configuration.
    Tries to load from .json configuration file. If not found values defined
    in BaseConfiguration are used. This is to separate live configuration
    values from this file. Most of the time setting values in
    BaseConfiguration instead of here is fine.
    """
    print("Loading config from JSON file.\n", file=sys.stderr)
    try:
        config_file = "/etc/entice/imagesynthesis-frontend.json"
        with open(config_file, 'r') as content_file:
            content = content_file.read()
            config_json = json.loads(content)
        SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
        SECURITY_PASSWORD_SALT = config_json[
            "imagesynthesis-frontend"].get("password_salt")
        SECRET_KEY = config_json["imagesynthesis-frontend"].get("secret_key")
        ENDPOINT = config_json["imagesynthesis-frontend"].get("endpoint")

    except Exception as e:
        print('WARNING: Could not read configuration from LIVE config file: {}\n'.format(e),
               file=sys.stderr)
        pass  # DIRRRRRTY HACK!
