# Somnia Native Runtime - Docker Build

FROM gcc:latest AS builder

WORKDIR /somnia

# Copy source files
COPY include/ include/
COPY src/ src/
COPY Makefile .

# Build
RUN make

# Runtime image
FROM debian:slim

WORKDIR /app

# Copy binary
COPY --from=builder /somnia/somnia /usr/local/bin/somnia

# Copy examples
COPY examples/ /app/examples/

# Default command
ENTRYPOINT ["somnia"]
CMD ["repl"]
