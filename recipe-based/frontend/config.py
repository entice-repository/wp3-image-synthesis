import os
import sys
import json
_basedir = os.path.abspath(os.path.dirname(__file__))

class BaseConfiguration(object):
    DEBUG = False
    TESTING = False
    APP_PORT = 5000
    SECURITY_PASSWORD_HASH = ""
    SECURITY_PASSWORD_SALT = ""
    SECRET_KEY = ""
    DATADIR = "/tmp/entice-builder/datadir"
    ENDPOINT = "https://entice.lpds.sztaki.hu:5443"
    WSPATH = "/api/imagebuilder/build"
    
class DebugConfiguration(BaseConfiguration):
    DEBUG = True
    SECURITY_PASSWORD_HASH = "pbkdf2_sha512"
    SECURITY_PASSWORD_SALT = "CHANGE_ME"
    SECRET_KEY = "CHANGE_ME"
    APP_PORT = 4000
    DATADIR = "/tmp/datadir"
    ENDPOINT = "http://localhost:4000"

    
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









