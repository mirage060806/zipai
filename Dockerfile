FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl python3 python3-numpy python3-pandas python3-sklearn \
    && rm -rf /var/lib/apt/lists/*

ENV ZIPAI_LIFESTYLE_ML_PYTHON_COMMAND=python3 \
    OMP_NUM_THREADS=1 \
    OPENBLAS_NUM_THREADS=1 \
    MKL_NUM_THREADS=1

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY index.html /app/index.html
COPY templates /app/templates
COPY static /app/static
COPY rpa/lifestyle_ml /app/rpa/lifestyle_ml

RUN python3 -c "import numpy, pandas, sklearn" \
    && python3 rpa/lifestyle_ml/recommendation_service.py --help > /dev/null

EXPOSE 8080 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
