import json
import logging
import os
import boto3

from aws_xray_sdk.core import patch_all

patch_all()

logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamoDB = boto3.resource("dynamodb")


def handle_request(event, context):
    logger.info(f"FnGetMovie.RemainingTimeInMillis {context.get_remaining_time_in_millis()}")

    request_http_method = event["httpMethod"]
    if request_http_method.lower() != "get":
        logger.info(f"Unsupported http method {request_http_method}")
        return {
            "headers": {
                "Content-Type": "application/json"
            },
            "statusCode": 405,
            "body": f"Method {request_http_method} not allowed"
        }

    if "movieId" not in event["pathParameters"]:
        logger.info("Missing parameter movieId")
        return {
            "headers": {
                "Content-Type": "application/json"
            },
            "statusCode": 400,
            "body": "Missing {id} path parameter"
        }

    movie_id = event["pathParameters"]["movieId"]
    logger.info(f"Retrieving movie {movie_id}")

    try:
        movie = get_movie_by_id(movie_id, os.environ["MOVIE_INFOS_TABLE"], os.environ["MOVIE_RATINGS_TABLE"])

        if movie is None:
            return {
                "headers": {
                    "Content-Type": "application/json"
                },
                "statusCode": 404,
                "body": f"Movie {request_http_method} not found"
            }
        else:
            return {
                "headers": {
                    "Content-Type": "application/json"
                },
                "statusCode": 200,
                "body": json.dumps(movie)
            }
    except Exception as e:
        logger.error(f"Error while retrieving movie {movie_id} from DynamoDB, error: {str(e)}")
        return {
            "headers": {
                "Content-Type": "application/json"
            },
            "statusCode": 500,
            "body": f"{str(e)}"
        }


def get_movie_by_id(movie_id, movie_infos_table, movie_ratings_table):
    movie_info = get_record_by_id(movie_id, movie_infos_table)
    movie_rating = get_record_by_id(movie_id, movie_ratings_table)

    if movie_info is None and movie_rating is None:
        return None

    if movie_info is None:
        return movie_rating

    if movie_rating is None:
        return movie_info

    movie_info.update(movie_rating)
    return movie_info


def get_record_by_id(movie_id, table):
    table = dynamoDB.Table(table)
    response = table.get_item(
        Key={
            "movieId": movie_id
        },
    )

    if "Item" in response:
        return response["Item"]

    return None
