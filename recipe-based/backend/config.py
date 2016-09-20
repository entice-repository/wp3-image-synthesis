import os
import sys
import json
import yaml
import logging

_basedir = os.path.abspath(os.path.dirname(__file__))

class BaseConfiguration(object):
    DEBUG = False
    TESTING = False
    APP_PORT = 5000
    SECURITY_PASSWORD_HASH = ""
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    DATADIR = "/tmp/entice-builder/datadir"
    SCRIPTDIR = "/tmp/entice-builder/scriptdir"
    LOGLEVEL = logging.INFO
    LOGNAME = "entice-ib-backend"
    SCANWAITINTERVAL = 5
    MAX_RUNNING_JOBS = 4
    
class DebugConfiguration(BaseConfiguration):
    DEBUG = True
    APP_PORT = 4000
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
    TESTING = True
    
class LiveConfiguration(BaseConfiguration):
    sys.stderr.write("Loading config from JSON file.\n")
    try:
        config_file = "/etc/entice/imagesynthesis-frontend.json"
        with open(config_file, 'r') as content_file:
            content = content_file.read()
            config_json = json.loads(content)
        SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
        SECURITY_PASSWORD_SALT = config_json["imagesynthesis-frontend"].get("password_salt")
        SECRET_KEY = config_json["imagesynthesis-frontend"].get("secret_key")
        
    except Exception, e:
        sys.stderr.write("WARNING: Could not read configuration from LIVE config file:" + str(e) + "\n")
        pass # DIRRRRRTY HACK!









