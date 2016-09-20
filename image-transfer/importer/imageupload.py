import xml.etree.ElementTree as ET
import myoneclientapi as oneclient
import sys,time
import logging

global log
log = logging.getLogger('image_upload')
log.setLevel(logging.DEBUG)
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
log.addHandler(ch)


SHORT_IMAGE_STATES = ['INIT','READY','USED','DISABLED','LOCKED','ERROR','CLONE','DELETE','USED_PERS']
IMAGE_TEMPLATE = '''
NAME = {0}
PATH = {1}
DESCRIPTION = \"{2}\"
PUBLIC = YES
'''


def extract_subelement_from_xml(root,subelements):
    subroot = None
    for element in root:
        if element.tag == subelements[0]:
            subroot = element
    if subroot is None:
        return None
    if len(subelements)>1:
        return extract_subelement_from_xml(subroot,subelements[1:])
    return subroot

def query_image_status(client,imageid):
    xml = client.image_info(imageid)
    root = ET.fromstring(xml)
    state = int(extract_subelement_from_xml(root,['STATE']).text)
    if SHORT_IMAGE_STATES[state] == 'ERROR':
        message = extract_subelement_from_xml(root,['TEMPLATE','ERROR','MESSAGE']).text
    else:
        message = None
    return state, message

def image_upload_and_wait(client,name,url,description="No description"):
    try:
        template = IMAGE_TEMPLATE.format(name,url,description)
        imageid = client.image_allocate(template)
    except Exception as e:
        return -1, str(e)
    log.debug('Image (id:'+str(imageid)+') upload started, waiting to finish...')
    state, message = query_image_status(client,imageid)
    while SHORT_IMAGE_STATES[state] not in ['READY','ERROR']:
        log.debug('Image (id:'+str(imageid)+') is being uploaded, waiting...')
        time.sleep(1)
        state, message = query_image_status(client,imageid)
    if SHORT_IMAGE_STATES[state] == 'READY':
        log.debug('Image (id:'+str(imageid)+') upload finished successfully!')
        return imageid, ""
    else:
        log.debug('Image (id:'+str(imageid)+') upload failed.')
        log.debug("Error message: "+message)
        client.image_delete(imageid)
        log.debug('Failed image has been removed.')
        return -1, message

'''MAIN'''
log.debug('Parameters: %s',str(sys.argv))
if len(sys.argv) < 3:
    log.info("Parameters: <name> <url> [optional]<desc>")
    log.info("Environment variables:")
    log.info("  ONE_XMLRPC: opennebula RPC API endpoint, default: \"http://localhost:2633/RPC2\"")
    log.info("  ONE_AUTH: opennebula authorisation file, default: \"~/.one/one_auth\"")
    sys.exit(1)
name = sys.argv[1]
url = sys.argv[2]
description = sys.argv[3] if len(sys.argv)>3 else "No description"

client = oneclient.Client()
log.info("Image upload starts.")
id, msg = image_upload_and_wait(client,name,url,description)
if id >=0:
    log.info("Image upload successfully finished. Image id: %i",id)
else:
    log.info("Image upload failed. Error message: %s",msg)


