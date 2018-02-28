Scan recipes for sensitive data
===============================

Uses regexpes and entropy to search for sensitive data (passwords, secrets, misconfigurations).

### Prerequisites ###
1. Install repo-supervisor from https://github.com/auth0/repo-supervisor to ./repo-supervisor to
   use entropy based search: 

    `git clone https://github.com/auth0/repo-supervisor.git`
    `npm install --no-optional`
    `npm run build`

### Usage ###

Run `scanner.py` -h to get help and available command line parameters.
