ENTICE-WP3-ImageSynthesis Frontend
==================================

Installing
----------

### Linux ###

Make sure you have installed the following devel libraries:
```
sudo apt-get install python-dev libffi-dev libssl-dev
```

Always upgrade pip to the latest version:
```
pip install --upgrade pip
```

If pip hangs with `caching b/c date exists and max-age > 0` use the following:
```
pip ... --no-cache-dir ...
```

### OS X ###

Install openssl via homebrew:

```
brew install openssl
```

Set the following environment variables before running `pip install -r requirements.txt`:

```bash
export EXTRA_CFLAGS="-I/usr/local/opt/openssl/include"
export EXTRA_LDFLAGS="-L/usr/local/opt/openssl/"
export EXTRA_CXXFLAGS="-I/usr/local/opt/openssl/include"
```

Deployment
----------
Use the Ansible playbook in `../deployment`.


Configuring the application
---------------------------
The application needs a working configuration file names `config.py` in the directory where the start scripts (`run-builder-*.py`)  are. Use the sample configuration file (`config-sample.py`) to create one.

In the configuration file you can specify several configurations (e.g., `DebugConfiguration`, `TestConfiguration` and `LiveConfiguration`). The `LiveConfiguration` supports reading configuration data from a `.json` file.

In the start scripts (`run-builder-*.py`) you can modify which configuration it should use.


Running tests
-------------

Install pytest and execute `python -m pytest tests/` in frontend and backend directories.


Input JSON format
-----------------

```
{
    "build": {
        "module": "packer",
        "version": "1.0",
        "input": {
            "zipdata": ..., // content of packer data .zip base64 encoded
            // OR
            "zipurl": ... // url containing packer data .zip
        },
        // Optional variables file. If specified it is automatically used and packer will
        // see it as a file named '__varfile__'.
        varfile {
            data : ..., // varfile base64_encoded
            // OR
            url: ... // url containing the varfile
        }
    },
    "test": {
        "module": "exec-internal",
        "version": "1.0",
        "input": {
            "command" : "run_test.sh",
            "zipdata": ..., // content of packer test .zip
            // OR
            "zipurl": ... // url containing packer test .zip
        }
    }
}
```

Sample submission
-----------------

See testing directory for samples.


TODO
----

* Test module is not fully implemented.