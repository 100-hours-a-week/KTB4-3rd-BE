# moyeota

## Stack
- Java 25
- Spring Boot 4.1.1
- Gradle 9.7 (wrapper 포함, 별도 설치 불필요)
- H2 (local) / MySQL (prod)

## Run
```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
./gradlew bootRun
```

- H2 콘솔: http://localhost:8080/h2-console
- prod 프로파일은 `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수가 필요함
