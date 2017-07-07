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