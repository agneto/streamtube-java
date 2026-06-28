package com.streamtube.application.video.result;

import java.util.UUID;

public record InitiateUploadResult(UUID id, String slug, String uploadUrl) {}
