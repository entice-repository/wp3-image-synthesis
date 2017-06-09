import os
import sys
import json
import yaml
import logging

_basedir = os.path.abspath(os.path.dirname(__file__))

class BaseConfiguration(object):
    """
    Base configuration. All others are derived from here.
    """
    # Debugging enabled (disable for production site).
    DEBUG = False
    # Testing enabled (disable for production site).
    TESTING = False
    # Root folder for storing incoming build requests. Must be the same as for the frontend.
    DATADIR = "/tmp/entice-builder/datadir"
    # Folder for the builder module (working directory). Must be the same as for the frontend.
    SCRIPTDIR = "/tmp/entice-builder/scriptdir"
    # logging information.
    LOGLEVEL = logging.INFO
    LOGNAME = "entice-ib-backend"
    # Sleep interval between.
    SCANWAITINTERVAL = 5
    # Maximum number of parallel running jobs.
    MAX_RUNNING_JOBS = 4

class DebugConfiguration(BaseConfiguration):
    """
    Configuration for debugging the application.
    """
    DEBUG = True
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


class LiveConfiguration(BaseConfiguration):
    """
    Live configuration.

    Tries to load from a .json configuration file. If it is not found then values defined
    in BaseConfiguration are used. This is to separate live configuration values from this
    file. Most of the time setting values in BaseConfiguration instead of here should be
    fine.
    """
    sys.stderr.write("Loading config from JSON file.\n")
    try:
        config_file = "/etc/entice/imagesynthesis-backend.json"
        with open(config_file, 'r') as content_file:
            content = content_file.read()
            config_json = json.loads(content)

    except Exception, e:
        sys.stderr.write("WARNING: Could not read configuration from LIVE config file:" + str(e) + "\n")
        pass # DIRRRRRTY HACK!









