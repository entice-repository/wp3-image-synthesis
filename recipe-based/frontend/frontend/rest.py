import json
from flask_restful import abort, Api, Resource, request
from flask import send_from_directory, current_app
from imagebuilder import ImageBuilder


class Build(Resource):

    def post(self):
        outcome, message, request_id = ImageBuilder().new(request.data)
        result = {'status': 'ok' if outcome else 'failed',
                  'message': '' if outcome else message,
                  'result': '' if not outcome else
                  dict({'request_id': request_id})}
        return result, 200


class Request(Resource):

    def get(self, request_id):
        outcome, message, reqstate, reqoutcome = ImageBuilder().state(request_id)
        result = {'status': 'ok' if outcome else 'failed',
                  'message': '' if outcome else message,
                  'result': '' if not outcome else
                  dict({'request_id': request_id,
                        'request_status': reqstate,
                        'outcome:': reqoutcome})}
        return result, 200

    def delete(self, request_id):
        outcome, message = ImageBuilder().cancel(request_id)
        result = {'status': 'ok' if outcome else 'failed',
                  'message': '' if outcome else message}
        return result, 200


class Result(Resource):

    def get(self, request_id):
        outcome, message, ret = ImageBuilder().result(request_id)
        result = {'status': 'ok' if outcome else "failed",
                  'message': '' if outcome else message,
                  'result': '' if not outcome else ret}
        return result, 200
        '''
        result = {
                 'status': 'OK',
                 'message': '',
                 'result': {
                    'request_id': request_id,
                    'image': {
                        'url': "http://somewhere.com",
                        'id' : "ami-001234"}},
                    'log_url': "http://somewhere.com/log" }
        return result, 200
        '''

    def delete(self, request_id):
        outcome, message = ImageBuilder().delete(request_id)
        result = {'status': 'ok' if outcome else "failed",
                  'message': '' if outcome else message}
        return result, 200


class Image(Resource):

    def get(self, request_id):
        imgdir, imgfile = ImageBuilder().getImagePath(request_id)
        if not imgdir or not imgfile:
            result = {'status': 'failed',
                      'message': 'Requested image does not exist!'}
            return result, 200
        else:
            return send_from_directory(imgdir, imgfile, as_attachment=True)


class Output(Resource):

    def get(self, request_id, output_id):
        imgdir, imgfile, outputs = ImageBuilder().getOutputs(request_id)
        if not imgdir or not imgfile or output_id > len(outputs)-1:
            result = {'status': 'failed',
                      'message': 'Requested image does not exist!'}
            return result, 200
        else:
            current_app.logger.debug("Serving file '{}/{}'".format(imgdir, outputs[output_id]))
            return send_from_directory(imgdir, outputs[output_id],
                as_attachment=True)


class Log(Resource):

    def get(self, request_id):
        logdir, logfile = ImageBuilder().getLogPath(request_id)
        if not logdir or not logfile:
            result = {'status': 'failed',
                      'message': 'Requested log does not exist!'}
            return result, 200
        else:
            return send_from_directory(logdir, logfile, as_attachment=True)
