package ch.sbb.polarion.extension.xml_repair.rest.exception;

import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoSuchElementExceptionMapperTest {

    @Test
    void testUnknownJobIsNotFound() {
        try (Response response = new NoSuchElementExceptionMapper().toResponse(new NoSuchElementException("Scan job is unknown: job-1"))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            assertEquals("Scan job is unknown: job-1", ((ErrorEntity) response.getEntity()).getMessage());
        }
    }
}
