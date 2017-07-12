#!/usr/bin/env python
from frontend import app, init_application
from config import DebugConfiguration as config

if __name__ == "__main__":
    init_application(app, config)
    app.debug = config.DEBUG
    app.run(host='0.0.0.0', port=config.APP_PORT, threaded=True)
