FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy the Java source files
COPY src ./src

# Compile the codebase using the sourcepath compiler flags
RUN mkdir -p out/production/Bookstore_App
RUN javac -encoding utf-8 -d out/production/Bookstore_App -sourcepath src src/com/mycompany/bookstore/testing/BookClientWebServer.java

# Expose the server port
EXPOSE 8080

# Command to execute the HTTP Web Server
CMD ["java", "-cp", "out/production/Bookstore_App", "com.mycompany.bookstore.testing.BookClientWebServer"]
