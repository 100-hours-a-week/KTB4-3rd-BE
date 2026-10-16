package com.ktb.moyeota.global.config;

import java.util.Arrays;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnServerRoleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnServerRole.class.getName());
        if (attributes == null) {
            return true;
        }
        ServerRole[] roles = (ServerRole[]) attributes.get("value");
        ServerRole current = ServerRole.from(context.getEnvironment().getProperty(ServerRole.ENV_NAME));
        return Arrays.asList(roles).contains(current);
    }
}
