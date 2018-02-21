import logging
import glob
import signal
import os
import shutil
import time
import subprocess
import errno
import zipfile
import psutil

FILE_BUILD_PID = "build.pid"
FILE_BUILD_STDERR = "build.stderr"
FILE_BUILD_STDOUT = "build.stdout"
FILE_BUILD_RETCODE = "build.retcode"


def change_reqdir_state(fullreqdir, statestr):
    datadir, reqdir, reqid, state = extract_request_details(fullreqdir)
    newreqdirname = os.path.join(datadir, statestr + "_" + reqid)
    os.rename(fullreqdir, newreqdirname)
    return newreqdirname


def extract_request_details(fullreqdir):
    datadir = os.path.dirname(fullreqdir)
    reqdir = os.path.basename(fullreqdir)
    reqid = reqdir[2:]
    state = reqdir[0:1]
    return datadir, reqdir, reqid, state


def save_content(filename, content):
    fd = open(filename, "w")
    fd.write(content)
    fd.close()


def read_content(filename):
    with file(filename) as f:
        content = f.read()
    return content


def run_backend(lconfig, llog):
    """
    """
    global config, log
    config = lconfig
    log = llog

    log.info("Entice image builder backend started.")
    log.info("Datadir: %s", config.DATADIR)
    log.debug("Max running jobs: %s", config.MAX_RUNNING_JOBS)

    while(1):
        log.debug("Looking for RUNNING requests:")
        reqdirs = find_requests(config.DATADIR, "R")
        for fullreqdir in reqdirs:
            log.debug("Request: %s", fullreqdir)
            handle_running_requests(fullreqdir)

        num_of_run_requests = len(find_requests(config.DATADIR, "R"))
        log.debug("Number of running requests after handling: %s",
                  num_of_run_requests)
        if num_of_run_requests < config.MAX_RUNNING_JOBS:
            log.debug("Looking for INIT requests:")
            reqdirs = find_requests(config.DATADIR, "I")
            for rd in reqdirs[0:config.MAX_RUNNING_JOBS - num_of_run_requests]:
                log.debug("Request: %s", rd)
                handle_init_requests(rd)

        log.debug("Waiting %i seconds...", config.SCANWAITINTERVAL)
        time.sleep(config.SCANWAITINTERVAL)

    log.info("Entice image bauilder backend exits.")


def find_requests(datadir, typestr):
    dirs = glob.glob(os.path.join(datadir, typestr + "_*"))
    return dirs


def handle_init_requests(fullreqdir):
    datadir, reqdir, reqid, state = extract_request_details(fullreqdir)
    log.info("Handling REQUEST id: %s", reqid)
    if not os.path.exists(os.path.join(fullreqdir, FILE_BUILD_PID)):
        fullreqdir = change_reqdir_state(fullreqdir, "R")
        prepare_build(fullreqdir)
        fullreqdir = start_build_process(fullreqdir)


def handle_running_requests(fullreqdir):
    datadir, reqdir, reqid, state = extract_request_details(fullreqdir)
    if os.path.exists(os.path.join(fullreqdir, "build", "cancel")) and \
            not os.path.exists(os.path.join(fullreqdir, "build", "cancelled")):
        pid = read_content(os.path.join(fullreqdir, "build", FILE_BUILD_PID))
        log.debug("Looking for childrens of process %s", pid)
        proc = psutil.Process(int(pid))
        children = proc.children(recursive=True)
        content = "Child processes killed:\n"
        for child in children:
            content = content + str(child.pid) + "\n"
            os.kill(child.pid, signal.SIGTERM)
        log.debug(content)
        save_content(os.path.join(fullreqdir, "build", "cancelled"), content)
    if not os.path.exists(os.path.join(fullreqdir, "build", FILE_BUILD_RETCODE)):
        #log.debug("REQUEST \""+str(reqid)+"\" still running build")
        return
    log.info("REQUEST \"" + str(reqid) + "\" finished building.")
    fullreqdir = change_reqdir_state(fullreqdir, "F")


def prepare_build(fullreqdir):
    builddir = os.path.join(fullreqdir, "build")
    sandboxdir = os.path.join(builddir, "sandbox")
    log.debug("Preparation in: " + sandboxdir)
    os.makedirs(sandboxdir)
    # Extract main build zip
    zipfilepath = os.path.join(builddir, "build.zip")
    log.debug("Extracting zip file: " + zipfilepath)
    zip_ref = zipfile.ZipFile(zipfilepath, 'r')
    zip_ref.extractall(sandboxdir)
    zip_ref.close()
    # Extract and move components (if present), max 99 assumed
    for i in xrange(99):
        component_zipfilepath = os.path.join(builddir, 
            "__component-{}.zip".format(i))
        if os.path.isfile(component_zipfilepath):
            log.debug("Extracting component {}".format(i))
            zip_ref = zipfile.ZipFile(component_zipfilepath, 'r')
            zip_ref.extractall(sandboxdir)
            zip_ref.close()
            component_filepath = os.path.join(sandboxdir, "provision.json")
            component_filepath_rename = os.path.join(sandboxdir, 
                "provision-{}.json".format(i))
            if os.path.isfile(component_filepath):
                os.rename(component_filepath, component_filepath_rename)
            else:
                log.debug("provision.json not found in zip for component {}".
                    format(i))
                continue
        else:
            log.debug("No __component-{}.zip found in {}".format(i, builddir))
            break
    # Copy optimization script to sandbox directory
    optimization_path = os.path.join(builddir, "__optimization.sh")
    if os.path.isfile(optimization_path):
        optimization_destination_path = os.path.join(sandboxdir, 
            "__optimization.sh")
        log.debug("Copying {} to {}".format(optimization_path, 
            optimization_destination_path))
        shutil.copyfile(optimization_path, optimization_destination_path)

def start_build_process(fullreqdir):
    builddir = os.path.join(fullreqdir, "build")
    sandboxdir = os.path.join(builddir, "sandbox")
    module = read_content(os.path.join(builddir, "module"))
    version = read_content(os.path.join(builddir, "version"))
    modulename = module + "-" + version
    exepath = os.path.join(config.SCRIPTDIR, modulename)
    # Make sure module is deployed to config.SCRIPTDIR and is executable
    if not os.path.isfile(exepath):
        if not os.path.isdir(config.SCRIPTDIR):
            os.mkdir(config.SCRIPTDIR, 0755)
        modulepath = os.path.join("modules", modulename)
        if not os.path.isfile(modulepath):
            message = "Module '" + modulepath + "' not found. Halting build."
            log.error(message)
            fullreqdir = change_reqdir_state(fullreqdir, "F")
            return
        shutil.copy(modulepath, exepath)
    os.chmod(exepath, 0755)
    command = "nohup " + exepath + " >../" + FILE_BUILD_STDOUT + " 2>../" + \
        FILE_BUILD_STDERR + "; echo $?>../" + FILE_BUILD_RETCODE + " &"
    log.debug("Executing command: %s", command)
    process = subprocess.Popen(command, cwd=sandboxdir, shell=True)
    log.debug("PID: %s", str(process.pid))
    save_content(os.path.join(builddir, FILE_BUILD_PID), str(process.pid))
