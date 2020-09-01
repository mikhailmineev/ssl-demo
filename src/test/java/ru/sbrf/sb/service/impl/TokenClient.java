package ru.sbrf.sb.service.impl;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface TokenClient {

    @GET
    Response getToken();
}
