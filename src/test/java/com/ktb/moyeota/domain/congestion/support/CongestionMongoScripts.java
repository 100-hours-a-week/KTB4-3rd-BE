package com.ktb.moyeota.domain.congestion.support;

import java.nio.file.Path;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.MountableFile;

public final class CongestionMongoScripts {

    private static final String INDEX_SCRIPT = "scripts/mongo/congestion-indexes.js";
    private static final String CONTAINER_PATH = "/tmp/congestion-indexes.js";

    private CongestionMongoScripts() {
    }

    public static void applyIndexes(MongoDBContainer container) throws Exception {
        container.copyFileToContainer(MountableFile.forHostPath(Path.of(INDEX_SCRIPT)), CONTAINER_PATH);
        ExecResult result = container.execInContainer("mongosh", "--quiet", "mongodb://localhost:27017/test",
                CONTAINER_PATH);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("인덱스 스크립트 실행 실패: " + result.getStderr() + result.getStdout());
        }
    }
}
