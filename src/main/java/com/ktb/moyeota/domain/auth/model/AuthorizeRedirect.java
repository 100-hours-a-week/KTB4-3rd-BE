package com.ktb.moyeota.domain.auth.model;

import java.net.URI;

public record AuthorizeRedirect(URI location, String state) {
}
