# ============================================
# 多階段建置 — 微服務通用 Docker Image
# ============================================
# 預設為 backend-gateway 以維持向下相容
ARG SERVICE_NAME=backend-gateway

# 階段 1：使用 Maven + JDK 21 編譯多模組專案
FROM maven:3.9.8-eclipse-temurin-21 AS build
ARG SERVICE_NAME
WORKDIR /app

# 複製所有 pom.xml（利用 Docker layer cache 加速相依性下載）
COPY pom.xml .
COPY backend-common/pom.xml backend-common/
COPY backend-gateway/pom.xml backend-gateway/
COPY backend-iam-service/pom.xml backend-iam-service/
COPY backend-competency-service/pom.xml backend-competency-service/
COPY backend-job-service/pom.xml backend-job-service/
COPY backend-external-api-service/pom.xml backend-external-api-service/
COPY backend-alert-service/pom.xml backend-alert-service/
COPY backend-test-support/pom.xml backend-test-support/

# 預先下載相依性（獨立 layer，source code 未變更時可快取）
RUN mvn dependency:go-offline -pl ${SERVICE_NAME} -am -B -q || true

# 複製所有原始碼
COPY backend-common/src backend-common/src/
COPY backend-gateway/src backend-gateway/src/
COPY backend-iam-service/src backend-iam-service/src/
COPY backend-competency-service/src backend-competency-service/src/
COPY backend-job-service/src backend-job-service/src/
COPY backend-external-api-service/src backend-external-api-service/src/
COPY backend-alert-service/src backend-alert-service/src/
COPY backend-test-support/src backend-test-support/src/

# 編譯並打包指定服務（含其依賴模組），跳過測試
RUN mvn clean package -pl ${SERVICE_NAME} -am -DskipTests -B -q

# ============================================
# 階段 2：執行階段 — OpenJDK 21 精簡映像
FROM eclipse-temurin:21-jre
ARG SERVICE_NAME
WORKDIR /app

# 從 build 階段複製指定服務 JAR
COPY --from=build /app/${SERVICE_NAME}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
