package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record SimPlace(String id, String name, String category, String roadAddress, GeoPoint point, String area) {

    public static List<SimPlace> loadAll(Path path) throws IOException {
        List<SimPlace> places = new ArrayList<>();
        for (JsonNode node : new ObjectMapper().readTree(path.toFile())) {
            places.add(new SimPlace(
                    node.get("id").asText(),
                    node.get("name").asText(),
                    node.get("category").isNull() ? null : node.get("category").asText(),
                    node.get("roadAddress").isNull() ? null : node.get("roadAddress").asText(),
                    GeoPoint.of(node.get("lat").decimalValue(), node.get("lng").decimalValue()),
                    node.get("area").asText()));
        }
        return places;
    }

    public Place toPlace() {
        return new Place(name, point);
    }
}
