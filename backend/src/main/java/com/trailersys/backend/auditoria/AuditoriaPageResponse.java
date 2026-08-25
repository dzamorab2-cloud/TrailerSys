package com.trailersys.backend.auditoria;

import java.util.List;

public record AuditoriaPageResponse(List<AuditoriaResponse> content, long totalElements,
        int page, int size, int totalPages) {
}
