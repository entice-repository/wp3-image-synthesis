ENTICE-WP3-ImageSynthesis Backend
=================================


Configuring the application
---------------------------
The application needs a working configuration file names `config.py` in the directory where the start scripts (`run-builder-*.py`)  are. Use the sample configuration file (`config-sample.py`) to create one.

In the configuration file you can specify several configurations (e.g., `DebugConfiguration`, `TestConfiguration` and `LiveConfiguration`). The `LiveConfiguration` supports reading configuration data from a `.json` file.

In the start scripts (`run-builder-*.py`) you can modify which configuration it should use.


Running tests
-------------

Install pytest and execute `python -m pytest tests/` in frontend and backend directories.