package com.project.w_3d_world.features.auth.delivery;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EmailStateCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, EmailDeliveryState> read(String json) {
        return mapper.readValue(json == null ? "{}" : json,
                new TypeReference<LinkedHashMap<String, EmailDeliveryState>>() {});
    }

    public String write(Map<String, EmailDeliveryState> state) {
        return mapper.writeValueAsString(state);
    }
}
