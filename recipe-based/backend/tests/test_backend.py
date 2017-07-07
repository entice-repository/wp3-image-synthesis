
def test_extract_request_detail(tmpdir):
   import backend
   request_id = "random"
   workdir = tmpdir.mkdir("test")
   fullreqdir = workdir.mkdir("P_"+request_id)
   retdatadir,retreqdir,retreqid,retstate = backend.extract_request_details(str(fullreqdir))
   assert(retdatadir == str(workdir))
   assert(retreqdir  == 'P_'+request_id)
   assert(retreqid   == request_id)
   assert(retstate   == 'P')
   tmpdir.remove(ignore_errors=True)
   return

def test_change_reqdir_state(tmpdir):
   import backend
   request_id = "random"
   workdir = tmpdir.mkdir("test")
   fullreqdir = workdir.mkdir("P_"+request_id)
   newfullreqdir = backend.change_reqdir_state(str(fullreqdir),"I")
   assert(str(workdir.join("I_"+request_id)) == newfullreqdir)
   assert(workdir.join("I_"+request_id).check())
   tmpdir.remove(ignore_errors=True)
   return

def test_handle_init_requests(tmpdir):
   # TODO
   pass

def test_handle_running_requests(tmpdir):
   # TODO
   pass

def test_prepare_build(tmpdir):
   # TODO
   pass

def test_start_build_process(tmpdir):
   # TODO
   pass

