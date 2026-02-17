# Этап 1: Сборка
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Собираем проект
RUN gradle build -x test --no-daemon

# Этап 2: Запуск (используем стабильный Eclipse Temurin)
FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080
RUN mkdir /app
# Копируем jar из этапа сборки
COPY --from=build /home/gradle/src/build/libs/*.jar /app/bot-app.jar
# Запускаем
ENTRYPOINT ["java", "-jar", "/app/bot-app.jar"]