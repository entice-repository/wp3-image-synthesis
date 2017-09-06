
def test_read_content(flask_app):
    # TODO
    pass


def test_deploy_data(flask_app, datadir, tmpdir):
    from frontend import imagebuilder
    import json

    request_id = "random"
    content = json.loads(datadir.join('submit-test-data.json').read())
    request_dir = tmpdir.mkdir("test").mkdir("P_" + request_id)
    imagebuilder.deploy_data(str(request_dir), content, 'build')
    assert(request_dir.join('build', 'build.zip').check())
    assert(request_dir.join('build', 'module').check())
    assert(request_dir.join('build', 'version').check())
    tmpdir.remove(ignore_errors=True)


def test_deploy_request_content(flask_app, datadir, tmpdir):
    from frontend import imagebuilder
    import json

    request_id = "random"
    content = json.loads(datadir.join('submit-test-data.json').read())
    workdir = tmpdir.join("test")
    imagebuilder.deploy_request_content(str(workdir),
                                        request_id, content)
    assert(workdir.join("P_" + request_id, 'build', 'build.zip').check())
    assert(workdir.join("P_" + request_id, 'build', 'module').check())
    assert(workdir.join("P_" + request_id, 'build', 'version').check())
    assert(workdir.join("P_" + request_id, 'test', 'test.zip').check())
    assert(workdir.join("P_" + request_id, 'test', 'module').check())
    assert(workdir.join("P_" + request_id, 'test', 'version').check())
    tmpdir.remove(ignore_errors=True)


def test_deploy_request_json(flask_app, tmpdir, datadir):
    from frontend import imagebuilder
    import json

    request_id = "random"
    content = json.loads(datadir.join('submit-test-data.json').read())
    workdir = tmpdir.mkdir("test")
    request_dir = workdir.mkdir("P_" + request_id)
    imagebuilder.deploy_request_json(str(workdir),
                                     request_id, content)
    assert(request_dir.join('request.json').check())
    tmpdir.remove(ignore_errors=True)


def test_set_request_dir_state(flask_app, tmpdir):
    from frontend import imagebuilder
    import json

    request_id = "random"
    workdir = tmpdir.mkdir("test")
    request_dir = workdir.mkdir("P_" + request_id)
    imagebuilder.set_request_dir_state(str(workdir), request_id, 'P', 'I')
    assert(workdir.join('I_' + request_id).check())
    tmpdir.remove(ignore_errors=True)


def test_get_state_by_dirname(flask_app, tmpdir):
    from frontend import imagebuilder
    import json

    request_id = "random"
    request_dir = tmpdir.mkdir("test").mkdir("P_" + request_id)
    state = imagebuilder.get_state_by_dirname(str(request_dir))
    assert(state == 'P')
    tmpdir.remove(ignore_errors=True)


def test_get_outcome_by_dirname(flask_app):
    # TODO
    pass


def test_find_dir_by_request_id(flask_app):
    # TODO
    pass


def test_collect_image_info(flask_app):
    # TODO
    pass


def test_collect_log_info(flask_app):
    # TODO
    pass
