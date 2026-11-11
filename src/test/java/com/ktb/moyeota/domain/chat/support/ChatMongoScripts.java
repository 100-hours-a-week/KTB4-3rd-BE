package com.ktb.moyeota.domain.chat.support;

import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.nio.file.Path;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.MountableFile;

public final class ChatMongoScripts {

    private static final String INDEX_SCRIPT = "scripts/mongo/chat-indexes.js";
    private static final String CONTAINER_PATH = "/tmp/chat-indexes.js";

    private ChatMongoScripts() {
    }

    public static void applyIndexes() throws Exception {
        MongoDBContainer container = MongoTestContainerConfig.MONGO;
        container.copyFileToContainer(MountableFile.forHostPath(Path.of(INDEX_SCRIPT)), CONTAINER_PATH);
        ExecResult result = container.execInContainer("mongosh", "--quiet", "mongodb://localhost:27017/test",
                CONTAINER_PATH);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("인덱스 스크립트 실행 실패: " + result.getStderr() + result.getStdout());
        }
    }
}
