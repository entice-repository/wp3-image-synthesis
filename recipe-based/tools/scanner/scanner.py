#!/usr/bin/env python

import re
import os
import zipfile
import argparse
import shutil
import subprocess

regexes = {
    "Slack Token": re.compile('(xox[p|b|o|a]-[0-9]{12}-[0-9]{12}-[0-9]{12}-[a-z0-9]{32})'),
    "RSA private key": re.compile('-----BEGIN RSA PRIVATE KEY-----'),
    "SSH (OPENSSH) private key": re.compile('-----BEGIN OPENSSH PRIVATE KEY-----'),
    "SSH (DSA) private key": re.compile('-----BEGIN DSA PRIVATE KEY-----'),
    "SSH (EC) private key": re.compile('-----BEGIN EC PRIVATE KEY-----'),
    "PGP private key block": re.compile('-----BEGIN PGP PRIVATE KEY BLOCK-----'),
    "Facebook Oauth": re.compile('[f|F][a|A][c|C][e|E][b|B][o|O][o|O][k|K].*[\'|"][0-9a-f]{32}[\'|"]'),
    "Twitter Oauth": re.compile('[t|T][w|W][i|I][t|T][t|T][e|E][r|R].*[\'|"][0-9a-zA-Z]{35,44}[\'|"]'),
    "GitHub": re.compile('[g|G][i|I][t|T][h|H][u|U][b|B].*[[\'|"]0-9a-zA-Z]{35,40}[\'|"]'),
    "Google Oauth": re.compile('("client_secret":"[a-zA-Z0-9-_]{24}")'),
    "AWS API Key": re.compile('AKIA[0-9A-Z]{16}'),
    "Heroku API Key": re.compile('[h|H][e|E][r|R][o|O][k|K][u|U].*[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}'),
    "Generic Secret": re.compile('[s|S][e|E][c|C][r|R][e|E][t|T].*[\'|"][0-9a-zA-Z]{32,45}[\'|"]'),
    "Password": re.compile('[p|P][a|A][s|S][w|W][o|O][r|R][d|D]'),
}

WORKDIR = "./workdir-scanner"
MAX_SIZE = 16000

class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def regex_check(path, custom_regexes={}):
    if custom_regexes:
        secret_regexes = custom_regexes
    else:
        secret_regexes = regexes
    regex_matches = []
    with open(path, 'r') as f:
        data = f.read()
        for key in secret_regexes:
            found_strings = secret_regexes[key].findall(data)
            for found_string in found_strings:
                found_diff = data.replace(data, bcolors.WARNING + 
                    found_string + bcolors.ENDC)
            if found_strings:
                foundRegex = {}
                foundRegex['path'] = path 
                foundRegex['stringsFound'] = found_strings
                foundRegex['reason'] = key
                regex_matches.append(foundRegex)
    return regex_matches


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Scan recipe bundles, directories for sensitive data.')
    parser.add_argument('use_path', type=str, 
        help='Directory path or recipe bundle for scanning for sensitive data')
    parser.add_argument("--use-rs", dest="use_rs", action="store_true", 
        help="Use repo-supervisor on top of regular expression based search")
    parser.add_argument("--directory", dest="use_directory", 
        action="store_true", 
        help="Assume recipe bundle path is a directory and not a zip file")
    parser.add_argument("--remove", dest="remove_items", 
        action="store_true", 
        help="Remove found files without asking, assumes --directory option")
    parser.add_argument("--filter", dest="filter_items", 
        help="Items to be filtered from reporting/ removal")
    parser.set_defaults(use_rs=False)
    parser.set_defaults(use_directory=False)
    parser.set_defaults(filter_items="")
    args = parser.parse_args()
    search_dir = ""
    filter_items = []
    # Extract zip to work folder
    if not args.use_directory:
        zip_ref = zipfile.ZipFile(args.use_path, 'r')
        zip_ref.extractall(WORKDIR)
        zip_ref.close()
        search_dir = WORKDIR
    else:
        search_dir = args.use_path
    # Parse and store filter items
    if args.filter_items:
        filter_items = args.filter_items.split(",")
    # Search and parse files
    print("Regular expression based search output:\n")
    for root, dirs, files in os.walk(search_dir):
        path = root.split(os.sep)
        foundIssues = []
        for file in files:
            full_path = os.path.join(root, file)
            if os.path.getsize(full_path) < MAX_SIZE:
                prefix = search_dir + os.sep
                path_check = full_path
                if path_check.startswith(prefix):
                    path_check = path_check[len(prefix):]
                if not path_check in filter_items:
                    found_regexes = regex_check(full_path)
                    foundIssues += found_regexes
    if foundIssues:
        for foundIssue in foundIssues:
            print("{}".format(foundIssue))
    else:
        print("{}")
    # Execute repo-supervisor-run.sh in work folder
    if args.use_rs:
        output = subprocess.check_output(['./repo-supervisor-run.sh', 
            search_dir])
        print("\nRepo-supervisor output:\n")
        print(output)
    # Remove found items if requested
    if args.remove_items and args.use_directory:
        for foundIssue in foundIssues:
            print("Removing: {}".format(foundIssue['path']))
            try:
                os.remove(foundIssue['path'])
            except OSError:
                pass
    # Clean up
    if not args.use_directory:
        shutil.rmtree(WORKDIR)

