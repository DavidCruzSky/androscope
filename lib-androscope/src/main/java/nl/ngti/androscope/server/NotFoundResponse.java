package nl.ngti.androscope.server;

import fi.iki.elonen.NanoHTTPD;

final class NotFoundResponse extends BaseAndroscopeResponse {

    @Override
    public NanoHTTPD.Response getResponse(SessionParams session) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "");
    }
}
