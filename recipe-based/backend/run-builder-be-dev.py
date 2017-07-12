#!/usr/bin/env python
from backend import run_backend
from config import DebugConfiguration as config
import logging


if __name__ == "__main__":
    logging.basicConfig(
        level=config.LOGLEVEL,
        format='%(asctime)s - %(levelname)s - %(message)s')
    logging.info('Logger initialised')
    logging.debug('Config: %r', config)

    run_backend(config, logging)
