# ==============================================================================
# Estágio 1: Build (Compilação)
# Usando a imagem Maven com Java 21 que você já utilizava
# ==============================================================================
FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /app

# ARGUMENTO IMPORTANTE:
# Este argumento virá do docker-compose para dizer qual módulo compilar
ARG MODULE_NAME

# Copiamos todo o código fonte (Raiz + Common + Módulos)
# Fazemos isso de uma vez para garantir que o Maven enxergue o projeto pai e o common
COPY . .

# -pl ${MODULE_NAME} : Compila apenas o módulo que passamos no argumento
# -am                : (Also Make) Compila também as dependências (ex: common)
# -DskipTests        : Pula os testes para agilizar o build do container
RUN mvn clean package -pl ${MODULE_NAME} -am -DskipTests

# ==============================================================================
# Estágio 2: Runtime (Execução)
# Usando a imagem JRE leve (apenas o necessário para rodar)
# ==============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Precisamos redeclarar o ARG aqui para usá-lo no caminho do COPY
ARG MODULE_NAME

# Copia o JAR gerado no estágio anterior
# O caminho é dinâmico: /app/<nome-do-modulo>/target/*.jar
COPY --from=build /app/${MODULE_NAME}/target/*-jar-with-dependencies.jar app.jar

# Comando para iniciar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]