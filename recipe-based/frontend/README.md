ENTICE-WP3-ImageSynthesis Frontend
==================================

Installing
----------

### Linux ###

Make sure you have installed the following devel libraries:
```
sudo apt-get install python-dev libffi-dev libssl-dev
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

Running tests
-------------

Install pytest and execute `python -m pytest tests/` in frontend and backend directories.


Sample input
------------

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
        varfile { // optional
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

TODO
----

* Testing is not implemented.