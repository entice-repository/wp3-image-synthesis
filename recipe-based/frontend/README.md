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

NOTE: During the first run the playbook may fail, as the application needs a working configuration file in the shared/ directory (created by ansistrano). If this is the case, create a config file (see section `Configuring the application`), and rerun the deployment.


Configuring the application
---------------------------
The application needs a working configuration file names `config.py` in the directory where the start scripts (`run-builder-*.py`)  are. Use the sample configuration file (`config-sample.py`) to create one.

In the configuration file you can specify several configurations (e.g., `DebugConfiguration`, `TestConfiguration` and `LiveConfiguration`). The `LiveConfiguration` supports reading configuration data from a `.json` file.

In the start scripts (`run-builder-*.py`) you can modify which configuration it should use. Recommended to use the run-\*-dev.py for development and run-\*-prod.py for production runs.

See the sample configuration file for description of the configuration items.


Running tests
-------------

Install pytest and execute `python -m pytest tests/` in frontend and backend directories.


Input JSON format
-----------------

```
{
    "build": {
        "description": "description",    // give a description for your build
        "tags": ["tag1", "tag2", "..."], // attach tags to annotate your build
        "module": "packer",
        "version": "1.1", // Either 1.0 or 1.1, 1.1 recommended
        "input": {
            "zipdata": ..., // Content of packer data .zip base64 encoded
            // OR
            "zipurl": ... // URL containing packer data .zip
            optional: [ // additional build steps
                {
                    "description": "description",
                    "tags": ["tag1", "tag2", "..."], // Tags for the build component
                    "zipdata": ..., // Content of additional packer data .zip base64 
                                    //     encoded
                                    // OR
                    "zipurl": ...   // URL containing packer data .zip
                }, ... // up to 99
            ],
            optimization: { // Script from a previous optimization, this will be run 
                            //     after all inputs.
                data : ..., // script base64 encoded.
                // OR
                url: ... // URL containing the script.
            }
        },
        // Optional variables file. If specified it is automatically used and packer will
        // see it as a file named '__varfile__'.
        varfile {
            data : ..., // Variables file base64 encoded.
            // OR
            url: ... // URL containing the variables file.
        }
    },
    "test": {
        "module": "exec-internal", // Test will be run on the VM as the last build step.
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
