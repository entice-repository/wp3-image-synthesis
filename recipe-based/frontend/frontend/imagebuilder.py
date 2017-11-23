import os
import sys
import frontend
import uuid
import json
import wget
import glob
import shutil
import urllib2
import contextlib
import base64

request_dir_states = ['P', 'I', 'R', 'F']
request_dir_states_str = {'P': 'prepare',
                          'I': 'init',
                          'R': 'running',
                          'F': 'finished'}

request_outcome = ['S', 'E', 'C', 'U']
request_outcome_str = {'S': 'success',
                       'E': 'error',
                       'C': 'cancelled',
                       'U': 'undefined'}


def read_content(filename):
    with file(filename) as f:
        content = f.read()
    return content


def download_file(url, target_file):
    '''
    Downloads file from given URL and saves it into target_file.

    Target_file may contain a filename or a full path with filename.
    '''
    try:
        #urlopen = urllib2.urlopen(url)
        outfilename = os.path.join(target_file)
        with contextlib.closing(urllib2.urlopen(url)) as urlopen:
            with open(outfilename, 'wb') as output:
                output.write(urlopen.read())
    except urllib2.HTTPError, e:
        log.debug("ERROR: cannot download data from url \"" + url +
                  "\" for file" + target_file + " .")
        raise Exception("Cannot download from " + url)
    except urllib2.URLError, e:
        log.debug("ERROR: invalid url specified \"" + url +
                  "\" for file" + target_file + " .")
        raise Exception("Invalid url specified: \"" + url + "\"")


def deploy_data(request_dir, content, target):
    '''
    This function deploys the requested parts found in either the 'build' or 'test'
    part of the request (it only gets either the 'build' or 'test' part. See
    deploy_request_content().

    Sample complete request can be found in README.md.
    '''
    # Create subdir
    target_dir = os.path.join(request_dir, target)
    os.makedirs(target_dir)
    # Create subdir
    module = content.get(target, dict()).get('module', None)
    if module:
        fd = open(os.path.join(target_dir, "module"), "wb")
        fd.write(str(module))
        fd.close()
    # Create version
    version = content.get(target, dict()).get('version', None)
    if module:
        fd = open(os.path.join(target_dir, "version"), "wb")
        fd.write(str(version))
        fd.close()
    # Save input content
    data = content.get(target, dict()).get(
        'input', dict()).get('zipdata', None)
    if data:
        fd = open(os.path.join(target_dir, target + ".zip"), "wb")
        fd.write(base64.b64decode(data))
        fd.close()
    else:
        url = content.get(target, dict()).get(
            'input', dict()).get('zipurl', None)
        outfilename = os.path.join(target_dir, target + ".zip")
        if url:
            download_file(url, outfilename)
        '''
        try:
            url = content.get(target,dict()).get('input',dict()).get('zipurl',None)
            if url:
                zipfile = urllib2.urlopen(url)
                outfilename = os.path.join(target_dir,target+".zip")
                with open(outfilename,'wb') as output:
                    output.write(zipfile.read())
        except urllib2.HTTPError, e:
            log.debug("ERROR: cannot download data from url \""+url+
                "\" for request"+request_dir+" .")
            raise Exception("Cannot download from "+url)
        except urllib2.URLError, e:
            log.debug("ERROR: invalid url specified \""+url+
                "\" for request"+request_dir+" .")
            raise Exception("Invalid url specified: \""+url+"\"")
        '''
    # Save varfile content (optional, json format assumed)
    # '__varfile__' contains the data of the varfile. If this file is present a varfile is assumed
    # for the command line.
    varfile_filename = "__varfile__"

    varfile = content.get(target, dict()).get('varfile', None)
    if varfile:
        varfile_data = content.get(target, dict()).get(
            'varfile', dict()).get('data', None)
        if varfile_data:
            fd = open(os.path.join(target_dir, varfile_filename), "wb")
            fd.write(json.dumps(varfile_data, indent=4))
            fd.close()
        else:
            # data key not found in varfile, let's look for url
            varfile_url = content.get(target, dict()).get(
                'varfile', dict()).get('url', None)
            outfilename = os.path.join(target_dir, varfile_filename)
            if not varfile_url:
                raise Exception("Neither data nor url is specified for varfile")
            download_file(varfile_url, outfilename)


def deploy_request_content(datadir, request_id, content):
    # Create request dir
    request_dir = os.path.join(datadir, request_dir_states[
                               0] + "_" + request_id)
    os.makedirs(request_dir)
    deploy_data(request_dir, content, 'build')
    deploy_data(request_dir, content, 'test')


def deploy_request_json(datadir, request_id, content):
    request_dir = os.path.join(datadir, request_dir_states[
                               0] + "_" + request_id)
    with open(os.path.join(request_dir, "request.json"), 'wb') as output:
        output.write(str(content))


def set_request_dir_state(datadir, request_id, oldstate, newstate):
    olddir = os.path.join(datadir, oldstate + "_" + request_id)
    newdir = os.path.join(datadir, newstate + "_" + request_id)
    os.rename(olddir, newdir)


def get_state_by_dirname(dirname):
    basedirname = os.path.basename(dirname)
    if basedirname[0] not in request_dir_states:
        raise Exception("Invalide state detected: " + basedirname)
    return basedirname[0]


def get_outcome_by_dirname(dirname):
    if get_state_by_dirname(dirname) not in ['F']:
        return 'U'
    if os.path.isfile(os.path.join(dirname, "build", "cancelled")):
        return 'C'
    if os.path.isfile(os.path.join(dirname, "build", "build.retcode")) and \
            os.path.isfile(os.path.join(dirname, "build", "build.image_url")):
        return 'S'
    return 'E'


def find_dir_by_request_id(datadir, request_id):
    dirs = glob.glob(os.path.join(datadir, "*_" + request_id))
    return None if len(dirs) != 1 else dirs[0]


def collect_image_info(request_id):
    image_info = {}
    endpoint = frontend.app.config.get("ENDPOINT")
    wspath = frontend.app.config.get("WSPATH")
    url = endpoint + wspath + "/"
    image_info['url'] = url + str(request_id) + "/result/image"
    return image_info


def collect_log_info(request_id):
    endpoint = frontend.app.config.get("ENDPOINT")
    wspath = frontend.app.config.get("WSPATH")
    url = endpoint + wspath + "/"
    log_info = url + str(request_id) + "/result/log"
    return log_info


class ImageBuilder(object):

    def __init__(self):
        global log
        log = self.log = frontend.app.logger
        self.datadir = frontend.app.config.get("DATADIR")
        if not os.path.exists(self.datadir):
            os.makedirs(self.datadir)

    def new(self, content):
        try:
            request_id = ""
            contentdict = json.loads(content)
            request_id = str(uuid.uuid4())
            deploy_request_content(self.datadir, request_id, contentdict)
            deploy_request_json(self.datadir, request_id, content)
            set_request_dir_state(datadir=self.datadir,
                                  request_id=request_id,
                                  oldstate=request_dir_states[0],
                                  newstate=request_dir_states[1])
            return True, "", request_id
        except Exception, e:
            if request_id is not "":
                shutil.rmtree(os.path.join(self.datadir, "P_" + request_id))
            return False, "Error: " + str(e), None

    def state(self, request_id):
        try:
            dirname = find_dir_by_request_id(self.datadir, request_id)
            if not dirname:
                return False, "Unknown request ID!", "unknown", request_outcome_str['U']
            state = get_state_by_dirname(dirname)
            state_str = request_dir_states_str[state]
            outcome_str = request_outcome_str[get_outcome_by_dirname(dirname)]
            return True, "", state_str, outcome_str
        except Exception, e:
            return False, "Error: " + str(e), None, request_outcome_str['U']

    def cancel(self, request_id):
        try:
            dirname = find_dir_by_request_id(self.datadir, request_id)
            if not dirname:
                return False, "Unknown request ID!"
            state = get_state_by_dirname(dirname)
            if state not in ['I', 'R']:
                return False, "Cancel operation not allowed in current state (" + request_dir_states_str[state] + ")!"
            fd = open(os.path.join(dirname, "build", "cancel"), "wb")
            fd.write("Cancel request by frontend")
            fd.close()
            fd = open(os.path.join(dirname, "test", "cancel"), "wb")
            fd.write("Cancel request by frontend")
            fd.close()
            return True, ""
        except Exception, e:
            return False, "Error: " + str(e)

    def delete(self, request_id):
        try:
            dirname = find_dir_by_request_id(self.datadir, request_id)
            if not dirname:
                return False, "Unknown request ID!"
            state = get_state_by_dirname(dirname)
            if state not in ['F']:
                return False, "Delete operation not allowed in current state (" + request_dir_states_str[state] + ")!"
            shutil.rmtree(dirname, ignore_errors=True, onerror=None)
            return True, ""
        except Exception, e:
            return False, "Error: " + str(e)

    def result(self, request_id):
        try:
            dirname = find_dir_by_request_id(self.datadir, request_id)
            if not dirname:
                return False, "Unknown request ID!", None
            state = get_state_by_dirname(dirname)
            if state not in ['F']:
                return False, "Get result operation not allowed in current state (" + \
                    request_dir_states_str[state] + ")!", None
            '''
            TODO: Handle CANCELLED request
            '''
            image_info = collect_image_info(request_id)
            log_info = collect_log_info(request_id)
            result = {'request_id': request_id,
                      'image': image_info,
                      'log_url': log_info}
            return True, "", result
        except Exception, e:
            return False, "Error: " + str(e), None

    def getImagePath(self, request_id):
        reqdir = find_dir_by_request_id(self.datadir, request_id)
        if not reqdir:
            return None, None
        builddir = os.path.join(reqdir, "build")
        '''
        TODO: handle non-finished request!
        '''
        imgsubpath = read_content(os.path.join(
            builddir, "build.image_url")).strip()
        imgpath = os.path.join(builddir, "sandbox", imgsubpath)
        imgfile = os.path.basename(imgpath)
        imgdir = os.path.dirname(imgpath)
        return imgdir, imgfile

    def getLogPath(self, request_id):
        reqdir = find_dir_by_request_id(self.datadir, request_id)
        if not reqdir:
            return None, None
        builddir = os.path.join(reqdir, "build")
        logpath = os.path.join(builddir, "build.stderr")
        logfile = os.path.basename(logpath)
        logdir = os.path.dirname(logpath)
        return logdir, logfile
