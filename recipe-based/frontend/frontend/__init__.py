from flask import Flask, redirect
from flask_restful import Resource, Api

from agro_log_handler import AgroLogHandler
from rest import Build, Request, Result, Image, Log, Output

app = Flask(__name__)


def init_application(app, config):
    """
    Reads config and registers blueprints.

    :param Flask app: application instance
    :param Object config: configuration object
    """
    app.config.from_object(config)

    api = Api(app)
    api.add_resource(Build, config.WSPATH)
    api.add_resource(Request, config.WSPATH + '/<request_id>')
    api.add_resource(Result, config.WSPATH + '/<request_id>/result')
    api.add_resource(Image, config.WSPATH + '/<request_id>/result/image')
    api.add_resource(Output, config.WSPATH + '/<request_id>/result/output/<int:output_id>')
    api.add_resource(Log, config.WSPATH + '/<request_id>/result/log')

    AgroLogHandler(app).init()
    app.logger.info("Flask Application initialized")
