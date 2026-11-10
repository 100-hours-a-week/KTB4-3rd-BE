package com.ktb.moyeota.global.external.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.ktb.moyeota.domain.notification.push.PushSender;
import com.ktb.moyeota.global.config.NotificationPushProperties;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(NotificationPushProperties properties) throws IOException {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(app.getName())) {
                return app;
            }
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setConnectTimeout(toMillis(properties.fcm().connectTimeout().toMillis()))
                .setReadTimeout(toMillis(properties.fcm().readTimeout().toMillis()))
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    public PushSender fcmPushSender(FirebaseMessaging firebaseMessaging) {
        return new FcmPushSender(firebaseMessaging);
    }

    private static int toMillis(long millis) {
        return Math.toIntExact(millis);
    }
}
