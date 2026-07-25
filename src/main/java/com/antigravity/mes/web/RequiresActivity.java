package com.antigravity.mes.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as belonging to a specific app activity (screen).
 * {@link RoleAuthorizationInterceptor} only allows the request through if the
 * caller's role has this activity code in its {@code roles.activities} list.
 * The value must match one of the codes in {@code frontend/lib/activities/}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresActivity {
    String value();
}
