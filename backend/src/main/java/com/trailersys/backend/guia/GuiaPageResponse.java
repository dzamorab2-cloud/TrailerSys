package com.trailersys.backend.guia;

import java.util.List;

public record GuiaPageResponse(
        List<GuiaListadoResponse> content, long totalElements, int totalPages,
        int number, boolean first, boolean last) {
}
