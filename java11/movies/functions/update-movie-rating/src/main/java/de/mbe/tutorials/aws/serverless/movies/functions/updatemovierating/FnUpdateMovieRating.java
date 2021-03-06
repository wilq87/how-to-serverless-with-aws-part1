package de.mbe.tutorials.aws.serverless.movies.functions.updatemovierating;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.handlers.TracingHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mbe.tutorials.aws.serverless.movies.functions.updatemovierating.repository.MoviesDynamoDbRepository;
import de.mbe.tutorials.aws.serverless.movies.functions.updatemovierating.utils.APIGatewayV2ProxyResponseUtils;
import de.mbe.tutorials.aws.serverless.movies.models.MovieRating;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public final class FnUpdateMovieRating implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent>, APIGatewayV2ProxyResponseUtils {

    private static final Logger LOGGER = LogManager.getLogger(FnUpdateMovieRating.class);
    private final ObjectMapper MAPPER = new ObjectMapper();

    private final MoviesDynamoDbRepository repository;

    public FnUpdateMovieRating() {

        final var amazonDynamoDB = AmazonDynamoDBClientBuilder
                .standard()
                .withRequestHandlers(new TracingHandler(AWSXRay.getGlobalRecorder()))
                .build();

        final var movieRatingsTable = System.getenv("MOVIE_RATINGS_TABLE");

        this.repository = new MoviesDynamoDbRepository(amazonDynamoDB, movieRatingsTable);
    }

    @Override
    public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent request, Context context) {

        LOGGER.info("FnAddMovieRating.getRemainingTimeInMillis {} ", context.getRemainingTimeInMillis());

        if (!request.getHttpMethod().equalsIgnoreCase("patch")) {
            return methodNotAllowed(LOGGER, "Method " + request.getHttpMethod() + " not allowed");
        }

        if (!request.getPathParameters().containsKey("movieId") || isNullOrEmpty(request.getPathParameters().get("movieId"))) {
            return badRequest(LOGGER, "Missing {movieId} path parameter");
        }

        final var movieId = request.getPathParameters().get("movieId");
        LOGGER.info("Patching movie {}", movieId);

        try {

            final var movieRating = MAPPER.readValue(request.getBody(), MovieRating.class);
            this.repository.updateMovieRating(movieRating);
            return ok(LOGGER, "SUCCESS");

        } catch (AmazonDynamoDBException error) {
            return amazonDynamoDBException(LOGGER, error);
        } catch (Exception error) {
            return internalServerError(LOGGER, error);
        }
    }
}
