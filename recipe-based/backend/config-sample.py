from __future__ import print_function
import os
import sys
import json
import yaml
import logging
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
    # Root folder for storing incoming build requests. Must be the same as for
    # the frontend.
    DATADIR = "/tmp/entice-builder/datadir"
    # Folder for the builder module (working directory).
    SCRIPTDIR = "/tmp/entice-builder/scriptdir"
    # Security configuration (not used here).
    SECURITY_PASSWORD_HASH = ""
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    # logging information.
    LOGLEVEL = logging.INFO
    LOGNAME = "entice-ib-backend"
    # Sleep interval between two scans.
    SCANWAITINTERVAL = 5
    # Maximum number of parallel running jobs.
    MAX_RUNNING_JOBS = 4


class DebugConfiguration(BaseConfiguration):
    """
    Configuration for debugging the application.
    """
    DEBUG = True
    # Security configuration (not used here).
    SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
    SECURITY_PASSWORD_SALT = "CHANGE_ME"
    SECRET_KEY = "CHANGE_ME"
    LOGLEVEL = logging.DEBUG
    DATADIR = "/tmp/datadir"
    SCRIPTDIR = "/tmp/scriptdir"
    LOGNAME = "entice-ib-backend"
    SCANWAITINTERVAL = 5
    MAX_RUNNING_JOBS = 2


class TestConfiguration(BaseConfiguration):
    """
    Configuration for testing environment.
    """
    TESTING = True
    # Configuration for Flask-Security (not used yet).
    SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    LOGLEVEL = logging.DEBUG
    DATADIR = "/tmp/entice-builder-backend-testing-" + \
        _random_string + "/datadir"
    SCRIPTDIR = "/tmp/entice-builder-backend-testing-" + \
        _random_string + "/scriptdir"
    LOGNAME = "entice-ib-backend"
    SCANWAITINTERVAL = 5
    MAX_RUNNING_JOBS = 2

class LiveConfiguration(BaseConfiguration):
    """
    Live configuration.

    Tries to load from a .json configuration file. If it is not found then
    values defined in BaseConfiguration are used. This is to separate live
    configuration values from this file. Most of the time setting values in
    BaseConfiguration instead of here should be fine.
    """
    print("Loading config from JSON file.\n", file=sys.stderr)
    try:
        config_file = "/etc/entice/imagesynthesis-frontend.json"
        with open(config_file, 'r') as content_file:
            content = content_file.read()
            config_json = json.loads(content)
        # Use the following to read security information in a live environment:
        #
        # SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
        # SECURITY_PASSWORD_SALT = config_json["imagesynthesis-frontend"] \
        #   .get("password_salt")
        # SECRET_KEY = config_json["imagesynthesis-frontend"].get("secret_key")
    except Exception as e:
        print('WARNING: Could not read configuration' \
              ' from LIVE config file: {}\n'.format(e), file=sys.stderr)
        pass  # DIRRRRRTY HACK!
