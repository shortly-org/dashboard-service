package io.shortly.dashboard.controller;

import io.shortly.dashboard.exception.InvalidTargetUrlException;
import io.shortly.dashboard.exception.LinkNotFoundException;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns exceptions into RFC 9457 problem responses. */
@RestControllerAdvice
class ExceptionAdvice extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionAdvice.class);
    private static final String PROBLEM_TYPE_BASE = "https://shortly.example/problems/";

    @ExceptionHandler(InvalidTargetUrlException.class)
    @ApiResponse(
            responseCode = "400",
            description =
                    "A malformed request: unreadable body, invalid parameter, or a date range that"
                            + " ends before it starts.")
    ProblemDetail invalidTargetUrl(InvalidTargetUrlException e) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-target-url",
                "Target URL not allowed",
                e.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    @ApiResponse(responseCode = "404", description = "link-not-found: no such link, or not yours.")
    ProblemDetail linkNotFound(LinkNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "link-not-found", "Link not found", e.getMessage());
    }

    /** The database is unreachable or briefly failing: a 503 the client may retry. */
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        TransientDataAccessException.class
    })
    @ApiResponse(
            responseCode = "503",
            description = "service-unavailable: retry after the Retry-After seconds.")
    ProblemDetail databaseUnavailable(DataAccessException e, HttpServletResponse response) {
        LOG.warn("database unavailable: {}", e.getClass().getSimpleName());
        response.setHeader(HttpHeaders.RETRY_AFTER, "5");
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "service-unavailable",
                "Service unavailable",
                "Try again in a few seconds.");
    }

    @ExceptionHandler(Exception.class)
    @ApiResponse(responseCode = "500", description = "internal-error.")
    ProblemDetail unexpected(Exception e) {
        LOG.error("unexpected failure", e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal error",
                "The request could not be completed.");
    }

    private static ProblemDetail problem(
            HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}
