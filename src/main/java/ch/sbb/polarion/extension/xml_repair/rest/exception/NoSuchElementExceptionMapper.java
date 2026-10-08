package ch.sbb.polarion.extension.xml_repair.rest.exception;

import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import com.polarion.core.util.logging.Logger;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.NoSuchElementException;

/**
 * Answers 404 for a job its caller does not know: never started, started by somebody else, or dropped once finished.
 */
public class NoSuchElementExceptionMapper implements ExceptionMapper<NoSuchElementException> {

    private final Logger logger = Logger.getLogger(NoSuchElementExceptionMapper.class);

    @Override
    public Response toResponse(NoSuchElementException e) {
        logger.warn("Unknown element: " + e.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorEntity(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
