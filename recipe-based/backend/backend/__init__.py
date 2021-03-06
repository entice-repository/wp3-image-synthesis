import logging
import glob
import signal
import os
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
        start_build_process(fullreqdir)


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
    zipfilepath = os.path.join(builddir, "build.zip")
    log.debug("Extracting zip file: " + zipfilepath)
    zip_ref = zipfile.ZipFile(zipfilepath, 'r')
    zip_ref.extractall(sandboxdir)
    zip_ref.close()


def start_build_process(fullreqdir):
    builddir = os.path.join(fullreqdir, "build")
    sandboxdir = os.path.join(builddir, "sandbox")
    module = read_content(os.path.join(builddir, "module"))
    version = read_content(os.path.join(builddir, "version"))
    exepath = os.path.join(config.SCRIPTDIR, module + "-" + version)
    command = "nohup " + exepath + " >../" + FILE_BUILD_STDOUT + " 2>../" + FILE_BUILD_STDERR + \
        "; echo $?>../" + FILE_BUILD_RETCODE + " &"
    log.debug("Executing command: %s", command)
    process = subprocess.Popen(command, cwd=sandboxdir, shell=True)
    log.debug("PID: %s", str(process.pid))
    save_content(os.path.join(builddir, FILE_BUILD_PID), str(process.pid))
