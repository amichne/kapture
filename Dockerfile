#
FROM ubuntu:22.04

# Install build dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    libcurl4-openssl-dev \
    openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*


RUN wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.2.0/graalvm-ce-java11-linux-amd64-21.2.0.tar.gz && \
    tar -xzf graalvm-ce-java11-linux-amd64-21.2.0.tar.gz && \
    sudo mv graalvm-ce-java11-21.2.0 /opt/graalvm && \
    echo 'export PATH=/opt/graalvm/bin:$PATH' >> ~/.bashrc && \
    source ~/.bashrc && java -version && gu install native-image




# Set up working directory
WORKDIR /workspace

# Copy project files
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build the project
RUN ./gradlew build --no-daemon

# Make integration test script executable
RUN chmod +x scripts/integration-test.sh

# Default command
CMD ["./scripts/integration-test.sh"]
