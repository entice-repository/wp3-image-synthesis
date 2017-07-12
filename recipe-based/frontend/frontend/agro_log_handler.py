import os
import sys
import logging
import logging.handlers


class AgroLogHandler:
    """ Log handler class based on Python's logging module."""

    def __init__(self, app):
        self.app = app

    def set_level(self, log_level):
        """ Set logging level."""
        LEVELS = {'DEBUG': logging.DEBUG,
                  'INFO': logging.INFO,
                  'WARNING': logging.WARNING,
                  'ERROR': logging.ERROR,
                  'CRITICAL': logging.CRITICAL}
        level = LEVELS.get(log_level, logging.NOTSET)
        self.logger.setLevel(level)

    def init(self):
        self.logger = logging.getLogger(self.app.name)
        #self.handler = logging.handlers.RotatingFileHandler('application.log', maxBytes=100000, backupCount=10)
        self.handler = logging.StreamHandler()
        self.format = "%(asctime)s | %(pathname)s:%(lineno)d | %(funcName)s | %(levelname)s | %(message)s "
        self.formatter = logging.Formatter(self.format)
        self.handler.setFormatter(self.formatter)
        self.set_level('DEBUG')
        return self.app.logger.addHandler(self.handler)
